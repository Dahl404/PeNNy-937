package com.dahl404.penny937.export

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.dahl404.penny937.model.Project
import com.dahl404.penny937.paint.FrameRasterizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * MP4 (H.264) export.
 *
 * Frames are vector-rendered on worker threads, converted to YUV 4:2:0 and fed to a
 * MediaCodec encoder through its flexible-YUV input buffers, then muxed to MP4. The
 * capture surface path was deliberately not used: MediaCodec's own documentation warns
 * that `Surface.lockCanvas` "may fail or produce unexpected results" on codec input
 * surfaces, and this app has no GL pipeline to blit through instead.
 */
object VideoExporter {

    class VideoExportException(message: String, cause: Throwable? = null) :
        Exception(message, cause)

    private const val TIMEOUT_US = 10_000L
    private const val PIPELINE_DEPTH = 4

    /** Render + encode one project frame at a time, streaming into the muxer. */
    suspend fun export(
        project: Project,
        file: File,
        targetWidth: Int,
        onProgress: suspend (Float) -> Unit
    ) {
        val (width, height) = VideoPlan.dimensions(project, targetWidth)
        val encoderThread = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "penny-mp4-encoder")
        }
        try {
            withContext(encoderThread.asCoroutineDispatcher()) {
                encode(project, file, width, height, onProgress)
            }
        } finally {
            encoderThread.shutdown()
        }
    }

    private suspend fun encode(
        project: Project,
        file: File,
        width: Int,
        height: Int,
        onProgress: suspend (Float) -> Unit
    ) = coroutineScope {
        val frames = project.frames
        val fps = project.fps
        val total = frames.size
        val format = VideoPlan.format(project, width, height)
        val encoder = openEncoder(format)
        val muxer = try {
            MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (t: Throwable) {
            encoder.release()
            throw VideoExportException("Could not open the video file", t)
        }
        val state = MuxState(muxer)
        var completed = false
        try {
            encoder.start()

            // Render frames ahead of the encoder so vector rasterisation overlaps with
            // encoding; the channel is bounded by the frame size, not just a constant,
            // so a 4K export cannot queue four full-resolution ARGB buffers.
            val depth = ExportMemory.framesPerBatch(
                ExportMemory.videoFrameBytes(width, height),
                PIPELINE_DEPTH,
                ExportMemory.runtimeBudget()
            )
            val prepared = Channel<Prepared>(depth)
            val producer = launch(Dispatchers.Default) {
                try {
                    for (index in frames.indices) {
                        prepared.send(renderFrame(project, index, width, height))
                    }
                } finally {
                    prepared.close()
                }
            }

            for (frame in prepared) {
                val inputIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex < 0) {
                    throw VideoExportException("The video encoder stopped accepting frames")
                }
                val image = encoder.getInputImage(inputIndex)
                    ?: throw VideoExportException(
                        "This device's H.264 encoder does not accept YUV frames"
                    )
                Yuv420.fromArgb(frame.pixels, width, height, image.planes)
                encoder.queueInputBuffer(inputIndex, 0, width * height * 3 / 2, frame.ptsUs, 0)
                drain(encoder, state, endOfStream = false)
                onProgress((frame.index + 1).toFloat() / total)
            }
            producer.join()

            // Flush the encoder.
            val inputIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex >= 0) {
                encoder.queueInputBuffer(
                    inputIndex, 0, 0,
                    VideoPlan.presentationTimeUs(project, total), MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
            drain(encoder, state, endOfStream = true)
            completed = true
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: VideoExportException) {
            throw t
        } catch (t: Throwable) {
            throw VideoExportException(t.message ?: "Video encoding failed", t)
        } finally {
            runCatching { encoder.stop() }
            encoder.release()
            if (completed && state.started) {
                runCatching { state.muxer.stop() }
                state.muxer.release()
            } else {
                runCatching { state.muxer.release() }
                file.delete()
            }
        }
    }

    private fun openEncoder(format: MediaFormat): MediaCodec {
        val encoder = try {
            MediaCodec.createEncoderByType(VideoPlan.MIME)
        } catch (t: Throwable) {
            throw VideoExportException("No H.264 encoder on this device", t)
        }
        return try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder
        } catch (t: Throwable) {
            encoder.release()
            throw VideoExportException(
                "This device can't encode ${format.getInteger(MediaFormat.KEY_COLOR_FORMAT)} frames",
                t
            )
        }
    }

    private class Prepared(val index: Int, val pixels: IntArray, val ptsUs: Long)

    private class MuxState(val muxer: MediaMuxer) {
        var trackIndex = -1
        var started = false
    }

    private fun renderFrame(project: Project, index: Int, width: Int, height: Int): Prepared {
        val bitmap = FrameRasterizer.rasterize(
            project, project.frames[index], width, height, project.background
        )
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        return Prepared(index, pixels, VideoPlan.presentationTimeUs(project, index))
    }

    private fun drain(encoder: MediaCodec, state: MuxState, endOfStream: Boolean) {
        val info = MediaCodec.BufferInfo()
        val deadline = if (endOfStream) System.nanoTime() + 15_000_000_000L else 0L
        while (true) {
            val index = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                    if (System.nanoTime() > deadline) {
                        throw VideoExportException("The video encoder did not finish")
                    }
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (state.started) {
                        throw VideoExportException("Encoder changed format mid-stream")
                    }
                    state.trackIndex = state.muxer.addTrack(encoder.outputFormat)
                    state.muxer.start()
                    state.started = true
                }
                index >= 0 -> {
                    val buffer = encoder.getOutputBuffer(index)
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (buffer != null && !isConfig && info.size > 0 && state.started) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        state.muxer.writeSampleData(state.trackIndex, buffer, info)
                    }
                    encoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
                else -> Unit
            }
        }
    }
}

