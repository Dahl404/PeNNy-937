package com.dahl404.penny937.paint

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.dahl404.penny937.data.ReferenceFiles
import com.dahl404.penny937.model.Frame
import com.dahl404.penny937.model.Layer
import com.dahl404.penny937.model.LayerImage
import com.dahl404.penny937.model.Project
import com.dahl404.penny937.model.Tool
import kotlin.math.roundToInt

/** Renders frames at arbitrary sizes straight from the vector strokes. */
object FrameRasterizer {

    /** Reusable per-layer scratch bitmap; rendering happens on several threads. */
    private val scratch = ThreadLocal<Bitmap>()

    /** Above this a scratch bitmap is not kept around: they can reach 64 MiB each. */
    private const val SCRATCH_KEEP_BYTES = 16L * 1024 * 1024

    /** Test hook: disable the single-layer fast path so both paths can be compared. */
    internal var forceComposited: Boolean = false

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
        val sx = w.toFloat() / project.width
        val sy = h.toFloat() / project.height

        // Fast path: one opaque, non-erasing layer needs no compositing pass at all.
        // This is the common case (a sketch on a single layer with the paper colour
        // underneath) and it skips a full-size scratch bitmap and a full-size copy.
        val single = if (forceComposited) null else singleDrawableLayer(frame, includeReferences)
        if (single != null) {
            target.save()
            target.scale(sx, sy)
            for (stroke in single.strokes) Ink.renderStroke(target, stroke)
            target.restore()
            return bitmap
        }

