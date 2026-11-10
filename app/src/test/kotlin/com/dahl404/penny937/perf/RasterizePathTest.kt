package com.dahl404.penny937.perf

import android.graphics.Bitmap
import android.graphics.Color
import com.dahl404.penny937.model.Frame
import com.dahl404.penny937.model.Layer
import com.dahl404.penny937.model.Project
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import com.dahl404.penny937.paint.FrameRasterizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The single-layer fast path draws strokes straight onto the output; everything else
 * composites through a scratch bitmap. Both must produce the same pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], manifest = Config.NONE)
class RasterizePathTest {

    private fun stroke(tool: Tool, color: Int, width: Float, seed: Int): Stroke {
        val pts = FloatArray(40 * 3)
        for (i in 0 until 40) {
            pts[i * 3] = 40f + i * 6f
            pts[i * 3 + 1] = 100f + (if (seed % 2 == 0) 1 else -1) * i * 1.7f
            pts[i * 3 + 2] = 0.8f
        }
        return Stroke(tool, color, width, 1f, pts)
    }

    private fun project(layers: List<Layer>): Project = Project(
        id = 1,
        name = "paths",
        width = 320,
        height = 320,
        fps = 12,
        background = 0xFFFBF9F4.toInt(),
        frames = listOf(Frame(id = 7, layers = layers)),
        createdAt = 1,
        updatedAt = 1
    )

    private fun compare(name: String, project: Project) {
        val frame = project.frames[0]
        FrameRasterizer.forceComposited = false
        val fast = FrameRasterizer.rasterize(project, frame, 320, 320, project.background)
        FrameRasterizer.forceComposited = true
        val composed = FrameRasterizer.rasterize(project, frame, 320, 320, project.background)
        FrameRasterizer.forceComposited = false

        val a = IntArray(320 * 320)
        val b = IntArray(320 * 320)
        fast.getPixels(a, 0, 320, 0, 0, 320, 320)
        composed.getPixels(b, 0, 320, 0, 0, 320, 320)
        var bad = 0
        var maxDiff = 0
        for (i in a.indices) {
            val d = abs(Color.red(a[i]) - Color.red(b[i])) +
                abs(Color.green(a[i]) - Color.green(b[i])) +
                abs(Color.blue(a[i]) - Color.blue(b[i]))
            if (d > 8) bad++
            if (d > maxDiff) maxDiff = d
        }
        println("RASTER $name: differing pixels $bad, max channel delta $maxDiff")
        assertEquals("$name fast path differs from composited path", 0, bad)
        fast.recycle()
        composed.recycle()
    }

    @Test
    fun singleOpaqueLayer() = compare(
        "single layer",
        project(listOf(Layer(id = 1, name = "L", strokes = listOf(stroke(Tool.PEN, 0xFF202020.toInt(), 8f, 0)))))
    )

    @Test
    fun singleTranslucentMarkerLayer() = compare(
        "single marker layer",
        project(
            listOf(
                Layer(
                    id = 1, name = "L",
                    strokes = listOf(
                        Stroke(
                            Tool.MARKER, 0xFF3B9CFF.toInt(), 40f, 0.42f,
                            FloatArray(30 * 3).also { pts ->
                                for (i in 0 until 30) {
                                    pts[i * 3] = 60f + i * 7f
                                    pts[i * 3 + 1] = 160f + (i % 5) * 6f
                                    pts[i * 3 + 2] = 1f
                                }
                            }
                        )
                    )
                )
            )
        )
    )

    @Test
    fun eraserLayerUsesCompositedPath() = compare(
        "eraser layer",
        project(
            listOf(
                Layer(
                    id = 1, name = "L",
                    strokes = listOf(
                        stroke(Tool.PEN, 0xFF202020.toInt(), 20f, 0),
                        stroke(Tool.ERASER, 0xFF202020.toInt(), 24f, 1)
                    )
                )
            )
        )
    )

    @Test
    fun twoLayers() = compare(
        "two layers",
        project(
            listOf(
                Layer(
                    id = 1, name = "A", opacity = 0.8f,
                    strokes = listOf(stroke(Tool.PEN, 0xFFB03030.toInt(), 10f, 0))
                ),
                Layer(
                    id = 2, name = "B",
                    strokes = listOf(stroke(Tool.PEN, 0xFF204080.toInt(), 6f, 1))
                )
            )
        )
    )

    @Test
    fun hiddenLayerIsIgnored() {
        val p = project(
            listOf(
                Layer(id = 1, name = "A", strokes = listOf(stroke(Tool.PEN, 0xFF202020.toInt(), 10f, 0))),
                Layer(id = 2, name = "B", visible = false, strokes = listOf(stroke(Tool.PEN, 0xFFFF0000.toInt(), 30f, 1)))
            )
        )
        val frame = p.frames[0]
        val bmp = FrameRasterizer.rasterize(p, frame, 320, 320, p.background)
        val pixels = IntArray(320 * 320)
        bmp.getPixels(pixels, 0, 320, 0, 0, 320, 320)
        var red = 0
        for (px in pixels) if (Color.red(px) > 200 && Color.green(px) < 60) red++
        println("RASTER hidden layer: red pixels $red")
        assertTrue("hidden layer must not be drawn", red == 0)
        bmp.recycle()
    }
}
