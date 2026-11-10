package com.dahl404.penny937.perf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import com.dahl404.penny937.paint.Ink
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], manifest = Config.NONE)
class InkDumpTest {

    private val size = 400

    private fun smoothStroke(tool: Tool, width: Float, count: Int, opacity: Float = 1f): Stroke {
        val r = Random(7)
        val pts = FloatArray(count * 3)
        var x = 60f
        var y = 200f
        var a = 0f
        for (i in 0 until count) {
            a += (r.nextFloat() - 0.5f) * 0.25f
            x += cos(a) * 2.5f
            y += sin(a) * 2.5f
            pts[i * 3] = x
            pts[i * 3 + 1] = y
            pts[i * 3 + 2] = 0.2f + r.nextFloat() * 0.8f
        }
        return Stroke(tool, 0xFF303030.toInt(), width, opacity, pts)
    }

    private fun dump(name: String, legacy: Bitmap, fresh: Bitmap) {
        val dir = File("build/inkdump").apply { mkdirs() }
        val diff = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val la = IntArray(size * size)
        val lb = IntArray(size * size)
        legacy.getPixels(la, 0, size, 0, 0, size, size)
        fresh.getPixels(lb, 0, size, 0, 0, size, size)
        val out = IntArray(size * size)
        for (i in out.indices) {
            val a = (la[i] ushr 24) and 0xFF
            val b = (lb[i] ushr 24) and 0xFF
            // red = legacy only, blue = new only, dark = both
            out[i] = when {
                a > 16 && b > 16 -> 0xFF808080.toInt()
                a > 16 -> 0xFFFF2020.toInt()
                b > 16 -> 0xFF2040FF.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
        }
        diff.setPixels(out, 0, size, 0, 0, size, size)
        var sumA = 0L
        var sumB = 0L
        var n = 0
        for (i in la.indices) {
            val a = (la[i] ushr 24) and 0xFF
            val b = (lb[i] ushr 24) and 0xFF
            if (a > 4 || b > 4) { sumA += a; sumB += b; n++ }
        }
        println("DENSITY $name: legacy mean alpha ${sumA / n}  new ${sumB / n}  over $n px")
        listOf("legacy" to legacy, "new" to fresh, "diff" to diff).forEach { (tag, bmp) ->
            FileOutputStream(File(dir, "$name-$tag.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun dumpThickPen() {
        val stroke = smoothStroke(Tool.PEN, 48f, 200)
        val (bl, cl) = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888) to null
        val a = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        LegacyInk.renderStroke(Canvas(a), stroke)
        Ink.renderStroke(Canvas(b), stroke)
        bl.recycle()
        dump("thick", a, b)
    }

    @Test
    fun dumpZigzag() {
        val pts = FloatArray(12 * 3)
        for (i in 0 until 12) {
            pts[i * 3] = 40f + i * 28f
            pts[i * 3 + 1] = if (i % 2 == 0) 120f else 260f
            pts[i * 3 + 2] = 1f
        }
        val stroke = Stroke(Tool.PEN, 0xFF303030.toInt(), 14f, 1f, pts)
        val a = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        LegacyInk.renderStroke(Canvas(a), stroke)
        Ink.renderStroke(Canvas(b), stroke)
        dump("zigzag", a, b)
    }

    @Test
    fun dumpPencil() {
        val stroke = smoothStroke(Tool.PENCIL, 8f, 200)
        val a = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        LegacyInk.renderStroke(Canvas(a), stroke)
        Ink.renderStroke(Canvas(b), stroke)
        dump("pencil", a, b)
    }

    @Test
    fun dumpSmoothPen() {
        val stroke = smoothStroke(Tool.PEN, 10f, 200)
        val a = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        LegacyInk.renderStroke(Canvas(a), stroke)
        Ink.renderStroke(Canvas(b), stroke)
        dump("smooth", a, b)
    }
}
