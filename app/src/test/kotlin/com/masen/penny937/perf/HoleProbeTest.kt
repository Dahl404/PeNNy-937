package com.masen.penny937.perf

import android.graphics.Bitmap
import android.graphics.Canvas
import com.masen.penny937.model.Stroke
import com.masen.penny937.model.Tool
import com.masen.penny937.paint.Ink
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Scratch probe: finds fully enclosed transparent regions ("holes") in a stroke. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], manifest = Config.NONE)
class HoleProbeTest {

    private val size = 512

    private fun render(stroke: Stroke): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        Ink.renderStroke(canvas, stroke)
        return bmp
    }

    private class Hole(val area: Int, val cx: Float, val cy: Float, val w: Int, val h: Int)

    private fun holes(bmp: Bitmap): List<Hole> {
        val px = IntArray(size * size)
        bmp.getPixels(px, 0, size, 0, 0, size, size)
        val inked = BooleanArray(size * size) { ((px[it] ushr 24) and 0xFF) > 8 }
        val seen = BooleanArray(size * size)
        // Flood fill background from the border.
        val stack = ArrayDeque<Int>()
        for (x in 0 until size) {
            for (y in intArrayOf(0, size - 1)) {
                val i = y * size + x
                if (!inked[i] && !seen[i]) { seen[i] = true; stack.addLast(i) }
            }
        }
        for (y in 0 until size) {
            for (x in intArrayOf(0, size - 1)) {
                val i = y * size + x
                if (!inked[i] && !seen[i]) { seen[i] = true; stack.addLast(i) }
            }
        }
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            val x = i % size
            val y = i / size
            if (x > 0) { val j = i - 1; if (!inked[j] && !seen[j]) { seen[j] = true; stack.addLast(j) } }
            if (x < size - 1) { val j = i + 1; if (!inked[j] && !seen[j]) { seen[j] = true; stack.addLast(j) } }
            if (y > 0) { val j = i - size; if (!inked[j] && !seen[j]) { seen[j] = true; stack.addLast(j) } }
            if (y < size - 1) { val j = i + size; if (!inked[j] && !seen[j]) { seen[j] = true; stack.addLast(j) } }
        }
        val out = ArrayList<Hole>()
        for (y in 0 until size) {
            for (x in 0 until size) {
                val i = y * size + x
                if (inked[i] || seen[i]) continue
                // New enclosed blob.
                var area = 0
                var minX = x; var maxX = x; var minY = y; var maxY = y
                var sumX = 0L; var sumY = 0L
                val blob = ArrayDeque<Int>()
                blob.addLast(i); seen[i] = true
                while (blob.isNotEmpty()) {
                    val k = blob.removeLast()
                    val kx = k % size
                    val ky = k / size
                    area++
                    sumX += kx; sumY += ky
                    if (kx < minX) minX = kx
                    if (kx > maxX) maxX = kx
                    if (ky < minY) minY = ky
                    if (ky > maxY) maxY = ky
                    if (kx > 0) { val m = k - 1; if (!inked[m] && !seen[m]) { seen[m] = true; blob.addLast(m) } }
                    if (kx < size - 1) { val m = k + 1; if (!inked[m] && !seen[m]) { seen[m] = true; blob.addLast(m) } }
                    if (ky > 0) { val m = k - size; if (!inked[m] && !seen[m]) { seen[m] = true; blob.addLast(m) } }
                    if (ky < size - 1) { val m = k + size; if (!inked[m] && !seen[m]) { seen[m] = true; blob.addLast(m) } }
                }
                out.add(Hole(area, sumX.toFloat() / area, sumY.toFloat() / area, maxX - minX + 1, maxY - minY + 1))
            }
        }
        return out.sortedByDescending { it.area }
    }

    private fun report(name: String, stroke: Stroke) {
        val bmp = render(stroke)
        val hs = holes(bmp)
        println("HOLES $name: count=${hs.size}")
        hs.take(6).forEachIndexed { i, h ->
            println(
                String.format(
                    "  #%d area=%d at (%.1f, %.1f) box=%dx%d",
                    i, h.area, h.cx, h.cy, h.w, h.h
                )
            )
        }
        bmp.recycle()
    }

    /** Fast scribble: big reverse turns, like colouring in. */
    private fun scribble(width: Float, count: Int, amp: Float, step: Float, seed: Int): Stroke {
        val r = Random(seed)
        val pts = FloatArray(count * 3)
        var x = 100f
        var y = 256f
        var a = 0f
        for (i in 0 until count) {
            // Oscillating heading produces repeated hairpins.
            a = sin(i * 0.55f) * amp + (r.nextFloat() - 0.5f) * 0.15f
            x += cos(a) * step
            y += sin(a) * step
            pts[i * 3] = x
            pts[i * 3 + 1] = y
            pts[i * 3 + 2] = 1f
        }
        return Stroke(Tool.PEN, 0xFF303030.toInt(), width, 1f, pts)
    }

    /** Back and forth strokes, the classic colouring motion. */
    private fun lawnmower(width: Float, rows: Int, step: Float): Stroke {
        val pts = ArrayList<Float>()
        var y = 80f
        var dir = 1f
        while (y < 430f) {
            var x = if (dir > 0) 80f else 430f
            while (x in 80f..430f) {
                pts.add(x); pts.add(y); pts.add(1f)
                x += step * dir
            }
            pts.add(if (dir > 0) 430f else 80f); pts.add(y); pts.add(1f)
            y += 24f
            dir = -dir
        }
        return Stroke(Tool.PEN, 0xFF303030.toInt(), width, 1f, pts.toFloatArray())
    }

    @Test
    fun probe() {
        report("scribble-w8", scribble(8f, 260, 2.4f, 3f, 1))
        report("scribble-w30", scribble(30f, 260, 2.4f, 3f, 2))
        report("scribble-w60", scribble(60f, 200, 2.4f, 6f, 3))
        report("lawnmower-w60", lawnmower(60f, 10, 12f))
        report("lawnmower-w20", lawnmower(20f, 10, 12f))
    }
}
