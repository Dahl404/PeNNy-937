package com.dahl404.penny937.paint

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], manifest = Config.NONE)
class PlaybackCacheTest {

    private fun bitmap(): Bitmap =
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

    @Test
    fun readyFramesAreReturnedForTheMatchingDocument() {
        val renderer = FrameRenderer()
        val frame = bitmap()
        renderer.putPlaybackBitmap(3, frameId = 40L, revision = 7, width = 64, frame)
        assertSame(frame, renderer.playbackBitmap(3, 40L, 7, 64))
        renderer.clearPlaybackCache()
    }

    @Test
    fun staleFramesAreDroppedInsteadOfShown() {
        val renderer = FrameRenderer()
        val frame = bitmap()
        renderer.putPlaybackBitmap(3, frameId = 40L, revision = 7, width = 64, frame)

        // Revision changed.
        assertNull(renderer.playbackBitmap(3, 40L, 8, 64))
        assertTrue("stale bitmap is recycled", frame.isRecycled)

        val widthFrame = bitmap()
        renderer.putPlaybackBitmap(4, frameId = 41L, revision = 7, width = 64, widthFrame)
        assertNull(renderer.playbackBitmap(4, 41L, 7, 90))
        assertTrue(widthFrame.isRecycled)

        val idFrame = bitmap()
        renderer.putPlaybackBitmap(5, frameId = 42L, revision = 7, width = 64, idFrame)
        assertNull(renderer.playbackBitmap(5, 43L, 7, 64))
        assertTrue(idFrame.isRecycled)
        renderer.clearPlaybackCache()
    }

    @Test
    fun clearRecyclesEveryReadyFrame() {
        val renderer = FrameRenderer()
        val a = bitmap()
        val b = bitmap()
        renderer.putPlaybackBitmap(0, 1L, 1, 64, a)
        renderer.putPlaybackBitmap(1, 2L, 1, 64, b)
        renderer.clearPlaybackCache()
        assertTrue(a.isRecycled)
        assertTrue(b.isRecycled)
        assertNull(renderer.playbackBitmap(0, 1L, 1, 64))
    }

    @Test
    fun replacingAFrameRecyclesThePreviousBitmap() {
        val renderer = FrameRenderer()
        val first = bitmap()
        val second = bitmap()
        renderer.putPlaybackBitmap(2, 9L, 1, 64, first)
        renderer.putPlaybackBitmap(2, 9L, 2, 64, second)
        assertTrue(first.isRecycled)
        assertSame(second, renderer.playbackBitmap(2, 9L, 2, 64))
        assertEquals(8, second.width)
        renderer.clearPlaybackCache()
    }
}
