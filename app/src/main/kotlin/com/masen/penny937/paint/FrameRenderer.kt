package com.masen.penny937.paint

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.masen.penny937.data.ReferenceFiles
import com.masen.penny937.model.Frame
import com.masen.penny937.model.Layer
import com.masen.penny937.model.LayerImage
import com.masen.penny937.model.Project
import kotlin.math.roundToInt

/** Renders frames at arbitrary sizes straight from the vector strokes. */
object FrameRasterizer {
    fun rasterize(
        project: Project,
        frame: Frame,
        targetWidth: Int,
        targetHeight: Int,
        background: Int?,
        includeReferences: Boolean = false
    ): Bitmap {
        val w = targetWidth.coerceAtLeast(1)
        val h = targetHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (background != null) bitmap.eraseColor(background)
        val target = Canvas(bitmap)
        val scratch = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val scratchCanvas = Canvas(scratch)
        val sx = w.toFloat() / project.width
        val sy = h.toFloat() / project.height
        val compositePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (layer in frame.layers) {
            if (!layer.visible || layer.opacity <= 0.002f) continue
            if (layer.isReference && !includeReferences) continue
            scratch.eraseColor(Color.TRANSPARENT)
            scratchCanvas.save()
            scratchCanvas.scale(sx, sy)
            for (stroke in layer.strokes) Ink.renderStroke(scratchCanvas, stroke)
            scratchCanvas.restore()
            compositePaint.alpha = (layer.opacity * 255f).roundToInt().coerceIn(0, 255)
            target.drawBitmap(scratch, 0f, 0f, compositePaint)
        }
        scratch.recycle()
        return bitmap
    }

    fun renderLayer(canvas: Canvas, layer: Layer) {
        layer.image?.let { drawReference(canvas, it) }
        for (stroke in layer.strokes) Ink.renderStroke(canvas, stroke)
    }

    private fun drawReference(canvas: Canvas, image: LayerImage) {
        val bitmap = ReferenceFiles.bitmap(image.file) ?: return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.save()
        canvas.translate(image.centerX, image.centerY)
        canvas.rotate(image.rotation)
        val halfW = image.width / 2f
        val halfH = image.height / 2f
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(-halfW, -halfH, halfW, halfH),
            paint
        )
        canvas.restore()
    }
}

/**
 * Full-resolution bitmap cache + small composite cache used by the canvas view.
 * Main-thread only.
 */
class FrameRenderer {

    private class LayerEntry(
        var bitmap: Bitmap,
        var drawnStrokes: Int,
        var drawnImageHash: Int
    )

    private val layers = LinkedHashMap<Long, LayerEntry>(16, 0.75f, true)
    private var layerBytes = 0L
    private val layerBudget = (Runtime.getRuntime().maxMemory() / 3).coerceAtLeast(48L * 1024 * 1024)

    private data class CompositeKey(val frameId: Long, val transparent: Boolean, val width: Int)

    private class CompositeEntry(val bitmap: Bitmap, val revision: Int)

    private val composites = LinkedHashMap<CompositeKey, CompositeEntry>(8, 0.75f, true)
    private var compositeBytes = 0L
    private val compositeBudget = 64L * 1024 * 1024

    private var overlay: Bitmap? = null
    private var overlayWidth = 0
    private var overlayHeight = 0

    fun editableLayerBitmap(project: Project, layer: Layer): Bitmap {
        val entry = ensureEntry(project, layer)
        val imageHash = layer.image?.hashCode() ?: 0
        if (entry.drawnStrokes != layer.strokes.size || entry.drawnImageHash != imageHash) {
            renderLayerInto(entry.bitmap, layer)
            entry.drawnStrokes = layer.strokes.size
            entry.drawnImageHash = imageHash
        }
        return entry.bitmap
    }

    fun cachedLayerBitmap(layer: Layer): Bitmap? = layers[layer.id]?.bitmap

