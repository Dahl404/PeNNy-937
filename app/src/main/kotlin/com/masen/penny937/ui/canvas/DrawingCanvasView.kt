package com.masen.penny937.ui.canvas

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.masen.penny937.editor.EditorModel
import com.masen.penny937.model.Project
import com.masen.penny937.model.Tool
import com.masen.penny937.paint.Ink
import com.masen.penny937.paint.StrokeInker
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Stylus-first drawing surface.
 *
 * - Pressure-sensitive, smoothed ink rendered incrementally into a layer bitmap.
 * - Markers are buffered so translucent strokes never double up at their joints.
 * - Two fingers (or palm-sized contacts) navigate; pen-sized contacts draw.
 * - Pinch to zoom, double-tap to toggle fit.
 */
class DrawingCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var editor: EditorModel? = null
        private set
    var previewFrameIndex: Int = -1
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var darkTheme: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val onionPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(0.8f)
    }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val cursorFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val markerCompositePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clipPath = Path()
    private val paperRect = RectF()
    private val bitmapRect = RectF()

    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var fitScale = 1f
    private var userZoomed = false
    private var seenWidth = -1
    private var seenHeight = -1

    private var activePointerId = -1
    private var mode = Mode.NONE
    private var inker: StrokeInker? = null
    private var activeLayerIndex = -1
    private var activeLayerId = -1L
    private var strokeFrameIndex = -1
    private var activeBitmap: Bitmap? = null
    private var markerBitmap: Bitmap? = null
    private var markerOpacity = 1f
    private var strokeIsMarker = false

    private var panLastX = 0f
    private var panLastY = 0f
    private var pinchStartSpan = 1f
    private var pinchStartScale = 1f
    private var pinchWorldX = 0f
    private var pinchWorldY = 0f
    private var pinchFocusX = 0f
    private var pinchFocusY = 0f
    private var pinchPointerA = -1
    private var pinchPointerB = -1

    private var hoverVisible = false
    private var hoverX = 0f
    private var hoverY = 0f
    private var hoverErase = false

    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var suppressUntilUp = false
    private var animator: ValueAnimator? = null

    private enum class Mode { NONE, DRAW, PAN, PINCH }

    fun bind(model: EditorModel) {
        val previous = editor
        editor = model
        model.onBeforeStructuralChange = { cancelActiveStroke() }
        val dimsChanged = previous?.project?.width != model.project.width ||
            previous?.project?.height != model.project.height
        val projectChanged = previous?.project?.id != model.project.id
        if (projectChanged) {
            cancelStroke()
            userZoomed = false
            fitToPaper()
        } else if (dimsChanged) {
            cancelStroke()
            userZoomed = false
            fitToPaper()
        }
        if (previous != model) {
            mode = Mode.NONE
        }
        val targetStillValid = inker == null ||
            (model.currentFrame == strokeFrameIndex &&
                model.currentLayer == activeLayerIndex &&
                model.frame.layers.getOrNull(activeLayerIndex)?.id == activeLayerId)
        if (!targetStillValid) {
            cancelStroke()
        }
        invalidate()
    }

    fun cancelActiveStroke() {
        if (inker != null) cancelStroke()
        mode = Mode.NONE
    }

    fun fitToPaper(animate: Boolean = false) {
        val project = editor?.project ?: return
        if (width == 0 || height == 0) return
        val padding = dp(18f)
        val availableW = (width - padding * 2f).coerceAtLeast(1f)
        val availableH = (height - padding * 2f).coerceAtLeast(1f)
        val newScale = min(availableW / project.width, availableH / project.height).coerceAtLeast(0.001f)
        fitScale = newScale
        val tx = (width - project.width * newScale) / 2f
        val ty = (height - project.height * newScale) / 2f
        userZoomed = false
        if (animate) animateTo(newScale, tx, ty) else {
            animator?.cancel()
            scale = newScale
            offsetX = tx
            offsetY = ty
            clampOffsets()
            invalidate()
        }
    }

    fun currentScale(): Float = scale

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != seenWidth || h != seenHeight) {
            seenWidth = w
            seenHeight = h
            if (!userZoomed) fitToPaper() else clampOffsets()
        }
    }

    // ------------------------------------------------------------------ draw

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val model = editor ?: return
        val project = model.project
        val frameIndex = if (previewFrameIndex >= 0) previewFrameIndex else model.currentFrame
        val frame = project.frameAt(frameIndex)
        val editing = previewFrameIndex < 0 && frameIndex == model.currentFrame

        paperRect.set(
            offsetX,
            offsetY,
            offsetX + project.width * scale,
            offsetY + project.height * scale
        )
        val radius = dp(if (userZoomed) 4f else 10f)

        // Soft paper lift.
        shadowPaint.color = if (darkTheme) Color.argb(110, 0, 0, 0) else Color.argb(16, 0, 0, 0)
        canvas.drawRoundRect(
            RectF(paperRect.left - dp(2f), paperRect.top + dp(2f), paperRect.right + dp(2f), paperRect.bottom + dp(9f)),
            radius + dp(2f), radius + dp(2f), shadowPaint
        )
        shadowPaint.color = if (darkTheme) Color.argb(70, 0, 0, 0) else Color.argb(8, 0, 0, 0)
        canvas.drawRoundRect(
            RectF(paperRect.left - dp(7f), paperRect.top + dp(5f), paperRect.right + dp(7f), paperRect.bottom + dp(16f)),
            radius + dp(7f), radius + dp(7f), shadowPaint
        )

        paperPaint.color = project.background
        canvas.drawRoundRect(paperRect, radius, radius, paperPaint)

        clipPath.reset()
        clipPath.addRoundRect(paperRect, radius, radius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clipPath)

        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        bitmapRect.set(0f, 0f, project.width.toFloat(), project.height.toFloat())

        if (editing) drawOnionSkins(canvas, model, project, frameIndex)

        if (previewFrameIndex >= 0) {
            val preview = model.renderer.composite(
                project, frame, min(project.width, 900), transparent = false
            )
            layerPaint.alpha = 255
            canvas.drawBitmap(preview, null, bitmapRect, layerPaint)
        } else {
            val drawingOnActive = editing && inker != null
            for (i in frame.layers.indices) {
                val layer = frame.layers[i]
                if (!layer.visible || layer.opacity <= 0.003f) continue
                val bitmap = model.renderer.editableLayerBitmap(project, layer)
                layerPaint.alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
                canvas.drawBitmap(bitmap, 0f, 0f, layerPaint)

                if (drawingOnActive && i == activeLayerIndex && strokeIsMarker) {
                    val overlay = markerBitmap
                    if (overlay != null) {
                        layerPaint.alpha =
                            (markerOpacity * layer.opacity * 255f).toInt().coerceIn(0, 255)
                        canvas.drawBitmap(overlay, 0f, 0f, layerPaint)
                    }
                }
            }
        }

        canvas.restoreToCount(save)

        borderPaint.color = if (darkTheme) Color.argb(40, 255, 255, 255) else Color.argb(30, 0, 0, 0)
        canvas.drawRoundRect(paperRect, radius, radius, borderPaint)

        if (editing && hoverVisible) drawCursor(canvas, model)
    }

    private fun drawOnionSkins(canvas: Canvas, model: EditorModel, project: Project, frameIndex: Int) {
        if (!model.onionEnabled) return
        val targetWidth = min(project.width, 640)
        val offsets = intArrayOf(-1, 1)
        for (dir in offsets) {
            val index = frameIndex + dir
            if (index !in project.frames.indices) continue
            val neighbor = project.frames[index]
            if (neighbor.strokeCount == 0) continue
            val bitmap = model.renderer.composite(project, neighbor, targetWidth, transparent = true)
            onionPaint.colorFilter = PorterDuffColorFilter(
                if (dir < 0) 0xFFFF7A45.toInt() else 0xFF3B9CFF.toInt(),
                PorterDuff.Mode.SRC_IN
            )
            onionPaint.alpha = if (dir < 0) 92 else 74
            canvas.drawBitmap(bitmap, null, bitmapRect, onionPaint)
        }
        onionPaint.colorFilter = null
        onionPaint.alpha = 255
    }

    private fun drawCursor(canvas: Canvas, model: EditorModel) {
        val project = model.project
        val radius = max(dp(2.5f), model.size * scale * 0.5f)
        val isEraser = hoverErase || model.tool == Tool.ERASER
        cursorPaint.strokeWidth = dp(1.2f)
        cursorPaint.color = if (isEraser) Color.argb(220, 255, 90, 70) else Color.argb(230, 40, 120, 255)
        canvas.drawCircle(hoverX, hoverY, radius, cursorPaint)
        if (isEraser) {
            cursorFill.color = Color.argb(200, 255, 255, 255)
            canvas.drawCircle(hoverX, hoverY, dp(1.4f), cursorFill)
        } else {
            cursorFill.color = Color.argb(230, 255, 255, 255)
            canvas.drawCircle(hoverX, hoverY, dp(1.1f), cursorFill)
        }
        // Cross-hair for precision.
        if (!isEraser) {
            cursorPaint.color = Color.argb(70, 20, 20, 20)
            cursorPaint.strokeWidth = dp(0.8f)
            canvas.drawLine(hoverX - radius - dp(3f), hoverY, hoverX - radius - dp(1f), hoverY, cursorPaint)
            canvas.drawLine(hoverX + radius + dp(1f), hoverY, hoverX + radius + dp(3f), hoverY, cursorPaint)
            canvas.drawLine(hoverX, hoverY - radius - dp(3f), hoverX, hoverY - radius - dp(1f), cursorPaint)
            canvas.drawLine(hoverX, hoverY + radius + dp(1f), hoverX, hoverY + radius + dp(3f), cursorPaint)
        }
    }

    // ----------------------------------------------------------------- touch

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val model = editor ?: return false
        if (model.isPlaying || previewFrameIndex >= 0) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event, model)
            MotionEvent.ACTION_POINTER_DOWN -> handlePointerDown(event, model)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
            MotionEvent.ACTION_UP -> handleUp(event)
            MotionEvent.ACTION_CANCEL -> {
                cancelStroke()
                mode = Mode.NONE
            }
        }
        return true
    }

    private fun handleDown(event: MotionEvent, model: EditorModel) {
        val index = 0
        val toolType = event.getToolType(index)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS ||
            toolType == MotionEvent.TOOL_TYPE_ERASER ||
            toolType == MotionEvent.TOOL_TYPE_MOUSE
        val isTouch = toolType == MotionEvent.TOOL_TYPE_FINGER
        val palmSized = isTouch && event.getSize(index) > 0.32f
        val hoverButtonErase = (event.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY)) != 0

        activePointerId = event.getPointerId(index)
        panLastX = event.x
        panLastY = event.y
        suppressUntilUp = false
        animator?.cancel()

        val doubleTap = isTouch && !palmSized && isDoubleTap(event)
        if (doubleTap) {
            handleDoubleTap(event.x, event.y)
            lastTapTime = 0L
            suppressUntilUp = true
            mode = Mode.NONE
            return
        }
        if (isTouch && !palmSized) {
            lastTapTime = System.currentTimeMillis()
            lastTapX = event.x
            lastTapY = event.y
        }

        if (!isStylus && (model.stylusOnly || palmSized)) {
            // Fingers navigate. Palms are ignored entirely.
            mode = if (palmSized) Mode.NONE else Mode.PAN
            return
        }

        startStrokeAt(event, index, hoverButtonErase)
    }

    private fun handlePointerDown(event: MotionEvent, model: EditorModel) {
        val newIndex = event.actionIndex
        val newToolType = event.getToolType(newIndex)
        val newIsPen = newToolType == MotionEvent.TOOL_TYPE_STYLUS ||
            newToolType == MotionEvent.TOOL_TYPE_ERASER ||
            newToolType == MotionEvent.TOOL_TYPE_MOUSE
        val drawingWithPen = drawPointerToolType == MotionEvent.TOOL_TYPE_STYLUS ||
            drawPointerToolType == MotionEvent.TOOL_TYPE_ERASER ||
            drawPointerToolType == MotionEvent.TOOL_TYPE_MOUSE
        val eraserOverride = (event.buttonState and
            (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY)) != 0

        if (mode == Mode.DRAW) {
            if (newIsPen && !drawingWithPen) {
                // A pen arrived while a finger stroke was in progress. Hand over to the pen.
                cancelStroke()
                startStrokeAt(event, newIndex, eraserOverride)
            }
            // Otherwise this is a palm or spare finger: ignore it completely.
            return
        }
        if (newIsPen) {
            // A palm may have landed first. Abandon navigation and let the pen draw.
            mode = Mode.NONE
            startStrokeAt(event, newIndex, eraserOverride)
            return
        }
        if (event.pointerCount >= 2) {
            val a = 0
            val b = 1
            pinchPointerA = event.getPointerId(a)
            pinchPointerB = event.getPointerId(b)
            pinchStartSpan = spanOf(event, a, b).coerceAtLeast(1f)
            pinchStartScale = scale
            pinchFocusX = (event.getX(a) + event.getX(b)) * 0.5f
            pinchFocusY = (event.getY(a) + event.getY(b)) * 0.5f
            pinchWorldX = (pinchFocusX - offsetX) / scale
            pinchWorldY = (pinchFocusY - offsetY) / scale
            mode = Mode.PINCH
        }
    }

    private fun startStrokeAt(event: MotionEvent, index: Int, eraserOverride: Boolean) {
        activePointerId = event.getPointerId(index)
        panLastX = event.getX(index)
        panLastY = event.getY(index)
        animator?.cancel()
        suppressUntilUp = false
        beginStroke(event, index, eraserOverride)
    }

    private fun handleMove(event: MotionEvent) {
        when (mode) {
            Mode.DRAW -> {
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) return
                appendSamples(event, index)
            }
            Mode.PAN -> {
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) return
                offsetX += event.getX(index) - panLastX
                offsetY += event.getY(index) - panLastY
                panLastX = event.getX(index)
                panLastY = event.getY(index)
                clampOffsets()
                invalidate()
            }
            Mode.PINCH -> updatePinch(event)
            Mode.NONE -> Unit
        }
    }

    private fun updatePinch(event: MotionEvent) {
        val indexA = event.findPointerIndex(pinchPointerA)
        val indexB = event.findPointerIndex(pinchPointerB)
        if (indexA < 0 || indexB < 0) return
        val span = spanOf(event, indexA, indexB).coerceAtLeast(1f)
        val focusX = (event.getX(indexA) + event.getX(indexB)) * 0.5f
        val focusY = (event.getY(indexA) + event.getY(indexB)) * 0.5f
        val minScale = max(0.02f, fitScale * 0.35f)
        val maxScale = max(2f, fitScale * 12f)
        val newScale = (pinchStartScale * (span / pinchStartSpan)).coerceIn(minScale, maxScale)
        offsetX = focusX - pinchWorldX * newScale
        offsetY = focusY - pinchWorldY * newScale
        scale = newScale
        userZoomed = true
        clampOffsets()
        invalidate()
    }

    private fun handlePointerUp(event: MotionEvent) {
        val lifted = event.getPointerId(event.actionIndex)
        if (mode == Mode.DRAW && lifted == activePointerId) {
            finishStroke()
            mode = Mode.NONE
            return
        }
        if (mode == Mode.PINCH && event.pointerCount - 1 < 2) {
            mode = Mode.NONE
        }
        if (mode == Mode.PAN && lifted == activePointerId) {
            mode = Mode.NONE
        }
    }

    private fun handleUp(event: MotionEvent) {
        if (suppressUntilUp) {
            suppressUntilUp = false
            mode = Mode.NONE
            return
        }
        if (mode == Mode.DRAW) finishStroke()
        mode = Mode.NONE
        activePointerId = -1
    }

    private fun isDoubleTap(event: MotionEvent): Boolean {
        val now = System.currentTimeMillis()
        val close = abs(event.x - lastTapX) < dp(40f) && abs(event.y - lastTapY) < dp(40f)
        return now - lastTapTime < 300L && close
    }

    private fun handleDoubleTap(x: Float, y: Float) {
        val project = editor?.project ?: return
        if (scale > fitScale * 1.15f) {
            fitToPaper(animate = true)
        } else {
            val targetScale = (fitScale * 2.4f).coerceAtMost(max(2f, fitScale * 12f))
            val wx = (x - offsetX) / scale
            val wy = (y - offsetY) / scale
            animateTo(targetScale, x - wx * targetScale, y - wy * targetScale)
            userZoomed = true
        }
        lastTapTime = 0L
    }

    private fun animateTo(targetScale: Float, targetX: Float, targetY: Float) {
        animator?.cancel()
        val startScale = scale
        val startX = offsetX
        val startY = offsetY
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val t = animation.animatedValue as Float
                scale = startScale + (targetScale - startScale) * t
                offsetX = startX + (targetX - startX) * t
                offsetY = startY + (targetY - startY) * t
                clampOffsets()
                invalidate()
            }
            start()
        }
    }

    // --------------------------------------------------------------- strokes

    private var drawPointerToolType = MotionEvent.TOOL_TYPE_FINGER

    private fun beginStroke(event: MotionEvent, index: Int, eraserOverride: Boolean) {
        val model = editor ?: return
        val project = model.project
        val layer = model.layer
        val activeTool = if (eraserOverride) Tool.ERASER else model.tool
        drawPointerToolType = event.getToolType(index)
        strokeIsMarker = activeTool == Tool.MARKER && !eraserOverride
        markerOpacity = model.markerOpacity
        activeLayerIndex = model.currentLayer
        activeLayerId = layer.id
        strokeFrameIndex = model.currentFrame
        activeBitmap = model.renderer.editableLayerBitmap(project, layer)
        val width = model.size
        inker = StrokeInker(
            tool = activeTool,
            color = model.color,
            baseWidth = width,
            opacity = if (activeTool == Tool.MARKER) markerOpacity else 1f,
            pressureEnabled = model.pressureEnabled
        )
        if (strokeIsMarker) {
            markerBitmap = model.renderer.markerOverlay(project)
            model.renderer.clearMarkerOverlay()
        } else {
            markerBitmap = null
        }
        model.ensureLayerVisibleForInk()
        mode = Mode.DRAW
        addSample(event, index)
    }

    private fun appendSamples(event: MotionEvent, index: Int) {
        val active = inker ?: return
        val history = event.historySize
        for (h in 0 until history) {
            val px = event.getHistoricalX(index, h)
            val py = event.getHistoricalY(index, h)
            val pressure = event.getHistoricalPressure(index, h)
            active.addRaw(toProjectX(px), toProjectY(py), pressureFor(active.tool, event, index, pressure))
        }
        val added = active.addRaw(
            toProjectX(event.getX(index)),
            toProjectY(event.getY(index)),
            pressureFor(active.tool, event, index, event.getPressure(index))
        )
        if (added || history > 0) {
            val canvas = inkCanvas() ?: return
            active.drawNew(canvas)
            invalidate()
        }
    }

    private fun addSample(event: MotionEvent, index: Int) {
        val active = inker ?: return
        active.addRaw(
            toProjectX(event.getX(index)),
            toProjectY(event.getY(index)),
            pressureFor(active.tool, event, index, event.getPressure(index))
        )
        val canvas = inkCanvas() ?: return
        active.drawNew(canvas)
        invalidate()
    }

    private fun pressureFor(tool: Tool, event: MotionEvent, index: Int, pressure: Float): Float {
        val type = event.getToolType(index)
        return when (type) {
            MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> pressure
            else -> 1f
        }
    }

    private fun finishStroke() {
        val active = inker ?: return
        val bitmap = activeBitmap
        val model = editor
        if (bitmap == null || model == null) {
            resetStroke()
            return
        }
        val overlay = markerBitmap
        if (strokeIsMarker) {
            val overlayCanvas = overlay?.let { Canvas(it) } ?: Canvas(bitmap)
            active.finish(overlayCanvas)
            if (overlay != null) {
                val layerCanvas = Canvas(bitmap)
                markerCompositePaint.alpha = (markerOpacity * 255f).toInt().coerceIn(0, 255)
                layerCanvas.drawBitmap(overlay, 0f, 0f, markerCompositePaint)
            }
            model.renderer.clearMarkerOverlay()
        } else {
            active.finish(Canvas(bitmap))
        }
        val stroke = active.snapshot()
        if (stroke.pointCount > 0 && strokeFrameIndex >= 0) {
            model.addStroke(stroke, strokeFrameIndex, activeLayerIndex)
            model.renderer.noteStrokeCommitted(activeLayerId)
        }
        resetStroke()
        invalidate()
    }

    private fun cancelStroke() {
        val bitmap = activeBitmap
        if (bitmap != null) {
            editor?.renderer?.invalidateLayer(activeLayerId)
            editor?.renderer?.clearMarkerOverlay()
        }
        resetStroke()
        invalidate()
    }

    private fun inkCanvas(): Canvas? = if (strokeIsMarker) {
        markerBitmap?.let { Canvas(it) }
    } else {
        activeBitmap?.let { Canvas(it) }
    }

    private fun resetStroke() {
        inker = null
        activeBitmap = null
        markerBitmap = null
        activeLayerIndex = -1
        activeLayerId = -1L
        strokeFrameIndex = -1
        strokeIsMarker = false
    }

    // ------------------------------------------------------------------ misc

    private fun toProjectX(x: Float): Float = (x - offsetX) / scale
    private fun toProjectY(y: Float): Float = (y - offsetY) / scale

    private fun spanOf(event: MotionEvent, a: Int, b: Int): Float =
        hypot(event.getX(a) - event.getX(b), event.getY(a) - event.getY(b))

    private fun clampOffsets() {
        val project = editor?.project ?: return
        val paperW = project.width * scale
        val paperH = project.height * scale
        val marginX = min(dp(72f), paperW * 0.8f)
        val marginY = min(dp(72f), paperH * 0.8f)
        offsetX = offsetX.coerceIn(-paperW + marginX, width - marginX)
        offsetY = offsetY.coerceIn(-paperH + marginY, height - marginY)
        if (paperW < width) {
            offsetX = (width - paperW) / 2f
        }
        if (paperH < height) {
            offsetY = (height - paperH) / 2f
        }
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                hoverVisible = true
                hoverX = event.x
                hoverY = event.y
                hoverErase = (event.buttonState and
                    (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY)) != 0
                invalidate()
                return true
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                hoverVisible = false
                invalidate()
                return true
            }
        }
        return super.onHoverEvent(event)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
