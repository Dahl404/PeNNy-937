package com.dahl404.penny937.ui.canvas

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
import com.dahl404.penny937.data.ReferenceFiles
import com.dahl404.penny937.editor.EditorModel
import com.dahl404.penny937.editor.ReferencePlacement
import com.dahl404.penny937.model.GridStyle
import com.dahl404.penny937.model.Project
import com.dahl404.penny937.model.ShapeKind
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import com.dahl404.penny937.paint.Ink
import com.dahl404.penny937.paint.Shapes
import com.dahl404.penny937.paint.StrokeInker
import com.dahl404.penny937.paint.Warp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Stylus-first drawing surface.
 *
 * - Pressure-sensitive, smoothed ink drawn as one ribbon outline per stroke.
 * - The live stroke is buffered in a scratch bitmap and blended into the layer once,
 *   so translucent ink never doubles up and the eraser can preview without damage.
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
    private var lastCanvasRevision: Int = Int.MIN_VALUE

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
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val clipPath = Path()
    private val paperRect = RectF()
    private val bitmapRect = RectF()

    private val viewport = CanvasViewport(
        viewWidth = { width },
        viewHeight = { height },
        fitPaddingPx = { dp(18f) },
        edgeMarginPx = { dp(72f) },
        onChange = { invalidate() }
    )

    private var pendingViewport: CanvasViewport.State? = null

    private var scale: Float
        get() = viewport.scale
        set(value) { viewport.scale = value }
    private var offsetX: Float
        get() = viewport.offsetX
        set(value) { viewport.offsetX = value }
    private var offsetY: Float
        get() = viewport.offsetY
        set(value) { viewport.offsetY = value }
    private val fitScale: Float
        get() = viewport.fitScale
    private var userZoomed: Boolean
        get() = viewport.userZoomed
        set(value) { viewport.userZoomed = value }

    private var activePointerId = -1
    private var mode = Mode.NONE
    private var inker: StrokeInker? = null
    private var activeLayerIndex = -1
    private var activeLayerId = -1L
    private var strokeFrameIndex = -1
    private var activeBitmap: Bitmap? = null
    private var overlayBitmap: Bitmap? = null
    private var overlayCanvas: Canvas? = null
    private var strokeOpacity = 1f
    private var strokeTool = Tool.PEN
    private val strokeBounds = RectF()
    private val strokeRect = android.graphics.Rect()
    private val overlayCompositePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private var shapeKind = ShapeKind.LINE
    private var shapeStartX = 0f
    private var shapeStartY = 0f
    private var shapeCurrentX = 0f
    private var shapeCurrentY = 0f
    private var shapeColor = 0
    private var shapeWidth = 8f
    private var shapePreview: Stroke? = null

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

    private var eyedropperActive = false
    private var eyedropperPointerId = -1
    private var eyedropperX = 0f
    private var eyedropperY = 0f

    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var suppressUntilUp = false

    private enum class PlaceMode { NONE, ONE, TWO }

    private var placeMode = PlaceMode.NONE
    private var placePointer = -1
    private var placeLastX = 0f
    private var placeLastY = 0f
    private var placePointerA = -1
    private var placePointerB = -1
    private var placeStartSpan = 1f
    private var placeStartAngle = 0f
    private var placeStartMidX = 0f
    private var placeStartMidY = 0f
    private var placeStartCenterX = 0f
    private var placeStartCenterY = 0f
    private var placeStartWidth = 0f
    private var placeStartHeight = 0f
    private var placeStartRotation = 0f
    private var placeAnchorLocalX = 0f
    private var placeAnchorLocalY = 0f

    private var shiftPointer = -1
    private var shiftLastX = 0f
    private var shiftLastY = 0f
    private var shiftPinchA = -1
    private var shiftPinchB = -1
    private var shiftBaseDx = 0f
    private var shiftBaseDy = 0f
    private var shiftBaseRotation = 0f
    private var shiftBaseAngle = 0f
    private var shiftBaseMidX = 0f
    private var shiftBaseMidY = 0f

    private var morphSession: MorphSession? = null
    private var morphPointer = -1
    private var morphLastX = 0f
    private var morphLastY = 0f
    private var morphAtX = 0f
    private var morphAtY = 0f
    private var morphPanning = false
    private var morphPinching = false
    private val patchRect = RectF()

    private enum class Mode { NONE, DRAW, SHAPE, PAN, PINCH }

    fun bind(model: EditorModel, revision: Int = 0) {
        lastCanvasRevision = revision
        val previous = editor
        editor = model
        model.onBeforeStructuralChange = { cancelActiveStroke() }
        val dimsChanged = previous?.project?.width != model.project.width ||
            previous?.project?.height != model.project.height
        val projectChanged = previous?.project?.id != model.project.id
        if (projectChanged) {
            persistViewport(previous)
            cancelStroke()
            userZoomed = false
            pendingViewport = CanvasViewportCodec.decode(model.savedViewportState())
            if (pendingViewport == null) {
                fitToPaper()
            } else if (width > 0 && height > 0) {
                viewport.updateProject(model.project.width, model.project.height)
                viewport.restore(pendingViewport!!)
                pendingViewport = null
            }
        } else if (dimsChanged) {
            cancelStroke()
            userZoomed = false
            fitToPaper()
        }
        if (previous != model) {
            mode = Mode.NONE
        }
        val targetActive = inker != null || mode == Mode.SHAPE
        val targetStillValid = !targetActive ||
            (model.currentFrame == strokeFrameIndex &&
                model.currentLayer == activeLayerIndex &&
                model.frame.layers.getOrNull(activeLayerIndex)?.id == activeLayerId)
        if (!targetStillValid) {
            cancelStroke()
        }
        if (model.eyedropper == null) eyedropperActive = false
        syncMorphSession(model)
        invalidate()
    }

    fun cancelActiveStroke() {
        if (inker != null) cancelStroke()
        mode = Mode.NONE
    }

    fun fitToPaper(animate: Boolean = false) {
        val project = editor?.project ?: return
        viewport.updateProject(project.width, project.height)
        viewport.fitToPaper(animate)
    }

    fun currentScale(): Float = viewport.currentScale()

    /**
     * Quantise the current zoom to one of a few cache scales. Zooming then only
     * re-renders layers when it crosses a step, and pinch frames stay cheap.
     */
    private fun syncInteractiveScale(project: Project) {
        val raw = scale.coerceIn(0f, 1f)
        val step = (ceil(raw * 8f).toInt()).coerceIn(1, 8) / 8f
        editor?.renderer?.setInteractiveScale(step)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewport.onSizeChanged(w, h, oldw, oldh)
    }

    // ------------------------------------------------------------------ draw

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val model = editor ?: return
        val project = model.project
        viewport.updateProject(project.width, project.height)
        pendingViewport?.let { saved ->
            if (width > 0 && height > 0) {
                viewport.restore(saved)
                pendingViewport = null
            }
        }
        // Keep the interactive cache at roughly display resolution. Skip updates
        // while a pinch or animated zoom is in flight so layers are not re-rendered
        // on every frame; onDraw syncs again as soon as the gesture settles.
        if (inker == null && mode != Mode.PINCH && viewport.animator?.isRunning != true) {
            syncInteractiveScale(project)
        }
        val frameIndex = if (previewFrameIndex >= 0) previewFrameIndex else model.currentFrame
        val frame = project.frameAt(frameIndex)
        val editing = previewFrameIndex < 0 && frameIndex == model.currentFrame
        syncMorphSession(model)

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

        if (editing && model.eyedropper == null) {
            drawOnionSkins(canvas, model, project, frameIndex)
        }

        if (previewFrameIndex >= 0) {
            // Playback frames are rasterised on a worker thread and only handed
            // over when ready; the canvas never blocks the UI thread on vectors.
            val width = min(project.width, 900)
            val preview = model.renderer.playbackBitmap(
                frameIndex, frame.id, frame.revision, width
            )
            if (preview != null) {
                layerPaint.alpha = 255
                canvas.drawBitmap(preview, null, bitmapRect, layerPaint)
            }
        } else {
            val drawingOnActive = editing && inker != null
            val placingLayerId = model.placement?.targetLayerId
            val pickingLayerId = model.eyedropper?.layerId
            val shift = model.layerShift
            for (i in frame.layers.indices) {
                val layer = frame.layers[i]
                val isPicked = pickingLayerId == layer.id
                if (pickingLayerId == null) {
                    if (!layer.visible || layer.opacity <= 0.003f) continue
                } else if (!isPicked) {
                    if (!layer.visible || layer.opacity <= 0.003f) continue
                } else if (layer.opacity <= 0.003f) {
                    continue
                }
                if (layer.id == placingLayerId) continue
                val bitmap = model.renderer.editableLayerBitmap(project, layer)
                val displayOpacity = when {
                    pickingLayerId == null || isPicked -> layer.opacity
                    else -> layer.opacity * 0.18f
                }
                val shiftLayer = shift?.takeIf {
                    it.frameIndex == frameIndex && it.layerId == layer.id
                }
                val morphLayer = model.morph?.takeIf {
                    it.frameIndex == frameIndex && it.layerId == layer.id
                }
                val shiftMoved = shiftLayer != null &&
                    (shiftLayer.dx != 0f || shiftLayer.dy != 0f ||
                        shiftLayer.rotation != 0f)
                if (shiftMoved) {
                    // "Before" ghost at the untransformed position.
                    layerPaint.alpha = (displayOpacity * 0.16f * 255f).toInt().coerceIn(0, 255)
                    canvas.drawBitmap(bitmap, null, bitmapRect, layerPaint)
                }
                val layerSave = if (shiftLayer != null) canvas.save() else -1
                if (shiftLayer != null) {
                    canvas.translate(shiftLayer.dx, shiftLayer.dy)
                    if (shiftLayer.rotation != 0f) {
                        canvas.rotate(shiftLayer.rotation, shiftLayer.pivotX, shiftLayer.pivotY)
                    }
                }
                layerPaint.alpha = (displayOpacity * 255f).toInt().coerceIn(0, 255)
                val erasingHere = drawingOnActive && i == activeLayerIndex &&
                    strokeTool == Tool.ERASER
                val activeMorph = morphSession
                if (morphLayer != null && activeMorph?.dirty != null) {
                    drawMorphPreview(canvas, model, activeMorph, bitmap, displayOpacity)
                } else if (erasingHere) {
                    // The erased view replaces the layer, it does not sit on top of it.
                    drawErasePreview(canvas, bitmap, displayOpacity)
                } else {
                    canvas.drawBitmap(bitmap, null, bitmapRect, layerPaint)
                }

                if (drawingOnActive && i == activeLayerIndex && !erasingHere) {
                    drawLiveStroke(canvas, displayOpacity)
                }
                if (shiftLayer != null) canvas.restoreToCount(layerSave)
            }
        }

        if (editing) {
            if (model.gridEnabled && model.eyedropper == null) {
                drawGrid(canvas, model, project)
            }
            val preview = shapePreview
            if (preview != null) Ink.renderStroke(canvas, preview)
            model.placement?.let { drawPlacement(canvas, it) }
        }

        canvas.restoreToCount(save)

        borderPaint.color = if (darkTheme) Color.argb(40, 255, 255, 255) else Color.argb(30, 0, 0, 0)
        canvas.drawRoundRect(paperRect, radius, radius, borderPaint)

        if (model.eyedropper != null && eyedropperActive) drawEyedropperLoupe(canvas, model)
        if (editing && model.tool == Tool.MORPH && (morphPointer >= 0 || hoverVisible)) {
            drawMorphRing(canvas, model)
        }
        if (editing && hoverVisible && model.placement == null &&
            model.eyedropper == null && model.tool != Tool.MORPH
        ) {
            drawCursor(canvas, model)
        }
    }

    private fun drawOnionSkins(canvas: Canvas, model: EditorModel, project: Project, frameIndex: Int) {
        if (model.placement != null) return
        val strength = model.onionStrength
        if (kotlin.math.abs(strength) < 0.01f) return
        val dir = if (strength < 0f) -1 else 1
        val index = frameIndex + dir
        if (index !in project.frames.indices) return
        val neighbor = project.frames[index]
        if (neighbor.strokeCount == 0) return
        val targetWidth = min(project.width, 640)
        val bitmap = model.renderer.composite(project, neighbor, targetWidth, transparent = true)
        onionPaint.colorFilter = PorterDuffColorFilter(
            if (dir < 0) 0xFF3B9CFF.toInt() else 0xFFFF5A48.toInt(),
            PorterDuff.Mode.SRC_IN
        )
        onionPaint.alpha = (kotlin.math.abs(strength).coerceIn(0f, 1f) * 140f)
            .toInt().coerceIn(3, 160)
        canvas.drawBitmap(bitmap, null, bitmapRect, onionPaint)
        onionPaint.colorFilter = null
        onionPaint.alpha = 255
    }

    private fun drawGrid(canvas: Canvas, model: EditorModel, project: Project) {
        val paperIsDark = Color.luminance(project.background) < 0.4f
        val base = if (paperIsDark) Color.argb(28, 255, 255, 255) else Color.argb(24, 0, 0, 0)
        val strong = if (paperIsDark) Color.argb(52, 255, 255, 255) else Color.argb(46, 0, 0, 0)
        guidePaint.strokeWidth = (1f / scale).coerceAtLeast(0.5f)
        val w = project.width.toFloat()
        val h = project.height.toFloat()
        when (model.gridStyle) {
            GridStyle.GRID -> {
                val cell = min(w, h) / 8f
                if (cell < 2f) return
                guidePaint.color = base
                var x = cell
                while (x < w - 0.5f) {
                    canvas.drawLine(x, 0f, x, h, guidePaint)
                    x += cell
                }
                var y = cell
                while (y < h - 0.5f) {
                    canvas.drawLine(0f, y, w, y, guidePaint)
                    y += cell
                }
                guidePaint.color = strong
                canvas.drawLine(w / 2f, 0f, w / 2f, h, guidePaint)
                canvas.drawLine(0f, h / 2f, w, h / 2f, guidePaint)
            }
            GridStyle.THIRDS -> {
                guidePaint.color = strong
                for (i in 1..2) {
                    val x = w * i / 3f
                    val y = h * i / 3f
                    canvas.drawLine(x, 0f, x, h, guidePaint)
                    canvas.drawLine(0f, y, w, y, guidePaint)
                }
            }
        }
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
        if (model.layerShift != null) {
            handleLayerShiftTouch(event, model)
            return true
        }
        if (model.placement != null) {
            handlePlacementTouch(event, model)
            return true
        }
        if (model.eyedropper != null) {
            handleEyedropperTouch(event, model)
            return true
        }
        if (model.isPlaying || previewFrameIndex >= 0) return true
        if (model.tool == Tool.MORPH) {
            handleMorphTouch(event, model)
            return true
        }

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

    private fun handleEyedropperTouch(event: MotionEvent, model: EditorModel) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val index = 0
                if (isPalm(event, index)) return
                startEyedropperPointer(event, index, model)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                if (isPalm(event, index)) return
                val isPen = isPen(event, index)
                // A resting palm may have arrived first; let the pen take over.
                if (eyedropperActive && !isPen) return
                startEyedropperPointer(event, index, model)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(eyedropperPointerId)
                if (index >= 0) sampleEyedropperAt(event.getX(index), event.getY(index), model)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                if (event.getPointerId(index) != eyedropperPointerId) return
                sampleEyedropperAt(event.getX(index), event.getY(index), model)
                finishEyedropperSample(model)
            }
            MotionEvent.ACTION_UP -> {
                if (!eyedropperActive) return
                val index = event.findPointerIndex(eyedropperPointerId)
                if (index >= 0) sampleEyedropperAt(event.getX(index), event.getY(index), model)
                finishEyedropperSample(model)
            }
            MotionEvent.ACTION_CANCEL -> {
                model.cancelEyedropper()
                eyedropperActive = false
                eyedropperPointerId = -1
                invalidate()
            }
        }
    }

    private fun isPalm(event: MotionEvent, index: Int): Boolean =
        event.getToolType(index) == MotionEvent.TOOL_TYPE_FINGER &&
            event.getSize(index) > 0.32f

    private fun isPen(event: MotionEvent, index: Int): Boolean {
        val type = event.getToolType(index)
        return type == MotionEvent.TOOL_TYPE_STYLUS ||
            type == MotionEvent.TOOL_TYPE_ERASER ||
            type == MotionEvent.TOOL_TYPE_MOUSE
    }

    private fun startEyedropperPointer(event: MotionEvent, index: Int, model: EditorModel) {
        eyedropperActive = true
        eyedropperPointerId = event.getPointerId(index)
        sampleEyedropperAt(event.getX(index), event.getY(index), model)
    }

    private fun finishEyedropperSample(model: EditorModel) {
        model.finishEyedropper()
        eyedropperActive = false
        eyedropperPointerId = -1
        invalidate()
    }

    private fun sampleEyedropperAt(screenX: Float, screenY: Float, model: EditorModel) {
        eyedropperX = screenX
        eyedropperY = screenY
        val project = model.project
        val x = toProjectX(screenX)
        val y = toProjectY(screenY)
        if (x >= 0f && y >= 0f && x <= project.width && y <= project.height) {
            val radius = (dp(8f) / scale).coerceIn(1.5f, 64f)
            model.sampleEyedropper(x, y, radius)
        }
        invalidate()
    }

    private fun drawEyedropperLoupe(canvas: Canvas, model: EditorModel) {
        val radius = dp(27f)
        var cx = eyedropperX
        var cy = eyedropperY - dp(58f)
        if (cy - radius < dp(10f)) cy = eyedropperY + dp(58f)
        val margin = radius + dp(10f)
        val maxX = (width - margin).coerceAtLeast(margin)
        val maxY = (height - margin).coerceAtLeast(margin)
        cx = cx.coerceIn(margin, maxX)
        cy = cy.coerceIn(margin, maxY)

        shadowPaint.color = Color.argb(if (darkTheme) 120 else 38, 0, 0, 0)
        canvas.drawCircle(cx, cy + dp(2.5f), radius + dp(1.5f), shadowPaint)

        cursorFill.color = model.color
        canvas.drawCircle(cx, cy, radius, cursorFill)
        cursorPaint.style = Paint.Style.STROKE
        cursorPaint.strokeWidth = dp(1.2f)
        cursorPaint.color = Color.argb(235, 255, 255, 255)
        canvas.drawCircle(cx, cy, radius, cursorPaint)
        cursorPaint.strokeWidth = dp(0.8f)
        cursorPaint.color = Color.argb(80, 0, 0, 0)
        canvas.drawCircle(cx, cy, radius + dp(1.4f), cursorPaint)

        cursorPaint.strokeWidth = dp(1.4f)
        cursorPaint.color = Color.argb(210, 255, 255, 255)
        canvas.drawCircle(eyedropperX, eyedropperY, dp(7f), cursorPaint)
        cursorPaint.strokeWidth = dp(0.8f)
        cursorPaint.color = Color.argb(120, 0, 0, 0)
        canvas.drawCircle(eyedropperX, eyedropperY, dp(8.2f), cursorPaint)
        cursorFill.color = Color.argb(215, 255, 255, 255)
        canvas.drawCircle(eyedropperX, eyedropperY, dp(1.5f), cursorFill)
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
        viewport.cancelAnimation()

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

        if (mode == Mode.DRAW || mode == Mode.SHAPE) {
            if (drawingWithPen) {
                // A palm or spare finger landed while the pen was working. Ignore it.
                return
            }
            if (newIsPen) {
                // A pen arrived while a finger stroke/shape was in progress.
                cancelStroke()
                startStrokeAt(event, newIndex, eraserOverride)
                return
            }
            // A second finger on a finger stroke/shape: abandon it and pinch.
            cancelStroke()
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
        viewport.cancelAnimation()
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
            Mode.SHAPE -> updateShape(event)
            Mode.PAN -> {
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) return
                viewport.panBy(event.getX(index) - panLastX, event.getY(index) - panLastY)
                panLastX = event.getX(index)
                panLastY = event.getY(index)
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
        viewport.zoomTo(newScale, focusX, focusY, pinchWorldX, pinchWorldY)
    }

    private fun handlePointerUp(event: MotionEvent) {
        val lifted = event.getPointerId(event.actionIndex)
        if (mode == Mode.DRAW && lifted == activePointerId) {
            finishStroke()
            mode = Mode.NONE
            return
        }
        if (mode == Mode.SHAPE && lifted == activePointerId) {
            commitShape()
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
        when (mode) {
            Mode.DRAW -> finishStroke()
            Mode.SHAPE -> commitShape()
            else -> Unit
        }
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
        viewport.updateProject(project.width, project.height)
        viewport.toggleZoom(x, y)
        lastTapTime = 0L
    }

    // --------------------------------------------------------------- strokes

    private var drawPointerToolType = MotionEvent.TOOL_TYPE_FINGER

    private fun beginStroke(event: MotionEvent, index: Int, eraserOverride: Boolean) {
        val model = editor ?: return
        if (model.layer.locked) {
            model.showHint("Layer is locked")
            mode = Mode.NONE
            return
        }
        if (model.layer.isReference) {
            model.showHint("Reference layers are not drawable")
            mode = Mode.NONE
            return
        }
        if (model.tool == Tool.SHAPE && !eraserOverride) {
            beginShape(event, index, model)
            return
        }
        val project = model.project
        syncInteractiveScale(project)
        val layer = model.layer
        val activeTool = if (eraserOverride) Tool.ERASER else model.tool
        drawPointerToolType = event.getToolType(index)
        strokeTool = activeTool
        strokeOpacity = if (activeTool == Tool.MARKER) model.markerOpacity else 1f
        activeLayerIndex = model.currentLayer
        activeLayerId = layer.id
        strokeFrameIndex = model.currentFrame
        activeBitmap = model.renderer.editableLayerBitmap(project, layer)
        // Keep this layer cached until the stroke is committed. Building the
        // bitmaps for other layers can otherwise evict the active one on large
        // canvases with several layers, and finishStroke would draw into a
        // recycled bitmap.
        model.renderer.pinLayer(layer.id)
        inker = StrokeInker(
            tool = activeTool,
            color = model.color,
            baseWidth = model.size,
            opacity = strokeOpacity,
            pressureEnabled = model.pressureEnabled
        )
        val scratch = model.renderer.inkOverlay(project)
        model.renderer.clearInkOverlay()
        overlayBitmap = scratch
        overlayCanvas = Canvas(scratch).apply {
            scale(
                scratch.width.toFloat() / project.width,
                scratch.height.toFloat() / project.height
            )
        }
        model.ensureLayerVisibleForInk()
        mode = Mode.DRAW
        addSample(event, index)
    }

    private fun beginShape(event: MotionEvent, index: Int, model: EditorModel) {
        drawPointerToolType = event.getToolType(index)
        activeLayerIndex = model.currentLayer
        activeLayerId = model.layer.id
        strokeFrameIndex = model.currentFrame
        model.ensureLayerVisibleForInk()
        shapeKind = model.shapeKind
        shapeColor = model.color
        shapeWidth = model.size
        shapeStartX = toProjectX(event.getX(index))
        shapeStartY = toProjectY(event.getY(index))
        shapeCurrentX = shapeStartX
        shapeCurrentY = shapeStartY
        shapePreview = null
        mode = Mode.SHAPE
        invalidate()
    }

    private fun updateShape(event: MotionEvent) {
        val index = event.findPointerIndex(activePointerId)
        if (index < 0) return
        shapeCurrentX = toProjectX(event.getX(index))
        shapeCurrentY = toProjectY(event.getY(index))
        rebuildShapePreview()
    }

    private fun rebuildShapePreview() {
        shapePreview = Shapes.build(
            kind = shapeKind,
            x0 = shapeStartX,
            y0 = shapeStartY,
            x1 = shapeCurrentX,
            y1 = shapeCurrentY,
            color = shapeColor,
            width = shapeWidth
        )
        invalidate()
    }

    private fun commitShape() {
        val model = editor ?: run { resetShape(); return }
        val preview = shapePreview ?: Shapes.build(
            kind = shapeKind,
            x0 = shapeStartX,
            y0 = shapeStartY,
            x1 = shapeCurrentX,
            y1 = shapeCurrentY,
            color = shapeColor,
            width = shapeWidth
        )
        if (preview != null && strokeFrameIndex >= 0) {
            model.addStroke(preview, strokeFrameIndex, activeLayerIndex)
        }
        resetShape()
        invalidate()
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
            updateStrokeBounds(active)
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
        updateStrokeBounds(active)
        invalidate()
    }

    /**
     * Keeps the eraser preview's isolated region in sync with the live stroke.
     * Without this the preview clips the active layer to the previous stroke's
     * bounds, which looks like whole layers vanishing while erasing.
     */
    private fun updateStrokeBounds(active: StrokeInker) {
        active.bounds(strokeBounds)
        strokeBounds.roundOut(strokeRect)
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
        val model = editor
        if (model == null) {
            resetStroke()
            return
        }
        val bitmap = activeBitmap?.takeUnless { it.isRecycled }
        val overlay = overlayBitmap
        val scratchCanvas = overlayCanvas
        if (overlay != null && scratchCanvas != null && bitmap != null) {
            active.finish(scratchCanvas)
            updateStrokeBounds(active)
            // Both bitmaps are at the same interactive scale, so compositing the
            // whole scratch bitmap is equivalent to the old stroke-rect copy.
            Canvas(bitmap).drawBitmap(
                overlay,
                null,
                android.graphics.Rect(0, 0, bitmap.width, bitmap.height),
                Ink.compositePaintFor(strokeTool, strokeOpacity)
            )
            model.renderer.clearInkOverlay()
        }
        val stroke = active.snapshot()
        if (stroke.pointCount > 0 && strokeFrameIndex >= 0) {
            model.addStroke(stroke, strokeFrameIndex, activeLayerIndex)
            if (bitmap != null) {
                model.renderer.noteStrokeCommitted(model.layer)
            } else {
                // The cache was dropped under us; re-render from vectors so the
                // committed stroke appears instead of being recorded as cached.
                model.renderer.invalidateLayer(activeLayerId)
            }
        }
        resetStroke()
        invalidate()
    }

    private fun cancelStroke() {
        if (mode == Mode.SHAPE) {
            resetShape()
            mode = Mode.NONE
            invalidate()
            return
        }
        val bitmap = activeBitmap
        if (bitmap != null) {
            editor?.renderer?.invalidateLayer(activeLayerId)
            editor?.renderer?.clearInkOverlay()
        }
        resetStroke()
        invalidate()
    }

    private fun resetShape() {
        shapePreview = null
        shapeStartX = 0f
        shapeStartY = 0f
        shapeCurrentX = 0f
        shapeCurrentY = 0f
    }

    private fun drawPlacement(canvas: Canvas, placement: ReferencePlacement) {
        val bitmap = editor?.let { ReferenceFiles.bitmap(it.project.id, placement.fileName) }
        val halfW = placement.width / 2f
        val halfH = placement.height / 2f
        canvas.save()
        canvas.translate(placement.centerX, placement.centerY)
        canvas.rotate(placement.rotation)
        if (bitmap != null) {
            val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(-halfW, -halfH, halfW, halfH),
                imagePaint
            )
        } else {
            val missing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(38, 10, 132, 255)
            }
            canvas.drawRect(-halfW, -halfH, halfW, halfH, missing)
        }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f / scale
            color = Color.argb(230, 10, 132, 255)
        }
        canvas.drawRect(-halfW, -halfH, halfW, halfH, outline)
        val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(240, 10, 132, 255)
        }
        val r = 4.5f / scale
        for (x in floatArrayOf(-halfW, halfW)) {
            for (y in floatArrayOf(-halfH, halfH)) {
                canvas.drawCircle(x, y, r, handle)
            }
        }
        canvas.restore()
    }

    private fun handleLayerShiftTouch(event: MotionEvent, model: EditorModel) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                shiftPointer = event.getPointerId(0)
                shiftLastX = event.x
                shiftLastY = event.y
                clearShiftPinch()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) beginShiftPinch(event, model)
            }
            MotionEvent.ACTION_MOVE -> {
                val shift = model.layerShift ?: return
                val pinching = shiftPinchA >= 0 && shiftPinchB >= 0 &&
                    event.findPointerIndex(shiftPinchA) >= 0 &&
                    event.findPointerIndex(shiftPinchB) >= 0
                if (pinching) {
                    updateShiftPinch(event, model)
                } else {
                    val index = event.findPointerIndex(shiftPointer)
                    if (index >= 0) {
                        val dx = shift.dx + (event.getX(index) - shiftLastX) / scale
                        val dy = shift.dy + (event.getY(index) - shiftLastY) / scale
                        shiftLastX = event.getX(index)
                        shiftLastY = event.getY(index)
                        model.updateLayerShift(dx, dy)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val lifted = event.getPointerId(event.actionIndex)
                if (lifted == shiftPinchA || lifted == shiftPinchB) {
                    clearShiftPinch()
                    // Keep moving with whatever finger is still down.
                    val remaining = (0 until event.pointerCount)
                        .firstOrNull { event.getPointerId(it) != lifted }
                    if (remaining != null) {
                        shiftPointer = event.getPointerId(remaining)
                        shiftLastX = event.getX(remaining)
                        shiftLastY = event.getY(remaining)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                shiftPointer = -1
                clearShiftPinch()
            }
        }
    }

    /** Two fingers on a shifted layer rotate it around the layer's own centre. */
    private fun beginShiftPinch(event: MotionEvent, model: EditorModel) {
        val shift = model.layerShift ?: return
        val a = 0
        val b = 1
        shiftPinchA = event.getPointerId(a)
        shiftPinchB = event.getPointerId(b)
        shiftBaseDx = shift.dx
        shiftBaseDy = shift.dy
        shiftBaseRotation = shift.rotation
        shiftBaseAngle = angleOf(event, a, b)
        shiftBaseMidX = (event.getX(a) + event.getX(b)) * 0.5f
        shiftBaseMidY = (event.getY(a) + event.getY(b)) * 0.5f
        shiftPointer = -1
    }

    private fun updateShiftPinch(event: MotionEvent, model: EditorModel) {
        val a = event.findPointerIndex(shiftPinchA)
        val b = event.findPointerIndex(shiftPinchB)
        if (a < 0 || b < 0) return
        val delta = Math.toDegrees(
            (angleOf(event, a, b) - shiftBaseAngle).toDouble()
        ).toFloat()
        var rotation = normalizeAngle(shiftBaseRotation + delta)
        val snapped = (rotation / 45f).roundToInt() * 45f
        if (abs(rotation - snapped) < 3.5f) rotation = normalizeAngle(snapped)
        val midX = (event.getX(a) + event.getX(b)) * 0.5f
        val midY = (event.getY(a) + event.getY(b)) * 0.5f
        model.updateLayerShift(
            dx = shiftBaseDx + (midX - shiftBaseMidX) / scale,
            dy = shiftBaseDy + (midY - shiftBaseMidY) / scale,
            rotation = rotation
        )
        invalidate()
    }

    private fun clearShiftPinch() {
        shiftPinchA = -1
        shiftPinchB = -1
    }

    private fun angleOf(event: MotionEvent, a: Int, b: Int): Float = atan2(
        (event.getY(b) - event.getY(a)).toDouble(),
        (event.getX(b) - event.getX(a)).toDouble()
    ).toFloat()

    // ------------------------------------------------------------------ morph

    /**
     * Interactive vector warp. Dragging pulls the ink under the brush with a soft
     * falloff; the model is only touched when the user confirms with Done, so a
     * whole sculpting session is one undoable edit and Cancel changes nothing.
     */
    /**
     * Morph is a normal tool. A stylus drag sculpts the selected layer and is
     * committed as one undoable edit when it lifts; fingers navigate exactly like
     * they do for the ink tools, so a pinch can never smear ink.
     */
    private fun handleMorphTouch(event: MotionEvent, model: EditorModel) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val index = 0
                val isTouch = event.getToolType(index) == MotionEvent.TOOL_TYPE_FINGER
                val palmSized = isPalm(event, index)
                viewport.cancelAnimation()
                clearMorphNavigation()
                suppressUntilUp = false
                activePointerId = event.getPointerId(index)
                panLastX = event.x
                panLastY = event.y

                val doubleTap = isTouch && !palmSized && isDoubleTap(event)
                if (doubleTap) {
                    handleDoubleTap(event.x, event.y)
                    lastTapTime = 0L
                    suppressUntilUp = true
                    invalidate()
                    return
                }
                if (isTouch && !palmSized) {
                    lastTapTime = System.currentTimeMillis()
                    lastTapX = event.x
                    lastTapY = event.y
                }
                if (!isPen(event, index) && (model.stylusOnly || palmSized)) {
                    // Fingers navigate; palms are ignored entirely.
                    if (!palmSized) morphPanning = true
                    return
                }
                if (!model.beginMorph()) return
                beginMorphSession(model)
                morphPointer = event.getPointerId(index)
                morphLastX = event.x
                morphLastY = event.y
                morphAtX = event.x
                morphAtY = event.y
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (suppressUntilUp) return
                val activeIndex = event.findPointerIndex(activePointerId)
                val warpFromPen = activeIndex >= 0 && isPen(event, activeIndex)
                if (event.pointerCount < 2) return
                // Palms and spare fingers arriving while the pen sculpts are ignored.
                if (model.morph != null && warpFromPen) return
                // A second finger navigates: abandon the warp in progress so a
                // pinch never smears ink, exactly like an ink stroke.
                if (model.morph != null) {
                    model.cancelMorph()
                    endMorphSession()
                }
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
                morphPanning = false
                morphPinching = true
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (morphPinching) {
                    updatePinch(event)
                    return
                }
                if (morphPanning) {
                    val index = event.findPointerIndex(activePointerId)
                    if (index < 0) return
                    viewport.panBy(event.getX(index) - panLastX, event.getY(index) - panLastY)
                    panLastX = event.getX(index)
                    panLastY = event.getY(index)
                    return
                }
                val session = morphSession ?: return
                if (morphPointer < 0) return
                val index = event.findPointerIndex(morphPointer)
                if (index < 0) return
                val x = event.getX(index)
                val y = event.getY(index)
                morphAtX = x
                morphAtY = y
                val dx = (x - morphLastX) / scale
                val dy = (y - morphLastY) / scale
                if (abs(dx) > 0.001f || abs(dy) > 0.001f) {
                    warpAlong(model, session, morphLastX, morphLastY, x, y, dx, dy)
                    morphLastX = x
                    morphLastY = y
                    invalidate()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (morphPinching) {
                    val lifted = event.getPointerId(event.actionIndex)
                    if (lifted == pinchPointerA || lifted == pinchPointerB) {
                        clearMorphPinch()
                        // Do not resume sculpting with the leftover finger; lift
                        // and put the stylus down again, like the ink tools.
                        morphPointer = -1
                    }
                    return
                }
                if (event.getPointerId(event.actionIndex) == morphPointer) morphPointer = -1
            }
            MotionEvent.ACTION_UP -> {
                if (suppressUntilUp) {
                    suppressUntilUp = false
                    morphPointer = -1
                    return
                }
                val wasWarping = model.morph != null
                clearMorphNavigation()
                morphPointer = -1
                if (wasWarping) commitMorph() else endMorphSession()
            }
            MotionEvent.ACTION_CANCEL -> {
                clearMorphNavigation()
                morphPointer = -1
                cancelMorph()
            }
        }
    }

    private fun clearMorphPinch() {
        pinchPointerA = -1
        pinchPointerB = -1
        morphPinching = false
    }

    private fun clearMorphNavigation() {
        clearMorphPinch()
        morphPanning = false
    }

    private fun beginMorphSession(model: EditorModel) {
        val state = model.morph ?: return
        if (morphSession?.layerId == state.layerId) return
        endMorphSession()
        val frame = model.project.frameAt(state.frameIndex)
        val layer = frame.layers.firstOrNull { it.id == state.layerId } ?: return
        morphSession = MorphSession(
            frameIndex = state.frameIndex,
            layerId = layer.id,
            source = layer.strokes.toList(),
            points = Array(layer.strokes.size) { layer.strokes[it].points.copyOf() },
            changed = BooleanArray(layer.strokes.size),
            originalBounds = Array(layer.strokes.size) {
                boundsOf(layer.strokes[it].points, layer.strokes[it].width)
            }
        )
    }

    private fun syncMorphSession(model: EditorModel) {
        if (model.morph == null && morphSession != null) endMorphSession()
    }

    private fun endMorphSession() {
        morphSession?.recycle()
        morphSession = null
        morphPointer = -1
    }

    private fun warpAlong(
        model: EditorModel,
        session: MorphSession,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        dx: Float,
        dy: Float
    ) {
        val radius = model.morph?.radius ?: return
        val travel = hypot((toX - fromX) / scale, (toY - fromY) / scale)
        val steps = (travel / (radius * 0.3f)).toInt().coerceIn(1, 24)
        val stepDx = dx / steps
        val stepDy = dy / steps
        val stepLength = hypot(stepDx, stepDy)
        val movedStrokes = HashSet<Int>()
        for (step in 1..steps) {
            val f = step.toFloat() / steps
            val cx = toProjectX(fromX + (toX - fromX) * f)
            val cy = toProjectY(fromY + (toY - fromY) * f)
            for (i in session.points.indices) {
                if (Warp.apply(session.points[i], cx, cy, stepDx, stepDy, radius)) {
                    session.changed[i] = true
                    movedStrokes.add(i)
                }
            }
            addMorphDirty(model, session, cx, cy, radius + stepLength)
        }
        // A moved point stretches the segments that connect it to unmoved
        // neighbours, which can reach far outside the brush. Re-render those whole
        // strokes so the patch never leaves a stale piece of the old shape behind.
        for (i in movedStrokes) {
            addMorphDirty(model, session, session.originalBounds[i])
            addMorphDirty(model, session, boundsOf(session.points[i], session.source[i].width))
        }
        session.revision++
    }

    private fun addMorphDirty(
        model: EditorModel,
        session: MorphSession,
        cx: Float,
        cy: Float,
        radius: Float
    ) {
        val project = model.project
        val left = (cx - radius).coerceIn(0f, project.width.toFloat())
        val top = (cy - radius).coerceIn(0f, project.height.toFloat())
        val right = (cx + radius).coerceIn(0f, project.width.toFloat())
        val bottom = (cy + radius).coerceIn(0f, project.height.toFloat())
        if (right <= left || bottom <= top) return
        val rect = RectF(left, top, right, bottom)
        val dirty = session.dirty
        if (dirty == null) {
            session.dirty = rect
        } else {
            dirty.union(rect)
        }
    }

    private fun addMorphDirty(model: EditorModel, session: MorphSession, rect: RectF) {
        val project = model.project
        val clamped = RectF(
            rect.left.coerceIn(0f, project.width.toFloat()),
            rect.top.coerceIn(0f, project.height.toFloat()),
            rect.right.coerceIn(0f, project.width.toFloat()),
            rect.bottom.coerceIn(0f, project.height.toFloat())
        )
        if (clamped.right <= clamped.left || clamped.bottom <= clamped.top) return
        val dirty = session.dirty
        if (dirty == null) {
            session.dirty = clamped
        } else {
            dirty.union(clamped)
        }
    }

    private fun boundsOf(points: FloatArray, width: Float): RectF {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var i = 0
        while (i < points.size) {
            val x = points[i]
            val y = points[i + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            i += 3
        }
        if (minX == Float.MAX_VALUE) return RectF()
        val pad = width + 2f
        return RectF(minX - pad, minY - pad, maxX + pad, maxY + pad)
    }

    /** Commits the sculpted points to the model. */
    fun commitMorph() {
        val model = editor ?: return
        val session = morphSession
        val dirty = session?.dirty
        if (session != null && dirty != null) {
            val strokes = session.source.mapIndexed { i, stroke ->
                if (session.changed[i]) {
                    Stroke(stroke.tool, stroke.color, stroke.width, stroke.opacity, session.points[i])
                } else {
                    stroke
                }
            }
            model.commitMorph(strokes)
        } else {
            model.cancelMorph()
        }
        endMorphSession()
        invalidate()
    }

    fun cancelMorph() {
        editor?.cancelMorph()
        endMorphSession()
        invalidate()
    }

    /**
     * Draws the morphing layer: a faint ghost of the original ink, the untouched
     * part of the cached layer, and a locally re-rendered patch for the warped
     * area. Only nearby strokes are re-rasterised while dragging.
     */
    private fun drawMorphPreview(
        canvas: Canvas,
        model: EditorModel,
        session: MorphSession,
        base: Bitmap,
        layerOpacity: Float
    ) {
        val dirty = session.dirty ?: return
        layerPaint.alpha = (layerOpacity * 0.16f * 255f).toInt().coerceIn(0, 255)
        canvas.drawBitmap(base, null, bitmapRect, layerPaint)

        val project = model.project
        val save = canvas.save()
        canvas.clipRect(0f, 0f, project.width.toFloat(), project.height.toFloat())
        canvas.clipOutRect(dirty)
        layerPaint.alpha = (layerOpacity * 255f).toInt().coerceIn(0, 255)
        canvas.drawBitmap(base, null, bitmapRect, layerPaint)
        canvas.restoreToCount(save)

        val patch = morphPatch(model, session) ?: return
        layerPaint.alpha = (layerOpacity * 255f).toInt().coerceIn(0, 255)
        patchRect.set(
            session.patchLeft.toFloat(),
            session.patchTop.toFloat(),
            (session.patchLeft + patch.width).toFloat(),
            (session.patchTop + patch.height).toFloat()
        )
        canvas.drawBitmap(patch, null, patchRect, layerPaint)
        layerPaint.alpha = 255
    }

    private fun morphPatch(model: EditorModel, session: MorphSession): Bitmap? {
        val dirty = session.dirty ?: return null
        val project = model.project
        val left = floor(dirty.left).toInt().coerceIn(0, project.width - 1)
        val top = floor(dirty.top).toInt().coerceIn(0, project.height - 1)
        val right = ceil(dirty.right).toInt().coerceIn(left + 1, project.width)
        val bottom = ceil(dirty.bottom).toInt().coerceIn(top + 1, project.height)
        val width = right - left
        val height = bottom - top
        var patch = session.patch
        val resized = patch == null || patch.width != width || patch.height != height
        if (resized) {
            patch?.recycle()
            patch = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            session.patch = patch
        }
        val stale = resized || session.patchRevision != session.revision ||
            session.patchLeft != left || session.patchTop != top
        if (!stale) return patch
        patch.eraseColor(Color.TRANSPARENT)
        val patchCanvas = Canvas(patch)
        patchCanvas.translate(-left.toFloat(), -top.toFloat())
        for (i in session.points.indices) {
            if (!strokeTouchesRect(session.points[i], session.source[i].width, left, top, right, bottom)) {
                continue
            }
            val stroke = session.source[i]
            Ink.renderStroke(
                patchCanvas,
                Stroke(stroke.tool, stroke.color, stroke.width, stroke.opacity, session.points[i])
            )
        }
        session.patchRevision = session.revision
        session.patchLeft = left
        session.patchTop = top
        return patch
    }

    private fun strokeTouchesRect(
        points: FloatArray,
        width: Float,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Boolean {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var i = 0
        while (i < points.size) {
            val x = points[i]
            val y = points[i + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            i += 3
        }
        if (minX == Float.MAX_VALUE) return false
        val pad = width + 2f
        return maxX + pad >= left && minX - pad <= right &&
            maxY + pad >= top && minY - pad <= bottom
    }

    private fun drawMorphRing(canvas: Canvas, model: EditorModel) {
        val x = if (morphPointer >= 0) morphAtX else hoverX
        val y = if (morphPointer >= 0) morphAtY else hoverY
        val radius = (model.morph?.radius ?: model.size) * scale
        if (radius < dp(2f)) return
        cursorPaint.style = Paint.Style.STROKE
        cursorPaint.strokeWidth = dp(1.3f)
        cursorPaint.color = Color.argb(210, 10, 132, 255)
        canvas.drawCircle(x, y, radius, cursorPaint)
        cursorPaint.strokeWidth = dp(0.8f)
        cursorPaint.color = Color.argb(90, 0, 0, 0)
        canvas.drawCircle(x, y, radius + dp(1.1f), cursorPaint)
        cursorFill.color = Color.argb(210, 10, 132, 255)
        canvas.drawCircle(x, y, dp(2.2f), cursorFill)
    }

    private fun handlePlacementTouch(event: MotionEvent, model: EditorModel) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                placeMode = PlaceMode.ONE
                placePointer = event.getPointerId(0)
                placeLastX = event.x
                placeLastY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    beginPlacementPinch(event, model)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (placeMode) {
                    PlaceMode.ONE -> {
                        val index = event.findPointerIndex(placePointer)
                        if (index >= 0) {
                            val dx = event.getX(index) - placeLastX
                            val dy = event.getY(index) - placeLastY
                            placeLastX = event.getX(index)
                            placeLastY = event.getY(index)
                            val placement = model.placement
                            if (placement != null) {
                                model.updatePlacement(
                                    centerX = placement.centerX + dx / scale,
                                    centerY = placement.centerY + dy / scale,
                                    width = placement.width,
                                    height = placement.height,
                                    rotation = placement.rotation
                                )
                                invalidate()
                            }
                        }
                    }
                    PlaceMode.TWO -> updatePlacementPinch(event, model)
                    PlaceMode.NONE -> Unit
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val lifted = event.getPointerId(event.actionIndex)
                if (placeMode == PlaceMode.TWO) {
                    if (event.pointerCount - 1 < 2) {
                        val remaining = (0 until event.pointerCount)
                            .firstOrNull { event.getPointerId(it) != lifted }
                        if (remaining != null) {
                            placeMode = PlaceMode.ONE
                            placePointer = event.getPointerId(remaining)
                            placeLastX = event.getX(remaining)
                            placeLastY = event.getY(remaining)
                        } else {
                            placeMode = PlaceMode.NONE
                        }
                    }
                } else if (placeMode == PlaceMode.ONE && lifted == placePointer) {
                    placeMode = PlaceMode.NONE
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> placeMode = PlaceMode.NONE
        }
    }

    private fun beginPlacementPinch(event: MotionEvent, model: EditorModel) {
        val placement = model.placement ?: return
        val a = 0
        val b = 1
        placePointerA = event.getPointerId(a)
        placePointerB = event.getPointerId(b)
        placeMode = PlaceMode.TWO
        placeStartSpan = spanOf(event, a, b).coerceAtLeast(1f)
        placeStartAngle = atan2(
            (event.getY(b) - event.getY(a)).toDouble(),
            (event.getX(b) - event.getX(a)).toDouble()
        ).toFloat()
        placeStartMidX = (event.getX(a) + event.getX(b)) * 0.5f
        placeStartMidY = (event.getY(a) + event.getY(b)) * 0.5f
        placeStartCenterX = placement.centerX
        placeStartCenterY = placement.centerY
        placeStartWidth = placement.width
        placeStartHeight = placement.height
        placeStartRotation = placement.rotation
        val midWorldX = toProjectX(placeStartMidX)
        val midWorldY = toProjectY(placeStartMidY)
        val rad = (-placement.rotation * Math.PI / 180.0).toFloat()
        val dx = midWorldX - placement.centerX
        val dy = midWorldY - placement.centerY
        placeAnchorLocalX = dx * cos(rad) - dy * sin(rad)
        placeAnchorLocalY = dx * sin(rad) + dy * cos(rad)
    }

    private fun updatePlacementPinch(event: MotionEvent, model: EditorModel) {
        val a = event.findPointerIndex(placePointerA)
        val b = event.findPointerIndex(placePointerB)
        if (a < 0 || b < 0) return
        val span = spanOf(event, a, b).coerceAtLeast(1f)
        val angle = atan2(
            (event.getY(b) - event.getY(a)).toDouble(),
            (event.getX(b) - event.getX(a)).toDouble()
        ).toFloat()
        val minFactor = 16f / placeStartWidth.coerceAtLeast(1f)
        val maxFactor = (editor?.project?.width ?: 1080) * 4f / placeStartWidth.coerceAtLeast(1f)
        val factor = (span / placeStartSpan).coerceIn(minFactor, maxFactor)
        var rotation = placeStartRotation + Math.toDegrees((angle - placeStartAngle).toDouble()).toFloat()
        rotation = normalizeAngle(rotation)
        val snapped = (rotation / 90f).roundToInt() * 90f
        if (abs(rotation - snapped) < 4f) rotation = snapped
        val midX = (event.getX(a) + event.getX(b)) * 0.5f
        val midY = (event.getY(a) + event.getY(b)) * 0.5f
        val midWorldX = toProjectX(midX)
        val midWorldY = toProjectY(midY)
        val rad = (rotation * Math.PI / 180.0).toFloat()
        val anchorX = placeAnchorLocalX * factor
        val anchorY = placeAnchorLocalY * factor
        val offsetX = anchorX * cos(rad) - anchorY * sin(rad)
        val offsetY = anchorX * sin(rad) + anchorY * cos(rad)
        model.updatePlacement(
            centerX = midWorldX - offsetX,
            centerY = midWorldY - offsetY,
            width = placeStartWidth * factor,
            height = placeStartHeight * factor,
            rotation = rotation
        )
        invalidate()
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle % 360f
        if (a > 180f) a -= 360f
        if (a <= -180f) a += 360f
        return a
    }

    private fun inkCanvas(): Canvas? = overlayCanvas

    /** Blends the buffered live stroke over the layer bitmap being edited. */
    private fun drawLiveStroke(canvas: Canvas, layerOpacity: Float) {
        val overlay = overlayBitmap ?: return
        // The overlay will end up inside the layer, so it carries the layer's own alpha.
        layerPaint.alpha = (strokeOpacity * layerOpacity * 255f).toInt().coerceIn(0, 255)
        canvas.drawBitmap(overlay, null, bitmapRect, layerPaint)
        layerPaint.alpha = 255
    }

    /**
     * Draws the layer being erased with the buffered mask cut out of it.
     *
     * The cut has to happen in an isolated layer: the mask is white-ish coverage, so
     * `DST_OUT` would otherwise punch through the paper and the layers underneath.
     */
    private fun drawErasePreview(canvas: Canvas, layerBitmap: Bitmap, layerOpacity: Float) {
        val overlay = overlayBitmap ?: return
        val alpha = (layerOpacity * 255f).toInt().coerceIn(0, 255)
        val left = strokeRect.left.toFloat()
        val top = strokeRect.top.toFloat()
        val right = strokeRect.right.toFloat()
        val bottom = strokeRect.bottom.toFloat()

        // The layer outside the live eraser region is unchanged: draw it exactly
        // once, so erasing a lower layer cannot hide the rest of that layer.
        val outside = canvas.save()
        canvas.clipRect(0f, 0f, bitmapRect.width(), bitmapRect.height())
        canvas.clipOutRect(left, top, right, bottom)
        layerPaint.alpha = alpha
        canvas.drawBitmap(layerBitmap, null, bitmapRect, layerPaint)
        canvas.restoreToCount(outside)

        // Only the eraser's bounding box needs the isolated DST_OUT cut-out.
        val save = canvas.save()
        canvas.clipRect(left, top, right, bottom)
        canvas.saveLayer(left, top, right, bottom, null)
        layerPaint.alpha = alpha
        canvas.drawBitmap(layerBitmap, null, bitmapRect, layerPaint)
        layerPaint.alpha = 255
        overlayCompositePaint.alpha = 255
        overlayCompositePaint.xfermode =
            android.graphics.PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        canvas.drawBitmap(overlay, null, bitmapRect, overlayCompositePaint)
        overlayCompositePaint.xfermode = null
        canvas.restoreToCount(save)
    }

    private fun resetStroke() {
        editor?.renderer?.unpinLayer(activeLayerId)
        inker = null
        activeBitmap = null
        overlayBitmap = null
        overlayCanvas = null
        activeLayerIndex = -1
        activeLayerId = -1L
        strokeFrameIndex = -1
    }

    // ------------------------------------------------------------------ misc

    private fun persistViewport(model: EditorModel?) {
        if (model == null) return
        if (viewport.projectWidth <= 1 && viewport.projectHeight <= 1) return
        model.storeViewportState(CanvasViewportCodec.encode(viewport.snapshot()))
    }

    private fun toProjectX(x: Float): Float = (x - offsetX) / scale
    private fun toProjectY(y: Float): Float = (y - offsetY) / scale

    private fun spanOf(event: MotionEvent, a: Int, b: Int): Float =
        hypot(event.getX(a) - event.getX(b), event.getY(a) - event.getY(b))

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

    override fun onWindowVisibilityChanged(visibility: Int) {
        if (visibility != VISIBLE) persistViewport(editor)
        super.onWindowVisibilityChanged(visibility)
    }

    override fun onDetachedFromWindow() {
        // Never leave a layer pinned (and its stroke half-applied) if the view is
        // detached before the pen lifts.
        cancelActiveStroke()
        persistViewport(editor)
        viewport.cancelAnimation()
        morphSession?.recycle()
        morphSession = null
        super.onDetachedFromWindow()
    }
}

/**
 * Working copy of the active layer while the morph tool sculpts it. Point arrays
 * are mutated in place and a small patch bitmap is re-rendered under the brush,
 * so the full layer bitmap never has to be rebuilt for every motion event.
 */
private class MorphSession(
    val frameIndex: Int,
    val layerId: Long,
    val source: List<Stroke>,
    val points: Array<FloatArray>,
    val changed: BooleanArray,
    val originalBounds: Array<RectF>
) {
    var dirty: RectF? = null
    var revision = 0
    var patch: Bitmap? = null
    var patchRevision = -1
    var patchLeft = 0
    var patchTop = 0

    fun recycle() {
        patch?.recycle()
        patch = null
    }
}
