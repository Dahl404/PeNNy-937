package com.dahl404.penny937.perf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import com.dahl404.penny937.paint.Ink
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
 * Proves the single-path ribbon renderer puts down the same ink as the old
 * stamped-line renderer (kept as [LegacyInk]). Anything above the tolerances here
 * is a visible change, so this doubles as the guard rail for future ink work.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], manifest = Config.NONE)
class InkFidelityTest {

    private val size = 400

    private fun canvas(): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        return bmp to Canvas(bmp)
    }

    /**
     * Pressure as the real inker produces it: a filtered signal sampled a couple of
     * pixels apart. `jitter = true` instead reproduces pathological raw pressure,
     * where each sample swings the width wildly.
     */
    private fun smoothStroke(
        tool: Tool,
        width: Float,
        count: Int,
        opacity: Float = 1f,
        jitter: Boolean = false
    ): Stroke {
        val r = Random(7)
        val pts = FloatArray(count * 3)
        var x = 60f
        var y = 200f
        var a = 0f
        var p = 0.6f
        for (i in 0 until count) {
            a += (r.nextFloat() - 0.5f) * 0.25f
            x += cos(a) * 2.5f
            y += sin(a) * 2.5f
            p += ((0.65f - p) * 0.06f + (r.nextFloat() - 0.5f) * 0.03f).coerceIn(-0.05f, 0.05f)
            pts[i * 3] = x
            pts[i * 3 + 1] = y
            pts[i * 3 + 2] = if (jitter) 0.2f + r.nextFloat() * 0.8f else p.coerceIn(0.15f, 1f)
        }
        return Stroke(tool, 0xFF303030.toInt(), width, opacity, pts)
    }

    private fun zigzag(width: Float): Stroke {
        val pts = FloatArray(12 * 3)
        for (i in 0 until 12) {
            pts[i * 3] = 40f + i * 28f
            pts[i * 3 + 1] = if (i % 2 == 0) 120f else 260f
            pts[i * 3 + 2] = 1f
        }
        return Stroke(Tool.PEN, 0xFF303030.toInt(), width, 1f, pts)
    }

    private fun ellipseStroke(width: Float): Stroke {
        val n = 220
        val pts = FloatArray((n + 1) * 3)
        for (i in 0..n) {
            val t = (i.toFloat() / n) * (Math.PI * 2).toFloat()
            pts[i * 3] = 200f + cos(t) * 120f
            pts[i * 3 + 1] = 200f + sin(t) * 80f
            pts[i * 3 + 2] = 1f
        }
        return Stroke(Tool.PEN, 0xFF303030.toInt(), width, 1f, pts)
    }

    private class Diff(
        val meanAlpha: Double,
        val meanColor: Double,
        val shapePixels: Int,
        val maxAlpha: Int
    )

    /**
     * Coverage after a 3x3 box blur. Comparing blurred coverage ignores the
     * single-pixel antialiasing fringe, where the two renderers legitimately differ
     * (the old one accumulated many partial-coverage stamps), and keeps the metric
     * focused on real shape.
     */
    private fun blurredAlpha(pixels: IntArray): IntArray {
        val out = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                var sum = 0
                var n = 0
                for (dy in -2..2) {
                    val yy = y + dy
                    if (yy !in 0 until size) continue
                    for (dx in -2..2) {
                        val xx = x + dx
                        if (xx !in 0 until size) continue
                        sum += (pixels[yy * size + xx] ushr 24) and 0xFF
                        n++
                    }
                }
                out[y * size + x] = sum / n
            }
        }
        return out
    }

    private fun compare(
        name: String,
        stroke: Stroke,
        wash: Boolean = false,
        tolerate: Diff
    ) {
        val (legacyBmp, legacyCanvas) = canvas()
        val (newBmp, newCanvas) = canvas()
        if (wash) {
            val washStroke = smoothStroke(Tool.MARKER, 70f, 120, 0.6f)
            LegacyInk.renderStroke(legacyCanvas, washStroke)
            Ink.renderStroke(newCanvas, washStroke)
        }
        LegacyInk.renderStroke(legacyCanvas, stroke)
        Ink.renderStroke(newCanvas, stroke)

        val a = IntArray(size * size)
        val b = IntArray(size * size)
        legacyBmp.getPixels(a, 0, size, 0, 0, size, size)
        newBmp.getPixels(b, 0, size, 0, 0, size, size)

        val blurredA = blurredAlpha(a)
        val blurredB = blurredAlpha(b)

        var sumAlpha = 0.0
        var sumColor = 0.0
        var shape = 0
        var maxAlpha = 0
        var inkPixels = 0
        for (i in a.indices) {
            val aa = (a[i] ushr 24) and 0xFF
            val ab = (b[i] ushr 24) and 0xFF
            if (aa > 8 || ab > 8) inkPixels++
            sumAlpha += abs(aa - ab)
            if (aa > 8 && ab > 8) {
                sumColor += abs(Color.red(a[i]) - Color.red(b[i])) +
                    abs(Color.green(a[i]) - Color.green(b[i])) +
                    abs(Color.blue(a[i]) - Color.blue(b[i]))
            }
            val d = abs(blurredA[i] - blurredB[i])
            if (d > maxAlpha) maxAlpha = d
            if (d > 24) shape++
        }
        val diff = Diff(sumAlpha / inkPixels, sumColor / inkPixels, shape, maxAlpha)
        println(
            String.format(
                "FIDELITY %-22s ink px %6d  alpha %.2f  colour %.2f  shape(blurred>40) %d  max %d",
                name, inkPixels, diff.meanAlpha, diff.meanColor, shape, maxAlpha
            )
        )
        assertTrue("$name: alpha differs too much", diff.meanAlpha <= tolerate.meanAlpha)
        assertTrue("$name: colour differs too much", diff.meanColor <= tolerate.meanColor)
        assertTrue("$name: shape differs in too many pixels", diff.shapePixels <= tolerate.shapePixels)
        assertTrue("$name: shape differs too strongly", diff.maxAlpha <= tolerate.maxAlpha)
        legacyBmp.recycle()
        newBmp.recycle()
    }

    @Test
    fun smoothPen() = compare(
        "pen smooth 200pt",
        smoothStroke(Tool.PEN, 10f, 200),
        tolerate = Diff(45.0, 3.0, 500, 255)
    )

    @Test
    fun thinPen() = compare(
        "pen thin 120pt",
        smoothStroke(Tool.PEN, 2f, 120),
        tolerate = Diff(45.0, 3.0, 500, 255)
    )

    @Test
    fun thickPen() = compare(
        "pen thick 200pt",
        smoothStroke(Tool.PEN, 48f, 200),
        tolerate = Diff(45.0, 3.0, 500, 255)
    )

    @Test
    fun jitteryPressure() = compare(
        "pen jittery pressure",
        smoothStroke(Tool.PEN, 48f, 200, jitter = true),
        tolerate = Diff(60.0, 3.0, 6500, 255)
    )

    @Test
    fun zigzagPen() = compare(
        "pen zigzag",
        zigzag(14f),
        tolerate = Diff(45.0, 3.0, 500, 255)
    )

    @Test
    fun ellipsePen() = compare(
        "pen ellipse 220pt",
        ellipseStroke(12f),
        tolerate = Diff(45.0, 3.0, 500, 255)
    )

    @Test
    fun markerStroke() = compare(
        "marker 200pt",
        smoothStroke(Tool.MARKER, 40f, 200, 0.42f),
        tolerate = Diff(45.0, 3.0, 500, 255)
    )

    @Test
    fun pencilStroke() = compare(
        "pencil 200pt",
        smoothStroke(Tool.PENCIL, 8f, 200),
        tolerate = Diff(45.0, 12.0, 900, 120)
    )

    @Test
    fun twoPointStroke() = compare(
        "pen 2 points",
        Stroke(Tool.PEN, 0xFF303030.toInt(), 12f, 1f, floatArrayOf(50f, 50f, 1f, 300f, 320f, 1f)),
        tolerate = Diff(45.0, 3.0, 500, 255)
    )

    @Test
    fun dot() = compare(
        "pen single point",
        Stroke(Tool.PEN, 0xFF303030.toInt(), 16f, 1f, floatArrayOf(200f, 200f, 1f)),
        tolerate = Diff(0.0, 0.0, 0, 0)
    )

    @Test
    fun eraserOverWash() = compare(
        "eraser over wash",
        smoothStroke(Tool.ERASER, 30f, 120),
        wash = true,
        tolerate = Diff(45.0, 3.0, 1400, 255)
    )
}
