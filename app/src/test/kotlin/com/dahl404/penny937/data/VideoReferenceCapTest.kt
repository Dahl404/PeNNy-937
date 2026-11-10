package com.dahl404.penny937.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The per-import frame cap is plain arithmetic, independent of MediaCodec. */
class VideoReferenceCapTest {

    @Test
    fun frameCountRoundsUpAndNeverReturnsZero() {
        assertEquals(1, VideoReferenceExtractor.frameCount(0, 12))
        assertEquals(1, VideoReferenceExtractor.frameCount(-50, 12))
        assertEquals(12, VideoReferenceExtractor.frameCount(1000, 12))
        assertEquals(13, VideoReferenceExtractor.frameCount(1001, 12))
        assertEquals(120, VideoReferenceExtractor.frameCount(10_000, 12))
    }

    @Test
    fun exactlyTheCapIsAllowed() {
        val total = VideoReferenceExtractor.frameCount(250_000, 12)
        assertEquals(VideoReferenceExtractor.MAX_FRAMES, total)
        VideoReferenceExtractor.ensureWithinCap(total, 12)
    }

    @Test
    fun oneFrameOverTheCapIsRejected() {
        val total = VideoReferenceExtractor.frameCount(250_001, 12)
        assertEquals(VideoReferenceExtractor.MAX_FRAMES + 1, total)
        try {
            VideoReferenceExtractor.ensureWithinCap(total, 12)
            fail("Expected VideoImportException")
        } catch (e: VideoImportException) {
            assertTrue(e.message!!.contains("3000"))
            assertTrue(e.message!!.contains("12 fps"))
        }
    }
}
