package com.dahl404.penny937.export

import android.content.Context
import android.graphics.Bitmap
import com.dahl404.penny937.model.Project
import com.dahl404.penny937.paint.FrameRasterizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ExportFormat(val label: String, val mime: String) {
    MP4("MP4 video", "video/mp4"),
    GIF("GIF", "image/gif"),
    PNG_SEQUENCE("PNG frames", "application/zip"),
    CURRENT_FRAME("Still frame", "image/png");

    /** Formats that render the whole timeline and offer an output-width choice. */
    val isTimeline: Boolean get() = this == MP4 || this == GIF
}

object Exporter {

    /** Frames are independent, so rasterising and encoding runs on several cores. */
    private val frameParallelism: Int
        get() = parallelismOverride
            ?: Runtime.getRuntime().availableProcessors().coerceIn(1, 6)

    /** Test hook: pin the worker count to measure serial vs parallel exports. */
    internal var parallelismOverride: Int? = null

    suspend fun export(
        context: Context,
        project: Project,
        currentFrame: Int,
        format: ExportFormat,
        targetWidth: Int,
        onProgress: suspend (Float) -> Unit
    ): File = withContext(Dispatchers.Default) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { f ->
            if (System.currentTimeMillis() - f.lastModified() > 24L * 60 * 60 * 1000) f.delete()
        }
        val stamp = System.currentTimeMillis()
        val base = sanitize(project.name)
        val file = when (format) {
            ExportFormat.MP4 -> File(dir, "$base-$stamp.mp4")
            ExportFormat.GIF -> File(dir, "$base-$stamp.gif")
            ExportFormat.PNG_SEQUENCE -> File(dir, "$base-$stamp.zip")
            ExportFormat.CURRENT_FRAME -> File(dir, "$base-frame-${currentFrame + 1}-$stamp.png")
        }
        when (format) {
            ExportFormat.MP4 -> VideoExporter.export(project, file, targetWidth, onProgress)
            ExportFormat.GIF -> exportGif(project, file, targetWidth, onProgress)
            ExportFormat.PNG_SEQUENCE -> exportPngZip(project, file, onProgress)
            ExportFormat.CURRENT_FRAME -> exportStill(project, currentFrame, file, onProgress)
        }
        file
    }

    /**
     * Renders and encodes frames on all available cores, writing the GIF in order.
     *
     * Encoding a frame is independent of every other frame, so the CPU-bound part
     * (vector rasterisation, palette quantisation, LZW) fans out; only the byte
     * stream stays serial. Frames are produced in bounded batches so a 3000-frame
     * export never buffers more than a couple of dozen encoded frames.
     */
    private suspend fun exportGif(
        project: Project,
        file: File,
        targetWidth: Int,
        onProgress: suspend (Float) -> Unit
    ) {
        val width = targetWidth.coerceIn(64, project.width)
        val height = (width.toFloat() * project.height / project.width).roundToInt().coerceAtLeast(1)
        val frames = project.frames
        FileOutputStream(file).use { fos ->
            val gif = GifWriter(BufferedOutputStream(fos, 1 shl 16), width, height, loop = true)
            var written = 0
            var index = 0
            val desiredBatch = frameParallelism * 2
            val frameBytes = ExportMemory.gifFrameBytes(width, height)
            while (index < frames.size) {
                // Re-evaluate each batch: a transient allocation elsewhere should
                // shrink the next batch instead of pushing the process to the limit.
                val batch = ExportMemory.framesPerBatch(
                    frameBytes, desiredBatch, ExportMemory.runtimeBudget()
                )
                val end = min(index + batch, frames.size)
                val encoded = coroutineScope {
                    (index until end).map { i ->
                        async(Dispatchers.Default) {
                            val frame = frames[i]
                            val bitmap = FrameRasterizer.rasterize(
                                project, frame, width, height, project.background
                            )
                            val pixels = IntArray(width * height)
                            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                            bitmap.recycle()
                            val quantized = FrameQuantizer().quantize(pixels, width, height, 256)
                            val blocks = LzwEncoder().compress(quantized.indices)
                            val delay = max(1, (100 * frame.hold.toFloat() / project.fps).roundToInt())
                            Pair(GifWriter.EncodedFrame(quantized.palette, blocks), delay)
                        }
                    }.awaitAll()
                }
                for ((frame, delay) in encoded) {
                    gif.addEncodedFrame(frame, delay)
                    written++
                }
                onProgress(written.toFloat() / frames.size)
                index = end
            }
            gif.close()
        }
    }

    private suspend fun exportPngZip(
        project: Project,
        file: File,
        onProgress: suspend (Float) -> Unit
    ) {
        val frames = project.frames
        FileOutputStream(file).use { fos ->
            ZipOutputStream(BufferedOutputStream(fos, 1 shl 16)).use { zip ->
                val info = buildString {
                    appendLine("PeNNy-937 project export")
                    appendLine("Name: ${project.name}")
                    appendLine("Size: ${project.width} x ${project.height}")
                    appendLine("Frame rate: ${project.fps} fps")
                    appendLine("Frames: ${project.frames.size}")
                    appendLine("Total ticks: ${project.totalTicks}")
                }
                zip.putNextEntry(ZipEntry("about.txt"))
                zip.write(info.toByteArray())
                zip.closeEntry()
                var written = 0
                var index = 0
                val desiredBatch = frameParallelism * 2
                val frameBytes = ExportMemory.pngFrameBytes(project.width, project.height)
                while (index < frames.size) {
                    val batch = ExportMemory.framesPerBatch(
                        frameBytes, desiredBatch, ExportMemory.runtimeBudget()
                    )
                    val end = min(index + batch, frames.size)
                    val pngs = coroutineScope {
                        (index until end).map { i ->
                            async(Dispatchers.Default) {
                                val bitmap = FrameRasterizer.rasterize(
                                    project, frames[i], project.width, project.height,
                                    project.background
                                )
                                val bytes = ByteArrayOutputStream(1 shl 16)
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
                                bitmap.recycle()
                                bytes.toByteArray()
                            }
                        }.awaitAll()
                    }
                    pngs.forEachIndexed { offset, bytes ->
                        zip.putNextEntry(ZipEntry("frame_%05d.png".format(index + offset + 1)))
                        zip.write(bytes)
                        zip.closeEntry()
                        written++
                    }
                    onProgress(written.toFloat() / frames.size)
                    index = end
                }
            }
        }
    }

    private suspend fun exportStill(
        project: Project,
        frameIndex: Int,
        file: File,
        onProgress: suspend (Float) -> Unit
    ) {
        val frame = project.frameAt(frameIndex)
        val bitmap = FrameRasterizer.rasterize(
            project, frame, project.width, project.height, project.background
        )
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        onProgress(1f)
    }

    private fun sanitize(name: String): String {
        val cleaned = name.trim().replace(Regex("[^A-Za-z0-9 _-]"), "").replace(' ', '-')
        return cleaned.ifBlank { "penny" }.take(40)
    }
}
