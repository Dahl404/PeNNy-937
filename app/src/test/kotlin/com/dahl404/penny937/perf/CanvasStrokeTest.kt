package com.dahl404.penny937.perf

import android.graphics.Color
import android.view.MotionEvent
import com.dahl404.penny937.data.Prefs
import com.dahl404.penny937.data.ProjectStore
import com.dahl404.penny937.editor.EditorModel
import com.dahl404.penny937.model.Tool
import com.dahl404.penny937.ui.canvas.DrawingCanvasView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.math.cos
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * End-to-end check of the drawing surface: stylus events go in, a committed stroke
 * comes out, and the cached layer bitmap matches a fresh render of that stroke.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], manifest = Config.NONE)
class CanvasStrokeTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var model: EditorModel
    private lateinit var view: DrawingCanvasView

    private fun setUpView(tool: Tool) {
        val context = RuntimeEnvironment.getApplication()
        val store = ProjectStore(context)
        val prefs = Prefs(context)
        val project = com.dahl404.penny937.model.ProjectFactory.create(
            "stroke test", 600, 600, 12, 0xFFFBF9F4.toInt()
        )
        model = EditorModel(context, store, prefs, project, scope)
        model.chooseTool(tool)
        view = DrawingCanvasView(context)
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(900, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(900, android.view.View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 900, 900)
        view.bind(model)
        view.fitToPaper()
    }

    @After
    fun tearDown() {
        model.discard()
    }

    /**
     * Builds a pointer event with an explicit tool type. `setToolType` is hidden from
     * the SDK, but the public multi-pointer `obtain` overload takes PointerProperties.
     */
    private fun event(
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
        pressure: Float = 1f,
        toolType: Int = MotionEvent.TOOL_TYPE_STYLUS
    ): MotionEvent {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            this.toolType = toolType
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            this.pressure = pressure
            size = 0.2f
        }
        return MotionEvent.obtain(
            0L, eventTime, action, 1, arrayOf(properties), arrayOf(coords),
            0, 0, 1f, 1f, 0, 0,
            android.view.InputDevice.SOURCE_STYLUS, 0
        )
    }

    private fun send(event: MotionEvent) {
        view.dispatchTouchEvent(event)
    }

    private fun drawStroke(tool: Tool, eraserOverride: Boolean = false) {
        setUpView(tool)
        val down = event(0L, MotionEvent.ACTION_DOWN, 300f, 300f, 0.6f)
        send(down)
        down.recycle()
        for (i in 1..40) {
            val e = event(
                i * 10L, MotionEvent.ACTION_MOVE, 300f + i * 5f, 300f + i * 4f, 0.5f + i * 0.01f
            )
            send(e)
            e.recycle()
        }
        val up = event(500L, MotionEvent.ACTION_UP, 500f, 460f, 0.7f)
        send(up)
        up.recycle()
    }

    @Test
    fun penStrokeIsCommitted() {
        drawStroke(Tool.PEN)
        val strokes = model.frame.layers[model.currentLayer].strokes
        assertEquals("one stroke should be committed", 1, strokes.size)
        assertTrue("stroke should carry samples", strokes[0].pointCount > 10)
    }

    @Test
    fun liveOverlayIsClearedAfterCommit() {
        drawStroke(Tool.MARKER)
        val overlay = model.renderer.inkOverlay(model.project)
        val pixels = IntArray(overlay.width * overlay.height)
        overlay.getPixels(pixels, 0, overlay.width, 0, 0, overlay.width, overlay.height)
        var painted = 0
        for (p in pixels) if ((p ushr 24) and 0xFF != 0) painted++
        println("CANVAS marker overlay pixels after commit: $painted")
        assertEquals("scratch overlay must be cleared after the stroke", 0, painted)
    }

    @Test
    fun layerBitmapMatchesAReRender() {
        drawStroke(Tool.PEN)
        val layer = model.frame.layers[model.currentLayer]
        val cached = model.renderer.editableLayerBitmap(model.project, layer)
        val direct = com.dahl404.penny937.paint.FrameRasterizer.rasterize(
            model.project,
            com.dahl404.penny937.model.Frame(id = 1, layers = listOf(layer)),
            model.project.width,
            model.project.height,
            null
        )
        val a = IntArray(cached.width * cached.height)
        val b = IntArray(direct.width * direct.height)
        cached.getPixels(a, 0, cached.width, 0, 0, cached.width, cached.height)
        direct.getPixels(b, 0, direct.width, 0, 0, direct.width, direct.height)
        var bad = 0
        var maxDelta = 0
        for (i in a.indices) {
            val alphaDelta = kotlin.math.abs(((a[i] ushr 24) and 0xFF) - ((b[i] ushr 24) and 0xFF))
            if (alphaDelta > 8) bad++
            if (alphaDelta > maxDelta) maxDelta = alphaDelta
        }
        println("CANVAS cached-vs-rerender: $bad pixels differ, max alpha delta $maxDelta")
        assertEquals("committed layer bitmap must match a re-render", 0, bad)
        direct.recycle()
    }

    @Test
    fun eraserStrokeRemovesInk() {
        setUpView(Tool.PEN)
        model.addStroke(
            com.dahl404.penny937.model.Stroke(
                Tool.PEN, Color.BLACK, 40f, 1f,
                FloatArray(30 * 3).also { p ->
                    for (i in 0 until 30) {
                        p[i * 3] = 100f + i * 12f
                        p[i * 3 + 1] = 300f
                        p[i * 3 + 2] = 1f
                    }
                }
            ),
            0, 0
        )
        model.chooseTool(Tool.ERASER)
        model.changeSize(60f)
        val layerBefore = model.frame.layers[0]
        assertEquals(1, layerBefore.strokes.size)

        // Erase along the view diagonal; the fitted paper transform is not exposed, so
        // the stroke is made long enough to cross the ink wherever it lands.
        val down = event(0L, MotionEvent.ACTION_DOWN, 60f, 60f)
        send(down)
        down.recycle()
        for (i in 1..40) {
            val e = event(i * 10L, MotionEvent.ACTION_MOVE, 60f + i * 19f, 60f + i * 19f)
            send(e)
            e.recycle()
        }
        val up = event(500L, MotionEvent.ACTION_UP, 820f, 820f)
        send(up)
        up.recycle()

        val layer = model.frame.layers[0]
        assertEquals("pen + eraser strokes", 2, layer.strokes.size)

        fun inked(): Int {
            val bitmap = model.renderer.editableLayerBitmap(model.project, layer)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            var count = 0
            for (p in pixels) if ((p ushr 24) and 0xFF > 100) count++
            return count
        }
        val penOnly = com.dahl404.penny937.paint.FrameRasterizer.rasterize(
            model.project,
            com.dahl404.penny937.model.Frame(id = 1, layers = listOf(layer.copy(strokes = layer.strokes.take(1)))),
            model.project.width, model.project.height, null
        )
        val penPixels = IntArray(penOnly.width * penOnly.height)
        penOnly.getPixels(penPixels, 0, penOnly.width, 0, 0, penOnly.width, penOnly.height)
        var before = 0
        for (p in penPixels) if ((p ushr 24) and 0xFF > 100) before++
        penOnly.recycle()
        val after = inked()
        println("CANVAS eraser: inked pixels before $before, after $after")
        assertTrue("eraser should remove ink", after < before * 0.85)
        assertTrue("eraser must not wipe the whole layer", after > 0)
    }

    @Test
    fun erasingLowerLayerKeepsTheRestOfThatLayerVisible() {
        setUpView(Tool.PEN)
        model.addLayer()
        model.selectLayer(0)
        val points = FloatArray(20 * 3).also { p ->
            for (i in 0 until 20) {
                p[i * 3] = 40f + i * 28f
                p[i * 3 + 1] = 300f
                p[i * 3 + 2] = 1f
            }
        }
        model.addStroke(
            com.dahl404.penny937.model.Stroke(Tool.PEN, Color.BLACK, 180f, 1f, points),
            0,
            0
        )

        fun render(): android.graphics.Bitmap {
            val bitmap = android.graphics.Bitmap.createBitmap(
                view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888
            )
            view.draw(android.graphics.Canvas(bitmap))
            return bitmap
        }

        fun inkPixels(bitmap: android.graphics.Bitmap): Int {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val paper = 0xFFFBF9F4.toInt()
            val pr = (paper shr 16) and 0xFF
            val pg = (paper shr 8) and 0xFF
            val pb = paper and 0xFF
            var count = 0
            for (p in pixels) {
                if ((p ushr 24) and 0xFF != 255) continue
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (kotlin.math.abs(r - pr) + kotlin.math.abs(g - pg) +
                    kotlin.math.abs(b - pb) > 200
                ) {
                    count++
                }
            }
            return count
        }

        val beforeBitmap = render()
        val before = inkPixels(beforeBitmap)
        beforeBitmap.recycle()
        assertTrue("test ink should cover a large area", before > 10_000)

        model.chooseTool(Tool.ERASER)
        model.changeSize(24f)
        val down = event(0L, MotionEvent.ACTION_DOWN, 450f, 450f)
        send(down)
        down.recycle()
        val move = event(20L, MotionEvent.ACTION_MOVE, 470f, 470f)
        send(move)
        move.recycle()

        val duringBitmap = render()
        val during = inkPixels(duringBitmap)
        duringBitmap.recycle()

        val up = event(40L, MotionEvent.ACTION_UP, 470f, 470f)
        send(up)
        up.recycle()

        println("CANVAS lower-layer erase: ink before $before, during $during")
        assertTrue(
            "the live eraser should cut ink out of the active layer",
            during < before
        )
        assertTrue(
            "erasing a lower layer must keep the rest of that layer visible",
            during > before * 0.7
        )
        assertEquals("eraser must commit to the lower layer", 2, model.frame.layers[0].strokes.size)
    }

    @Test
    fun drawingOnALowerLayerSurvivesCacheEviction() {
        setUpView(Tool.PEN)
        model.addLayer()
        model.addLayer()
        model.selectLayer(0)
        // Force the layer cache to consider every extra layer too large. Rendering
        // the stack then has to skip (not evict) the layer with the live stroke.
        model.renderer.layerBudget = 1L

        val down = event(0L, MotionEvent.ACTION_DOWN, 300f, 300f)
        send(down)
        down.recycle()
        val move = event(20L, MotionEvent.ACTION_MOVE, 340f, 340f)
        send(move)
        move.recycle()

        // This is what onDraw does while the pen is down: it renders every layer
        // and would previously recycle the active one on a tight cache budget.
        val frame = android.graphics.Bitmap.createBitmap(
            view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888
        )
        view.draw(android.graphics.Canvas(frame))
        frame.recycle()

        val up = event(40L, MotionEvent.ACTION_UP, 340f, 340f)
        send(up)
        up.recycle()

        assertEquals(
            "the stroke on the lower layer should still commit",
            1, model.frame.layers[0].strokes.size
        )
    }

    /** Two-pointer event used for the layer-shift rotation gesture. */
    private fun twoPointerEvent(
        eventTime: Long,
        action: Int,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float
    ): MotionEvent {
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            },
            MotionEvent.PointerProperties().apply {
                id = 1
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = x0
                y = y0
                pressure = 1f
                size = 0.2f
            },
            MotionEvent.PointerCoords().apply {
                x = x1
                y = y1
                pressure = 1f
                size = 0.2f
            }
        )
        return MotionEvent.obtain(
            0L, eventTime, action, 2, properties, coords,
            0, 0, 1f, 1f, 0, 0,
            android.view.InputDevice.SOURCE_TOUCHSCREEN, 0
        )
    }

    @Test
    fun twoFingerTwistRotatesTheShiftedLayer() {
        drawStroke(Tool.PEN)
        model.beginLayerShift()
        val started = model.layerShift
        assertNotNull("shift mode should start", started)
        val pivotX = started!!.pivotX
        val pivotY = started.pivotY

        val down = event(600L, MotionEvent.ACTION_DOWN, 300f, 400f, 1f, MotionEvent.TOOL_TYPE_FINGER)
        send(down)
        down.recycle()
        val second = twoPointerEvent(
            620L,
            MotionEvent.ACTION_POINTER_DOWN or
                (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            300f, 400f, 300f, 500f
        )
        send(second)
        second.recycle()
        // Fingers are vertical, then horizontal: a 90 degree twist around (300, 450).
        val move = twoPointerEvent(700L, MotionEvent.ACTION_MOVE, 400f, 450f, 200f, 450f)
        send(move)
        move.recycle()

        val shifted = model.layerShift
        assertNotNull(shifted)
        assertEquals(90f, shifted!!.rotation, 0.5f)
        assertEquals("the gesture midpoint did not move", 0f, shifted.dx, 0.001f)
        assertEquals(0f, shifted.dy, 0.001f)

        val before = model.frame.layers[model.currentLayer].strokes[0]
        val px = before.pointX(0)
        val py = before.pointY(0)
        model.commitLayerShift()
        val after = model.frame.layers[model.currentLayer].strokes[0]
        val radians = Math.toRadians(90.0)
        val expectedX = pivotX + (px - pivotX) * cos(radians).toFloat() -
            (py - pivotY) * sin(radians).toFloat()
        val expectedY = pivotY + (px - pivotX) * sin(radians).toFloat() +
            (py - pivotY) * cos(radians).toFloat()
        assertEquals(expectedX, after.pointX(0), 0.01f)
        assertEquals(expectedY, after.pointY(0), 0.01f)
    }

    @Test
    fun morphToolPullsTheInkAndCommitsOneUndoableEdit() {
        drawStroke(Tool.PEN)
        val layer = model.frame.layers[model.currentLayer]
        val before = layer.strokes[0].points.copyOf()
        model.chooseTool(Tool.MORPH)
        model.changeSize(200f)

        val down = event(600L, MotionEvent.ACTION_DOWN, 400f, 400f)
        send(down)
        down.recycle()
        for (i in 1..6) {
            val e = event(600L + i * 16L, MotionEvent.ACTION_MOVE, 400f + i * 10f, 400f)
            send(e)
            e.recycle()
        }
        val up = event(760L, MotionEvent.ACTION_UP, 460f, 400f)
        send(up)
        up.recycle()

        assertEquals(null, model.morph)
        val after = model.frame.layers[model.currentLayer].strokes[0].points
        var changed = 0
        for (i in before.indices) if (before[i] != after[i]) changed++
        assertTrue("the brush should move ink", changed > 0)
        assertTrue("a morph is undoable", model.canUndo)

        model.undo()
        val restored = model.frame.layers[model.currentLayer].strokes[0].points
        for (i in before.indices) assertEquals(before[i], restored[i], 0.0001f)
    }

    @Test
    fun morphCancelLeavesTheLayerUntouched() {
        drawStroke(Tool.PEN)
        val before = model.frame.layers[model.currentLayer].strokes[0].points.copyOf()
        model.chooseTool(Tool.MORPH)
        model.changeSize(200f)
        val down = event(800L, MotionEvent.ACTION_DOWN, 400f, 400f)
        send(down)
        down.recycle()
        val move = event(820L, MotionEvent.ACTION_MOVE, 460f, 430f)
        send(move)
        move.recycle()
        // Lifting the stylus commits; cancelling the gesture discards the sculpt.
        val cancel = event(840L, MotionEvent.ACTION_CANCEL, 460f, 430f)
        send(cancel)
        cancel.recycle()

        assertEquals(null, model.morph)
        val after = model.frame.layers[model.currentLayer].strokes[0].points
        for (i in before.indices) assertEquals(before[i], after[i], 0.0001f)
    }

    @Test
    fun morphToolNavigatesWithTwoFingers() {
        drawStroke(Tool.PEN)
        model.chooseTool(Tool.MORPH)
        model.changeSize(200f)
        val before = model.frame.layers[model.currentLayer].strokes[0].points.copyOf()
        val beforeScale = view.currentScale()

        val down = event(600L, MotionEvent.ACTION_DOWN, 300f, 400f, 1f, MotionEvent.TOOL_TYPE_FINGER)
        send(down)
        down.recycle()
        val second = twoPointerEvent(
            620L,
            MotionEvent.ACTION_POINTER_DOWN or
                (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            300f, 400f, 300f, 500f
        )
        send(second)
        second.recycle()
        val move = twoPointerEvent(700L, MotionEvent.ACTION_MOVE, 220f, 400f, 380f, 400f)
        send(move)
        move.recycle()

        assertTrue("two fingers should zoom", view.currentScale() > beforeScale)
        val after = model.frame.layers[model.currentLayer].strokes[0].points
        for (i in before.indices) {
            assertEquals("navigation must not pull ink", before[i], after[i], 0.0001f)
        }
        assertEquals(null, model.morph)

        val lift = twoPointerEvent(
            760L,
            MotionEvent.ACTION_POINTER_UP or
                (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            220f, 400f, 380f, 400f
        )
        send(lift)
        lift.recycle()
        val up = event(800L, MotionEvent.ACTION_UP, 220f, 400f, 1f, MotionEvent.TOOL_TYPE_FINGER)
        send(up)
        up.recycle()
    }

    @Test
    fun touchDoesNotDrawWithStylusOnly() {
        setUpView(Tool.PEN)
        val finger = MotionEvent.TOOL_TYPE_FINGER
        val down = event(0L, MotionEvent.ACTION_DOWN, 400f, 400f, 1f, finger)
        send(down)
        down.recycle()
        for (i in 1..10) {
            val e = event(i * 10L, MotionEvent.ACTION_MOVE, 400f + i * 6f, 400f + i * 3f, 1f, finger)
            send(e)
            e.recycle()
        }
        val up = event(200L, MotionEvent.ACTION_UP, 460f, 430f, 1f, finger)
        send(up)
        up.recycle()
        assertEquals(
            "finger input pans instead of drawing",
            0, model.frame.layers[model.currentLayer].strokes.size
        )
        assertNotNull(view)
    }
}