    fun noteStrokeCommitted(layerId: Long) {
        layers[layerId]?.let { it.drawnStrokes += 1 }
    }

    fun invalidateComposites() {
        composites.values.forEach { it.bitmap.recycle() }
        composites.clear()
        compositeBytes = 0
    }

    fun invalidateLayer(layerId: Long) {
        val entry = layers.remove(layerId) ?: return
        layerBytes -= bytesOf(entry.bitmap)
        entry.bitmap.recycle()
    }

    fun invalidateAll() {
        layers.values.forEach { it.bitmap.recycle() }
        layers.clear()
        layerBytes = 0
        composites.values.forEach { it.bitmap.recycle() }
        composites.clear()
        compositeBytes = 0
        overlay?.recycle()
        overlay = null
        overlayWidth = 0
        overlayHeight = 0
    }

    /** Live marker ink is buffered here so translucent joints never double up. */
    fun markerOverlay(project: Project): Bitmap {
        val existing = overlay
        if (existing != null && overlayWidth == project.width && overlayHeight == project.height) {
            return existing
        }
        existing?.recycle()
        overlay = Bitmap.createBitmap(project.width, project.height, Bitmap.Config.ARGB_8888)
        overlayWidth = project.width
        overlayHeight = project.height
        return overlay!!
    }

    fun clearMarkerOverlay() {
        overlay?.eraseColor(Color.TRANSPARENT)
    }

    fun composite(project: Project, frame: Frame, targetWidth: Int, transparent: Boolean): Bitmap {
        val width = targetWidth.coerceIn(16, project.width)
        val key = CompositeKey(frame.id, transparent, width)
        val height = (width.toFloat() * project.height / project.width).roundToInt().coerceAtLeast(1)
        val existing = composites[key]
        if (existing != null && existing.revision == frame.revision &&
            existing.bitmap.width == width && existing.bitmap.height == height
        ) {
            return existing.bitmap
        }
        val bitmap = FrameRasterizer.rasterize(
            project, frame, width, height,
            if (transparent) null else project.background
        )
        existing?.let {
            compositeBytes -= bytesOf(it.bitmap)
            it.bitmap.recycle()
        }
        val entry = CompositeEntry(bitmap, frame.revision)
        composites[key] = entry
        compositeBytes += bytesOf(bitmap)
        val iterator = composites.entries.iterator()
        while (compositeBytes > compositeBudget && composites.size > 1 && iterator.hasNext()) {
            val eldest = iterator.next()
            if (eldest.key == key) continue
            compositeBytes -= bytesOf(eldest.value.bitmap)
            eldest.value.bitmap.recycle()
            iterator.remove()
        }
        return bitmap
    }

    private fun ensureEntry(project: Project, layer: Layer): LayerEntry {
        val existing = layers[layer.id]
        if (existing != null) {
            if (existing.bitmap.width == project.width && existing.bitmap.height == project.height) {
                return existing
            }
            invalidateLayer(layer.id)
        }
        val bitmap = Bitmap.createBitmap(project.width, project.height, Bitmap.Config.ARGB_8888)
        val entry = LayerEntry(bitmap, -1, Int.MIN_VALUE)
        layers[layer.id] = entry
        layerBytes += bytesOf(bitmap)
        val iterator = layers.entries.iterator()
        while (layerBytes > layerBudget && layers.size > 2 && iterator.hasNext()) {
            val eldest = iterator.next()
            if (eldest.key == layer.id) continue
            layerBytes -= bytesOf(eldest.value.bitmap)
            eldest.value.bitmap.recycle()
            iterator.remove()
        }
        return entry
    }

    private fun renderLayerInto(bitmap: Bitmap, layer: Layer) {
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        FrameRasterizer.renderLayer(canvas, layer)
    }

    private fun bytesOf(bitmap: Bitmap): Long =
        bitmap.width.toLong() * bitmap.height.toLong() * 4L
}
