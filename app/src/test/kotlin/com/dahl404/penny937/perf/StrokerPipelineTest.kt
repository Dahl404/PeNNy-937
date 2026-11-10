package com.dahl404.penny937.perf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import com.dahl404.penny937.paint.Ink
import com.dahl404.penny937.paint.StrokeInker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Exercises the path a real stroke takes: samples arrive one motion event at a time,
 * ink accumulates in the scratch overlay, and the overlay is composited into the layer
 * once on lift. The result must match rendering the committed stroke directly, because
 * the layer bitmap cache stops re-rendering after a commit.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], manifest = Config.NONE)
class StrokerPipelineTest {

    private val size = 300

    private fun samples(count: Int, seed: Int = 3): FloatArray {
        val r = Random(seed)
        val pts = FloatArray(count * 3)
        var x = 60f
        var y = 150f
        var a = 0f
        for (i in 0 until count) {
            a += (r.nextFloat() - 0.5f) * 0.22f
            x += cos(a) * 3f
            y += sin(a) * 3f
            pts[i * 3] = x
            pts[i * 3 + 1] = y
            pts[i * 3 + 2] = (0.4f + 0.5f * (i.toFloat() / count)).coerceIn(0f, 1f)
        }
        return pts
    }

    /** Feeds samples in small batches, exactly as separate motion events would. */
    private fun inkLive(
        tool: Tool,
        width: Float,
        opacity: Float,
        points: FloatArray,
        perEvent: Int = 3
    ): Triple<Bitmap, Bitmap, Stroke> {
        val overlay = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val overlayCanvas = Canvas(overlay)
        val base = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        if (tool == Tool.ERASER) base.eraseColor(Color.RED)
        val inker = StrokeInker(tool, Color.BLACK, width, opacity, pressureEnabled = true)
        var i = 0
        val count = points.size / 3
        while (i < count) {
            var k = 0
            while (k < perEvent && i < count) {
                inker.addRaw(points[i * 3], points[i * 3 + 1], points[i * 3 + 2])
                i++
                k++
            }
            inker.drawNew(overlayCanvas)
        }
        inker.finish(overlayCanvas)

        val bounds = RectF()
        inker.bounds(bounds)
        val compositePaint = Ink.compositePaintFor(tool, opacity)
        if (tool == Tool.ERASER) {
            compositePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        val rect = android.graphics.Rect()
        bounds.roundOut(rect)
        Canvas(base).drawBitmap(overlay, rect, rect, compositePaint)
        return Triple(base, overlay, inker.snapshot())
    }

    private fun directRender(stroke: Stroke): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        if (stroke.tool == Tool.ERASER) bitmap.eraseColor(Color.RED)
        Ink.renderStroke(Canvas(bitmap), stroke)
        return bitmap
    }

    private fun diff(a: Bitmap, b: Bitmap): Pair<Double, Int> {
        val pa = IntArray(size * size)
        val pb = IntArray(size * size)
        a.getPixels(pa, 0, size, 0, 0, size, size)
        b.getPixels(pb, 0, size, 0, 0, size, size)
        var sum = 0L
        var ink = 0
        for (i in pa.indices) {
            val aa = (pa[i] ushr 24) and 0xFF
            val ab = (pb[i] ushr 24) and 0xFF
            sum += abs(aa - ab)
            if (aa > 8 || ab > 8) ink++
        }
        return (if (ink == 0) 0.0 else sum.toDouble() / ink) to ink
    }

    private fun check(tool: Tool, width: Float, opacity: Float, maxDiff: Double) {
        val points = samples(80)
        val (live, _, stroke) = inkLive(tool, width, opacity, points)
        val direct = directRender(stroke)
        val (mean, ink) = diff(live, direct)
        println(String.format("PIPELINE %-8s w=%.0f live-vs-committed mean alpha diff %.2f over %d px", tool, width, mean, ink))
        assertTrue("$tool stroke produced no ink", ink > 400)
        assertTrue("$tool live stroke differs from committed render: $mean", mean <= maxDiff)
    }

    @Test
    fun penLiveMatchesCommitted() = check(Tool.PEN, 10f, 1f, 6.0)

    @Test
    fun thinPenLiveMatchesCommitted() = check(Tool.PEN, 2f, 1f, 12.0)

    @Test
    fun markerLiveMatchesCommitted() = check(Tool.MARKER, 36f, 0.45f, 6.0)

    @Test
    fun pencilLiveMatchesCommitted() = check(Tool.PENCIL, 8f, 1f, 20.0)

    @Test
    fun eraserLiveMatchesCommitted() = check(Tool.ERASER, 24f, 1f, 8.0)

    @Test
    fun batchSizeDoesNotChangeTheStroke() {
        val points = samples(80)
        val (small, _, _) = inkLive(Tool.PEN, 12f, 1f, points, perEvent = 1)
        val (large, _, _) = inkLive(Tool.PEN, 12f, 1f, points, perEvent = 40)
        val (mean, ink) = diff(small, large)
        println(String.format("PIPELINE event-batching mean alpha diff %.2f over %d px", mean, ink))
        assertTrue("ink present", ink > 400)
        assertTrue("batched live strokes differ: $mean", mean <= 6.0)
    }

    @Test
    fun committedStrokeIsNotBlendedTwice() {
        // A translucent marker drawn live then committed must keep its requested
        // opacity: two 50% passes would show up as a darker stroke.
        val (live, _, _) = inkLive(Tool.MARKER, 36f, 0.5f, samples(60))
        val pixels = IntArray(size * size)
        live.getPixels(pixels, 0, size, 0, 0, size, size)
        var maxAlpha = 0
        for (p in pixels) {
            val a = (p ushr 24) and 0xFF
            if (a > maxAlpha) maxAlpha = a
        }
        assertEquals("marker alpha should be 0.5*255", 128.0, maxAlpha.toDouble(), 4.0)
    }
}
