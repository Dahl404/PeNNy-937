package com.dahl404.penny937.editor

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dahl404.penny937.model.Project
import com.dahl404.penny937.paint.FrameRasterizer
import com.dahl404.penny937.paint.FrameRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Owns the playhead and the off-main-thread frame prefetcher. The editor keeps only
 * project mutations; playback state, lookahead and cache lifetime live here.
 */
class PlaybackController(
    private val scope: CoroutineScope,
    private val renderer: FrameRenderer,
    private val project: () -> Project,
    private val currentFrame: () -> Int
) {
    var isPlaying by mutableStateOf(false)
        private set
    var previewFrame by mutableStateOf(0)
        private set

    private var prefetchJob: Job? = null

    fun start(): Boolean {
        val snapshot = project()
        if (snapshot.frames.size <= 1) return false
        transitionToStart()
        startPrefetch()
        return true
    }

    fun stop() {
        isPlaying = false
        previewFrame = currentFrame()
        cancelPrefetch()
        renderer.clearPlaybackCache()
    }

    fun updatePreviewFrame(index: Int) {
        val frames = project().frames
        previewFrame = index.coerceIn(0, frames.lastIndex)
    }

    fun dispose() {
        cancelPrefetch()
        renderer.clearPlaybackCache()
        isPlaying = false
    }

    private fun transitionToStart() {
        previewFrame = 0
        isPlaying = true
    }

    private fun cancelPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
    }

    /**
     * Renders the next few playback frames on a worker thread and hands finished
     * bitmaps to the main thread. The canvas never rasterises vectors while the
     * playhead is advancing.
     */
    private fun startPrefetch() {
        cancelPrefetch()
        renderer.clearPlaybackCache()
        prefetchJob = scope.launch {
            var snapshot = project()
            var width = min(snapshot.width, PLAYBACK_PREVIEW_WIDTH)
            while (isActive && isPlaying) {
                if (project() !== snapshot) {
                    snapshot = project()
                    width = min(snapshot.width, PLAYBACK_PREVIEW_WIDTH)
                    renderer.clearPlaybackCache()
                }
                val frames = snapshot.frames
                if (frames.isEmpty()) {
                    delay(32)
                    continue
                }
                val current = previewFrame.coerceIn(0, frames.lastIndex)
                var rendered = false
                val height = (width.toFloat() * snapshot.height / snapshot.width)
                    .roundToInt().coerceAtLeast(1)
                for (offset in 0 until PLAYBACK_LOOKAHEAD) {
                    if (!isActive || !isPlaying) break
                    val index = (current + offset) % frames.size
                    val frame = frames[index]
                    if (renderer.hasPlaybackBitmap(index, frame.id, frame.revision, width)) {
                        continue
                    }
                    var renderedBitmap: Bitmap? = null
                    try {
                        withContext(Dispatchers.Default) {
                            renderedBitmap = FrameRasterizer.rasterize(
                                snapshot, frame, width, height, snapshot.background
                            )
                        }
                    } catch (c: kotlinx.coroutines.CancellationException) {
                        renderedBitmap?.recycle()
                        throw c
                    }
                    val bitmap = renderedBitmap ?: break
                    if (!isActive || !isPlaying) {
                        bitmap.recycle()
                        break
                    }
                    renderer.putPlaybackBitmap(index, frame.id, frame.revision, width, bitmap)
                    rendered = true
                    // Re-plan if the playhead moved while this frame was rendering.
                    if (previewFrame != current) break
                }
                if (!rendered) delay(12)
            }
        }
    }

    private companion object {
        const val PLAYBACK_PREVIEW_WIDTH = 900
        const val PLAYBACK_LOOKAHEAD = 8
    }
}