/** All the MP4 numbers that can be reasoned about (and tested) without a codec. */
object VideoPlan {
    const val MIME = "video/avc"

    /** H.264 needs even dimensions; the aspect ratio is preserved as closely as possible. */
    fun dimensions(project: Project, targetWidth: Int): Pair<Int, Int> {
        val requested = targetWidth.coerceAtLeast(16).coerceAtMost(project.width)
        val width = max(16, even(requested))
        val scaled = (width.toFloat() * project.height / project.width).roundToInt()
        val height = max(16, even(scaled))
        return width to height
    }

    /** H.264 wants even dimensions; nudge up so the aspect ratio stays closest. */
    private fun even(value: Int): Int = (value / 2) * 2 + (value % 2) * 2

    /** Roughly 0.12 bits per pixel per frame keeps flat animation clean without bloat. */
    fun bitrate(width: Int, height: Int, fps: Int): Int {
        val bits = width.toLong() * height.toLong() * fps.toLong() * 12L / 100L
        return bits.coerceIn(800_000L, 24_000_000L).toInt()
    }

    fun format(project: Project, width: Int, height: Int): MediaFormat =
        MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate(width, height, project.fps))
            setInteger(MediaFormat.KEY_FRAME_RATE, project.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

    /** Presentation time of a frame on the project timeline, in microseconds. */
    fun presentationTimeUs(project: Project, frameIndex: Int): Long =
        project.tickOfFrame(frameIndex).toLong() * 1_000_000L / project.fps.coerceAtLeast(1)

    fun durationUs(project: Project): Long =
        project.totalTicks.toLong() * 1_000_000L / project.fps.coerceAtLeast(1)
}

/**
 * ARGB to YUV 4:2:0 (BT.601 studio swing), written straight into the encoder's planes
 * with whatever row and pixel strides it asks for.
 */
object Yuv420 {

    fun fromArgb(pixels: IntArray, width: Int, height: Int, planes: Array<android.media.Image.Plane>) {
        require(planes.size >= 3) { "expected three YUV planes" }
        val y = planes[0]
        val u = planes[1]
        val v = planes[2]
        fromArgb(
            pixels, width, height,
            y.buffer, y.rowStride, y.pixelStride,
            u.buffer, u.rowStride, u.pixelStride,
            v.buffer, v.rowStride, v.pixelStride
        )
    }

    fun fromArgb(
        pixels: IntArray,
        width: Int,
        height: Int,
        y: ByteBuffer, yRowStride: Int, yPixelStride: Int,
        u: ByteBuffer, uRowStride: Int, uPixelStride: Int,
        v: ByteBuffer, vRowStride: Int, vPixelStride: Int
    ) {
        require(pixels.size >= width * height) { "pixel buffer too small" }
        // Luma.
        var p = 0
        for (row in 0 until height) {
            var yi = row * yRowStride
            for (col in 0 until width) {
                val c = pixels[p++]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                y.put(yi, clamp(((66 * r + 129 * g + 25 * b + 128) shr 8) + 16))
                yi += yPixelStride
            }
        }
        // Chroma, averaged over each 2x2 block.
        val chromaWidth = (width + 1) / 2
        val chromaHeight = (height + 1) / 2
        for (row in 0 until chromaHeight) {
            val row0 = row * 2
            val row1 = min(row0 + 1, height - 1) * width
            val row0Base = row0 * width
            var ui = row * uRowStride
            var vi = row * vRowStride
            for (col in 0 until chromaWidth) {
                val col0 = col * 2
                val col1 = min(col0 + 1, width - 1)
                var r = 0
                var g = 0
                var b = 0
                var c = pixels[row0Base + col0]
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                c = pixels[row0Base + col1]
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                c = pixels[row1 + col0]
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                c = pixels[row1 + col1]
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                r = r shr 2
                g = g shr 2
                b = b shr 2
                u.put(ui, clamp(((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128))
                v.put(vi, clamp(((112 * r - 94 * g - 18 * b + 128) shr 8) + 128))
                ui += uPixelStride
                vi += vPixelStride
            }
        }
    }

    private fun clamp(value: Int): Byte =
        (if (value < 0) 0 else if (value > 255) 255 else value).toByte()
}
