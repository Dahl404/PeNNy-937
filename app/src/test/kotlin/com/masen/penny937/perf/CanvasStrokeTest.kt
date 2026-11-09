package com.masen.penny937.perf

import android.graphics.Color
import android.view.MotionEvent
import com.masen.penny937.data.Prefs
import com.masen.penny937.data.ProjectStore
import com.masen.penny937.editor.EditorModel
import com.masen.penny937.model.Tool
import com.masen.penny937.ui.canvas.DrawingCanvasView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        val project = com.masen.penny937.model.ProjectFactory.create(
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
        val direct = com.masen.penny937.paint.FrameRasterizer.rasterize(
            model.project,
            com.masen.penny937.model.Frame(id = 1, layers = listOf(layer)),
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
            com.masen.penny937.model.Stroke(
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
        val penOnly = com.masen.penny937.paint.FrameRasterizer.rasterize(
            model.project,
            com.masen.penny937.model.Frame(id = 1, layers = listOf(layer.copy(strokes = layer.strokes.take(1)))),
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
