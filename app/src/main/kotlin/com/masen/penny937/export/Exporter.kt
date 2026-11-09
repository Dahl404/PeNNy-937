package com.masen.penny937.export

import android.content.Context
import android.graphics.Bitmap
import com.masen.penny937.model.Project
import com.masen.penny937.paint.FrameRasterizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

enum class ExportFormat(val label: String, val mime: String) {
    GIF("GIF", "image/gif"),
    PNG_SEQUENCE("PNG frames", "application/zip"),
    CURRENT_FRAME("Still frame", "image/png")
}

object Exporter {

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
            ExportFormat.GIF -> File(dir, "$base-$stamp.gif")
            ExportFormat.PNG_SEQUENCE -> File(dir, "$base-$stamp.zip")
            ExportFormat.CURRENT_FRAME -> File(dir, "$base-frame-${currentFrame + 1}-$stamp.png")
        }
        when (format) {
            ExportFormat.GIF -> exportGif(project, file, targetWidth, onProgress)
            ExportFormat.PNG_SEQUENCE -> exportPngZip(project, file, onProgress)
            ExportFormat.CURRENT_FRAME -> exportStill(project, currentFrame, file, onProgress)
        }
        file
    }

    private suspend fun exportGif(
        project: Project,
        file: File,
        targetWidth: Int,
        onProgress: suspend (Float) -> Unit
    ) {
        val width = targetWidth.coerceIn(64, project.width)
        val height = (width.toFloat() * project.height / project.width).roundToInt().coerceAtLeast(1)
        FileOutputStream(file).use { fos ->
            val gif = GifWriter(BufferedOutputStream(fos, 1 shl 16), width, height, loop = true)
            val pixels = IntArray(width * height)
            project.frames.forEachIndexed { index, frame ->
                val bitmap = FrameRasterizer.rasterize(
                    project, frame, width, height, project.background
                )
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                bitmap.recycle()
                val delay = max(1, (100 * frame.hold.toFloat() / project.fps).roundToInt())
                gif.addFrame(pixels, delay)
                onProgress((index + 1).toFloat() / project.frames.size)
            }
            gif.close()
        }
    }

    private suspend fun exportPngZip(
        project: Project,
        file: File,
        onProgress: suspend (Float) -> Unit
    ) {
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
                project.frames.forEachIndexed { index, frame ->
                    val bitmap = FrameRasterizer.rasterize(
                        project, frame, project.width, project.height, project.background
                    )
                    zip.putNextEntry(ZipEntry("frame_%05d.png".format(index + 1)))
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)
                    zip.closeEntry()
                    bitmap.recycle()
                    onProgress((index + 1).toFloat() / project.frames.size)
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
