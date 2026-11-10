package com.dahl404.penny937.perf

import android.graphics.Bitmap
import android.graphics.Canvas
import com.dahl404.penny937.data.ProjectCodec
import com.dahl404.penny937.export.FrameQuantizer
import com.dahl404.penny937.export.ExportFormat
import com.dahl404.penny937.export.Exporter
import com.dahl404.penny937.export.GifWriter
import com.dahl404.penny937.export.LzwEncoder
import com.dahl404.penny937.model.Frame
import com.dahl404.penny937.model.Layer
import com.dahl404.penny937.model.Project
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import com.dahl404.penny937.paint.FrameRasterizer
import com.dahl404.penny937.paint.Ink
import com.dahl404.penny937.paint.StrokeInker
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.system.measureNanoTime

/**
 * Not an assertion-heavy test: a repeatable timing harness for the hot paths so
 * optimisation work can be measured instead of guessed. Run with
 * `./gradlew testDebugUnitTest --tests '*RenderBenchTest*'` and read the log.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], manifest = Config.NONE)
class RenderBenchTest {

    private val rnd = Random(42)

    private fun project(frames: Int, layers: Int, strokes: Int, points: Int, size: Int = 1080): Project {
        val now = 1L
        val frameList = (0 until frames).map { f ->
            Frame(
                id = 100L + f,
                layers = (0 until layers).map { l ->
                    Layer(
                        id = (f * 10 + l).toLong() + 1,
                        name = "Layer ${l + 1}",
                        strokes = (0 until strokes).map { s -> stroke(points, size, s + f) }
                    )
                }
            )
        }
        return Project(
            id = 1L,
            name = "bench",
            width = size,
            height = size,
            fps = 12,
            background = 0xFFFBF9F4.toInt(),
            frames = frameList,
            createdAt = now,
            updatedAt = now
        )
    }

    private var forceTool: Tool? = null

    private fun stroke(points: Int, size: Int, seed: Int): Stroke {
        val r = Random(seed)
        val pts = FloatArray(points * 3)
        var x = r.nextFloat() * size
        var y = r.nextFloat() * size
        var angle = r.nextFloat() * 6.28f
        for (i in 0 until points) {
            angle += (r.nextFloat() - 0.5f) * 0.5f
            x = (x + cos(angle) * 3f).coerceIn(0f, size.toFloat())
            y = (y + sin(angle) * 3f).coerceIn(0f, size.toFloat())
            pts[i * 3] = x
            pts[i * 3 + 1] = y
            pts[i * 3 + 2] = 0.3f + r.nextFloat() * 0.7f
        }
        val tool = forceTool ?: Tool.entries[seed % 3]
        return Stroke(tool, 0xFF202020.toInt(), if (tool == Tool.MARKER) 40f else 8f, 1f, pts)
    }

    private inline fun time(label: String, warmup: Int = 2, runs: Int = 5, block: () -> Unit) {
        repeat(warmup) { block() }
        val samples = LongArray(runs) { measureNanoTime { block() } }
        val best = samples.min() / 1_000_000.0
        val avg = samples.average() / 1_000_000.0
        println(String.format("BENCH %-38s best %8.2f ms  avg %8.2f ms", label, best, avg))
    }

    @Test
    fun penOnly() {
        forceTool = Tool.PEN
        val p = project(frames = 2, layers = 1, strokes = 90, points = 120)
        val frame = p.frames[0]
        time("pen-only rasterize 720px") {
            FrameRasterizer.rasterize(p, frame, 720, 720, p.background)
        }
        // Outline construction cost alone.
        val outlines = frame.layers[0].strokes.map { Ink.buildOutline(it) }
        println("BENCH outlines built: ${outlines.size}")
        time("pen-only buildOutline x90") {
            frame.layers[0].strokes.forEach { Ink.buildOutline(it) }
        }
        val bmp = android.graphics.Bitmap.createBitmap(720, 720, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = Ink.paintFor(Tool.PEN, 0xFF202020.toInt(), 8f, 1f)
        paint.style = android.graphics.Paint.Style.FILL
        canvas.scale(720f / 1080f, 720f / 1080f)
        time("pen-only fill saved outlines x90") {
            outlines.forEach { it?.let { path -> canvas.drawPath(path, paint) } }
        }
        forceTool = null
    }

    @Test
    fun perTool() {
        // Warm the JIT for all tools before measuring any of them.
        for (tool in listOf(Tool.PEN, Tool.PENCIL, Tool.MARKER)) {
            forceTool = tool
            val warm = project(frames = 1, layers = 1, strokes = 90, points = 120)
            repeat(3) { FrameRasterizer.rasterize(warm, warm.frames[0], 720, 720, warm.background) }
        }
        for (tool in listOf(Tool.PEN, Tool.PENCIL, Tool.MARKER)) {
            forceTool = tool
            val p = project(frames = 2, layers = 1, strokes = 90, points = 120)
            val frame = p.frames[0]
            time("$tool rasterize 720") {
                FrameRasterizer.rasterize(p, frame, 720, 720, p.background)
            }
        }
        forceTool = null
    }

    @Test
    fun render() {
        // A dense drawing: 3 layers x 30 strokes x 120 points.
        val p = project(frames = 4, layers = 3, strokes = 30, points = 120)
        val frame = p.frames[0]
        time("rasterize 1080px frame (3x30x120)") {
            FrameRasterizer.rasterize(p, frame, 1080, 1080, p.background)
        }
        time("rasterize 720px frame") {
            FrameRasterizer.rasterize(p, frame, 720, 720, p.background)
        }
        time("rasterize 480px frame") {
            FrameRasterizer.rasterize(p, frame, 480, 480, p.background)
        }
        time("rasterize 104px thumb") {
            FrameRasterizer.rasterize(p, frame, 104, 104, p.background)
        }
        time("rasterize 4 frames @720") {
            p.frames.forEach { FrameRasterizer.rasterize(p, it, 720, 720, p.background) }
        }
    }

    @Test
    fun gifPipeline() {
        val p = project(frames = 6, layers = 3, strokes = 30, points = 120)
        val width = 480
        val height = 480
        val pixels = IntArray(width * height)

        time("gif: 6 frames @480 (raster+quant+lzw)") {
            val out = ByteArrayOutputStream()
            val gif = GifWriter(out, width, height)
            p.frames.forEach { frame ->
                val bmp = FrameRasterizer.rasterize(p, frame, width, height, p.background)
                bmp.getPixels(pixels, 0, width, 0, 0, width, height)
                bmp.recycle()
                gif.addFrame(pixels, 8)
            }
            gif.close()
        }

        // Isolate quantizer + LZW on a realistic (mostly flat, few-ink) image.
        val flat = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            if ((x / 30 + y / 30) % 7 == 0) 0xFF202020.toInt()
            else if ((x - 240) * (x - 240) + (y - 240) * (y - 240) < 12000) 0xFF3B9CFF.toInt()
            else 0xFFFBF9F4.toInt()
        }
        val noisy = IntArray(width * height) { rnd.nextInt() or 0xFF000000.toInt() }
        time("quantize flat 480^2") { FrameQuantizer().quantize(flat, width, height, 256) }
        time("quantize noisy 480^2") { FrameQuantizer().quantize(noisy, width, height, 256) }
        val indices = FrameQuantizer().quantize(flat, width, height, 256).indices
        time("lzw flat 480^2") { LzwEncoder().compress(indices) }
    }

    @Test
    fun codec() {
        val p = project(frames = 60, layers = 3, strokes = 30, points = 120)
        time("codec write 5400 strokes x120pts") {
            ProjectCodec.write(p, ByteArrayOutputStream())
        }
        val bytes = ByteArrayOutputStream().also { ProjectCodec.write(p, it) }.toByteArray()
        println("BENCH codec size: ${bytes.size / 1024} KiB")
        time("codec read 5400 strokes x120pts") {
            ProjectCodec.read(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun exportPipeline() {
        val p = project(frames = 24, layers = 1, strokes = 24, points = 100)
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val dir = java.io.File(context.cacheDir, "bench").apply { mkdirs() }
        kotlinx.coroutines.runBlocking {
            for (workers in listOf(1, 4)) {
                Exporter.parallelismOverride = workers
                time("gif export 24 frames @480, ${workers}w", warmup = 0, runs = 2) {
                    kotlinx.coroutines.runBlocking {
                        Exporter.export(
                            context, p, 0, ExportFormat.GIF, 480
                        ) { }
                    }
                }
            }
            Exporter.parallelismOverride = null
        }
        dir.deleteRecursively()
    }

    @Test
    fun liveInkLatency() {
        val sizePx = 1080
        val overlay = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlay)
        val points = FloatArray(300 * 3)
        val r = Random(11)
        var x = 200f
        var y = 500f
        var a = 0.4f
        for (i in 0 until 300) {
            a += (r.nextFloat() - 0.5f) * 0.18f
            x = (x + cos(a) * 4f).coerceIn(40f, 1040f)
            y = (y + sin(a) * 4f).coerceIn(40f, 1040f)
            points[i * 3] = x
            points[i * 3 + 1] = y
            points[i * 3 + 2] = 0.6f
        }
        // Warm up, then measure a whole stroke delivered 3 samples at a time.
        for (pass in 0..2) {
            val inker = StrokeInker(Tool.PEN, 0xFF202020.toInt(), 12f, 1f, true)
            var i = 0
            val start = System.nanoTime()
            var events = 0
            while (i < 300) {
                repeat(3) { if (i < 300) { inker.addRaw(points[i * 3], points[i * 3 + 1], points[i * 3 + 2]); i++ } }
                inker.drawNew(canvas)
                events++
            }
            if (pass == 2) {
                val total = (System.nanoTime() - start) / 1_000_000.0
                println(
                    String.format(
                        "BENCH live ink 300 samples @1080: %d events, %.2f ms total, %.3f ms per event",
                        events, total, total / events
                    )
                )
            }
        }
    }

    @Test
    fun bitmapOnly() {
        val src = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888)
        src.eraseColor(0xFFFBF9F4.toInt())
        time("bitmap getPixels 1080^2") { src.getPixels(IntArray(1080 * 1080), 0, 1080, 0, 0, 1080, 1080) }
    }
}