        val keepScratch = w.toLong() * h.toLong() * 4L <= SCRATCH_KEEP_BYTES
        val layerBitmap = scratchFor(w, h, keepScratch)
        val layerCanvas = Canvas(layerBitmap)
        val compositePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (layer in frame.layers) {
            if (!layer.visible || layer.opacity <= 0.002f) continue
            if (!drawsInOutput(layer, includeReferences)) continue
            layerBitmap.eraseColor(Color.TRANSPARENT)
            layerCanvas.save()
            layerCanvas.scale(sx, sy)
            for (stroke in layer.strokes) Ink.renderStroke(layerCanvas, stroke)
            if (includeReferences || layer.image?.inOutput == true) {
                layer.image?.let { drawReference(layerCanvas, it, project.id) }
            }
            layerCanvas.restore()
            compositePaint.alpha = (layer.opacity * 255f).roundToInt().coerceIn(0, 255)
            target.drawBitmap(layerBitmap, 0f, 0f, compositePaint)
        }
        if (!keepScratch) layerBitmap.recycle()
        return bitmap
    }

    /**
     * Returns the only layer of [frame] if it can be drawn straight onto the output:
     * fully opaque, not a reference, and free of eraser strokes (which would punch
     * through the paper colour instead of just the layer's own ink).
     */
    private fun singleDrawableLayer(frame: Frame, includeReferences: Boolean): Layer? {
        var found: Layer? = null
        for (layer in frame.layers) {
            if (!layer.visible || layer.opacity <= 0.002f) continue
            if (!drawsInOutput(layer, includeReferences)) continue
            if (found != null) return null
            found = layer
        }
        val layer = found ?: return null
        if (layer.opacity < 0.999f) return null
        if (layer.isReference) return null
        // The fast path only walks strokes; raster ink (merged layers) needs the
        // compositing path so its baked image is drawn too.
        if (layer.image != null) return null
        for (stroke in layer.strokes) if (stroke.tool == Tool.ERASER) return null
        return layer
    }

    /**
     * A reference is normally an editor-only guide. Set [LayerImage.inOutput] to
     * include it in exports and thumbnails that render the finished animation.
     */
    private fun drawsInOutput(layer: Layer, includeReferences: Boolean): Boolean =
        !layer.isReference || includeReferences || layer.image?.inOutput == true

    fun renderLayer(canvas: Canvas, layer: Layer, projectId: Long) {
        layer.image?.let { drawReference(canvas, it, projectId) }
        for (stroke in layer.strokes) Ink.renderStroke(canvas, stroke)
    }

    private fun scratchFor(w: Int, h: Int, keep: Boolean): Bitmap {
        val existing = scratch.get()
        if (existing != null && existing.width == w && existing.height == h && !existing.isRecycled) {
            return existing
        }
        if (existing != null && !existing.isRecycled) {
            existing.recycle()
            if (!keep) scratch.remove() else scratch.set(null)
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        if (keep) scratch.set(bitmap)
        return bitmap
    }

    private fun drawReference(canvas: Canvas, image: LayerImage, projectId: Long) {
        val bitmap = ReferenceFiles.bitmap(projectId, image.file) ?: return
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
 * Display-scale bitmap cache + small composite cache used by the canvas view.
 * Main-thread only.
 *
 * Interactive layer bitmaps are rendered at roughly the pixels the paper occupies
 * on screen instead of always at project resolution: a 4096px canvas on a phone
 * no longer needs a 64 MiB bitmap per layer just to draw an 8px pencil line.
 * Export uses [FrameRasterizer] at its own target size and is unaffected.
 */
class FrameRenderer {

    private class LayerEntry(
        var bitmap: Bitmap,
        var contentRevision: Int
    )

    /**
     * Pixels per project pixel for interactive layer caches. Quantised by the
     * canvas so pinch gestures do not trigger a re-render at every frame.
     */
    var interactiveScale: Float = 1f
        private set

    private val layers = LinkedHashMap<Long, LayerEntry>(16, 0.75f, true)
    private var layerBytes = 0L
    internal var layerBudget = (Runtime.getRuntime().maxMemory() / 3).coerceAtLeast(48L * 1024 * 1024)

    /**
     * Layers the canvas is currently drawing on. Their bitmaps must survive cache
     * eviction: the live stroke is composited into them when the pen lifts, and a
     * recycled bitmap would crash the commit. Main-thread only, like the rest of
     * this class.
     */
    private val pinnedLayers = HashSet<Long>()

    private data class CompositeKey(val frameId: Long, val transparent: Boolean, val width: Int)

    private class CompositeEntry(val bitmap: Bitmap, val revision: Int)

    private val composites = LinkedHashMap<CompositeKey, CompositeEntry>(8, 0.75f, true)
    private var compositeBytes = 0L
    private val compositeBudget = 64L * 1024 * 1024

    private var overlay: Bitmap? = null
    private var overlayWidth = 0
    private var overlayHeight = 0

    private class PlaybackEntry(
        val frameId: Long,
        val revision: Int,
        val width: Int,
        val bitmap: Bitmap
    )

    /**
     * Ready-to-draw playback frames produced on a worker thread. Main-thread only:
     * the prefetcher posts finished bitmaps here, and [onDraw] is the only reader,
     * so eviction and recycling never race with drawing.
     */
    private val playback = LinkedHashMap<Int, PlaybackEntry>(16, 0.75f, true)
    private var playbackBytes = 0L
    private val playbackBudget = 48L * 1024 * 1024

    fun pinLayer(layerId: Long) {
        pinnedLayers.add(layerId)
    }

    fun unpinLayer(layerId: Long) {
        pinnedLayers.remove(layerId)
    }

    fun setInteractiveScale(scale: Float) {
        val next = scale.coerceIn(MIN_INTERACTIVE_SCALE, 1f)
        if (next == interactiveScale) return
        // Re-rendering at a new scale would recycle the bitmap of the stroke in
        // progress. Keep the current scale until the canvas finishes the stroke;
        // the next onDraw retries this call.
        if (pinnedLayers.isNotEmpty()) return
        interactiveScale = next
        // Re-rendering is lazy through ensureEntry; release the old-scale bitmaps
        // now instead of letting them sit in the LRU until each layer is redrawn.
        layers.values.forEach { it.bitmap.recycle() }
        layers.clear()
        layerBytes = 0
        overlay?.recycle()
        overlay = null
        overlayWidth = 0
        overlayHeight = 0
    }

    fun editableLayerBitmap(project: Project, layer: Layer): Bitmap {
        val entry = ensureEntry(project, layer)
        val revision = layer.contentRevision
        if (entry.contentRevision != revision) {
            renderLayerInto(entry.bitmap, layer, project)
            entry.contentRevision = revision
        }
        return entry.bitmap
    }

    fun cachedLayerBitmap(layer: Layer): Bitmap? = layers[layer.id]?.bitmap

    /**
     * The canvas already composited the stroke it just committed into the cached
     * bitmap, so record the layer's new identity instead of re-rendering it.
     */
    fun noteStrokeCommitted(layer: Layer) {
        layers[layer.id]?.let { it.contentRevision = layer.contentRevision }
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
        clearPlaybackCache()
    }

    /** Drops prefetched playback frames; call when playback starts or stops. */
    fun clearPlaybackCache() {
        playback.values.forEach { it.bitmap.recycle() }
        playback.clear()
        playbackBytes = 0
    }

    /**
     * Returns a prefetched frame only when it matches the current document and
     * target width. A stale entry is discarded instead of shown for one frame.
     */
    fun playbackBitmap(frameIndex: Int, frameId: Long, revision: Int, width: Int): Bitmap? {
        val entry = playback[frameIndex] ?: return null
        if (entry.frameId != frameId || entry.revision != revision || entry.width != width) {
            playback.remove(frameIndex)
            playbackBytes -= bytesOf(entry.bitmap)
            entry.bitmap.recycle()
            return null
        }
        return entry.bitmap
    }

    fun hasPlaybackBitmap(frameIndex: Int, frameId: Long, revision: Int, width: Int): Boolean {
        val entry = playback[frameIndex] ?: return false
        return entry.frameId == frameId && entry.revision == revision && entry.width == width
    }

    /** Adds a bitmap rendered off the main thread and evicts the oldest frames. */
    fun putPlaybackBitmap(
        frameIndex: Int,
        frameId: Long,
        revision: Int,
        width: Int,
        bitmap: Bitmap
    ) {
        playback.remove(frameIndex)?.let {
            playbackBytes -= bytesOf(it.bitmap)
            it.bitmap.recycle()
        }
        val entry = PlaybackEntry(frameId, revision, width, bitmap)
        playback[frameIndex] = entry
        playbackBytes += bytesOf(bitmap)
        val iterator = playback.entries.iterator()
        while (playbackBytes > playbackBudget && playback.size > 2 && iterator.hasNext()) {
            val eldest = iterator.next()
            if (eldest.key == frameIndex) continue
            playbackBytes -= bytesOf(eldest.value.bitmap)
            eldest.value.bitmap.recycle()
            iterator.remove()
        }
    }

    /**
     * Scratch bitmap for the stroke currently under the pen.
     *
     * Every tool draws its live ink here as an opaque ribbon, and the result is
     * blended into the layer exactly once (on lift, or per frame while previewing).
     * That is what keeps translucent marker ink from darkening at every joint, and
     * it lets the eraser preview without touching the layer bitmap.
     */
    fun inkOverlay(project: Project): Bitmap {
        val width = cacheWidth(project)
        val height = cacheHeight(project)
        val existing = overlay
        if (existing != null && overlayWidth == width && overlayHeight == height) {
            return existing
        }
        existing?.recycle()
        overlay = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        overlayWidth = width
        overlayHeight = height
        return overlay!!
    }

    fun clearInkOverlay() {
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
        val width = cacheWidth(project)
        val height = cacheHeight(project)
        val existing = layers[layer.id]
        if (existing != null) {
            if (existing.bitmap.width == width && existing.bitmap.height == height) {
                return existing
            }
            invalidateLayer(layer.id)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val entry = LayerEntry(bitmap, Int.MIN_VALUE)
        layers[layer.id] = entry
        layerBytes += bytesOf(bitmap)
        val iterator = layers.entries.iterator()
        while (layerBytes > layerBudget && layers.size > 2 && iterator.hasNext()) {
            val eldest = iterator.next()
            if (eldest.key == layer.id || eldest.key in pinnedLayers) continue
            layerBytes -= bytesOf(eldest.value.bitmap)
            eldest.value.bitmap.recycle()
            iterator.remove()
        }
        return entry
    }

    private fun renderLayerInto(bitmap: Bitmap, layer: Layer, project: Project) {
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        if (bitmap.width != project.width || bitmap.height != project.height) {
            canvas.scale(
                bitmap.width.toFloat() / project.width,
                bitmap.height.toFloat() / project.height
            )
        }
        FrameRasterizer.renderLayer(canvas, layer, project.id)
    }

    private fun cacheWidth(project: Project): Int =
        (project.width * interactiveScale).roundToInt().coerceAtLeast(1)

    private fun cacheHeight(project: Project): Int =
        (project.height * interactiveScale).roundToInt().coerceAtLeast(1)

    private fun bytesOf(bitmap: Bitmap): Long =
        bitmap.width.toLong() * bitmap.height.toLong() * 4L

    private companion object {
        /** Below this the cache stops buying memory back and starts losing detail. */
        const val MIN_INTERACTIVE_SCALE = 1f / 16f
    }
}
