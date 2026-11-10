package com.dahl404.penny937.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ExtractedVideoFrame(val file: String, val width: Int, val height: Int)

data class ExtractedVideo(
    val frames: List<ExtractedVideoFrame>,
    val durationMs: Long
)

class VideoImportException(message: String) : Exception(message)

/**
 * Samples a video at exactly the project frame rate and writes each sample as a
 * small JPEG that can back a reference layer. Frames are one-per-timeline-frame,
 * so importing a 10s clip at 12 fps produces 120 references.
 */
object VideoReferenceExtractor {
    const val MAX_FRAMES = 3000
    private const val MAX_DIMENSION = 1920
    private const val JPEG_QUALITY = 86

    /**
     * Number of timeline frames a clip will occupy at [targetFps]. Kept separate
     * from the Android decoder so the cap is testable on the JVM.
     */
    fun frameCount(durationMs: Long, targetFps: Int): Int =
        ceil(durationMs.toDouble() * targetFps / 1000.0).toInt().coerceAtLeast(1)

    /**
     * Throws [VideoImportException] when [total] would exceed the per-import cap.
     * Lowering the project frame rate or trimming the clip is the way out.
     */
    fun ensureWithinCap(total: Int, targetFps: Int) {
        if (total > MAX_FRAMES) {
            throw VideoImportException(
                "That clip is too long at $targetFps fps ($total frames, max $MAX_FRAMES). " +
                    "Lower the project frame rate or trim the clip."
            )
        }
    }

    suspend fun extract(
        context: Context,
        projectId: Long,
        uri: Uri,
        targetFps: Int,
        onTotal: suspend (Int) -> Unit,
        onProgress: suspend (Int) -> Unit
    ): ExtractedVideo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val files = ArrayList<String>()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: throw VideoImportException("Could not read the video length")
            if (durationMs <= 0L) throw VideoImportException("That video has no duration")

            val total = frameCount(durationMs, targetFps)
            ensureWithinCap(total, targetFps)
            onTotal(total)

            val naturalWidth = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 0
            val naturalHeight = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 0
            val rotation = (retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: 0).let { ((it % 360) + 360) % 360 }
            val storedLandscape = naturalWidth >= naturalHeight

            val stamp = System.currentTimeMillis()
            val result = ArrayList<ExtractedVideoFrame>(total)
            for (i in 0 until total) {
                currentCoroutineContext().ensureActive()
                val timeUs = i.toLong() * 1_000_000L / targetFps
                var bitmap = grabFrame(retriever, timeUs, naturalWidth, naturalHeight)
                if (bitmap == null) {
                    if (result.isEmpty()) {
                        throw VideoImportException("Could not decode that video")
                    }
                    result.add(result.last())
                    onProgress(i + 1)
                    continue
                }
                bitmap = orient(bitmap, rotation, storedLandscape)
                bitmap = downscale(bitmap, MAX_DIMENSION)
                val name = "video-$stamp-$i.jpg"
                val ok = ReferenceFiles.saveJpeg(projectId, name, bitmap, JPEG_QUALITY)
                val width = bitmap.width
                val height = bitmap.height
                bitmap.recycle()
                if (!ok) {
                    ReferenceFiles.delete(projectId, name)
                    throw VideoImportException("Could not save the video frame")
                }
                files.add(name)
                result.add(ExtractedVideoFrame(name, width, height))
                onProgress(i + 1)
            }
            if (result.isEmpty()) throw VideoImportException("No frames could be read")
            ExtractedVideo(result, durationMs)
        } catch (t: Throwable) {
            files.forEach { ReferenceFiles.delete(projectId, it) }
            throw t
        } finally {
            retriever.release()
        }
    }

    private fun grabFrame(
        retriever: MediaMetadataRetriever,
        timeUs: Long,
        naturalWidth: Int,
        naturalHeight: Int
    ): Bitmap? {
        if (Build.VERSION.SDK_INT >= 27 && naturalWidth > 0 && naturalHeight > 0) {
            val factor = min(1f, MAX_DIMENSION.toFloat() / max(naturalWidth, naturalHeight))
            val targetW = (naturalWidth * factor).roundToInt().coerceAtLeast(1)
            val targetH = (naturalHeight * factor).roundToInt().coerceAtLeast(1)
            val scaled = runCatching {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    targetW,
                    targetH
                )
            }.getOrNull()
            if (scaled != null) return scaled
        }
        val full = runCatching {
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
        }.getOrNull() ?: return null
        return downscale(full, MAX_DIMENSION)
    }

    private fun orient(bitmap: Bitmap, rotation: Int, storedLandscape: Boolean): Bitmap {
        if (rotation == 0) return bitmap
        val bitmapLandscape = bitmap.width >= bitmap.height
        val alreadyRotated = (rotation == 90 || rotation == 270) &&
            bitmapLandscape != storedLandscape
        if (alreadyRotated) return bitmap
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrNull() ?: return bitmap
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun downscale(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largest
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = runCatching {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }.getOrNull() ?: return bitmap
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }
}
