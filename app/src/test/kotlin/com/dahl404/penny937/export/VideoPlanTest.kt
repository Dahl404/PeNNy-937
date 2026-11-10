package com.dahl404.penny937.export

import com.dahl404.penny937.model.Frame
import com.dahl404.penny937.model.Layer
import com.dahl404.penny937.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/**
 * The MP4 encoder itself can only run on a device, but everything it depends on —
 * frame timing, encoder geometry, bitrate and the ARGB→YUV 4:2:0 conversion — is plain
 * arithmetic and is covered here.
 */
class VideoPlanTest {

    private fun project(
        width: Int = 1080,
        height: Int = 1920,
        fps: Int = 12,
        holds: List<Int> = listOf(1, 1, 1)
    ): Project = Project(
        id = 1,
        name = "video",
        width = width,
        height = height,
        fps = fps,
        background = 0xFFFBF9F4.toInt(),
        frames = holds.mapIndexed { index, hold ->
            Frame(id = 10L + index, hold = hold, layers = listOf(Layer(id = 100L + index, name = "L")))
        },
        createdAt = 1,
        updatedAt = 1
    )

    @Test
    fun dimensionsKeepAspectAndStayEven() {
        val p = project(width = 1080, height = 1920)
        assertEquals(720 to 1280, VideoPlan.dimensions(p, 720))
        assertEquals(480 to 854, VideoPlan.dimensions(p, 480))
        assertEquals(1080 to 1920, VideoPlan.dimensions(p, 4000))
    }

    @Test
    fun dimensionsRoundOddSizesToEven() {
        val p = project(width = 1081, height = 721)
        val (w, h) = VideoPlan.dimensions(p, 1081)
        assertEquals(0, w % 2)
        assertEquals(0, h % 2)
    }

    @Test
    fun dimensionsNeverGoBelowTheEncoderMinimum() {
        val p = project(width = 4000, height = 32)
        val (w, h) = VideoPlan.dimensions(p, 16)
        assertTrue(w >= 16)
        assertTrue(h >= 16)
        assertEquals(0, w % 2)
        assertEquals(0, h % 2)
    }

    @Test
    fun bitrateScalesWithPixelsAndIsClamped() {
        val small = VideoPlan.bitrate(160, 120, 6)
        val mid = VideoPlan.bitrate(720, 1280, 24)
        val huge = VideoPlan.bitrate(2160, 3840, 60)
        assertTrue("small bitrate is clamped up", small >= 800_000)
        assertTrue("bitrate grows with area", mid > small)
        assertTrue("bitrate is capped", huge <= 24_000_000)
        assertEquals(720 * 1280 * 24 * 12 / 100, mid)
    }

    @Test
    fun presentationTimesFollowFrameHolds() {
        val p = project(fps = 12, holds = listOf(1, 3, 2))
        assertEquals(0L, VideoPlan.presentationTimeUs(p, 0))
        assertEquals(83_333L, VideoPlan.presentationTimeUs(p, 1))
        assertEquals(333_333L, VideoPlan.presentationTimeUs(p, 2))
        assertEquals(500_000L, VideoPlan.durationUs(p))
    }

    // ---------------------------------------------------------------- YUV

    private class Planes(val y: ByteBuffer, val u: ByteBuffer, val v: ByteBuffer, val size: Int)

    private fun planes(width: Int, height: Int, pad: Int = 0): Planes {
        val cw = (width + 1) / 2
        val ch = (height + 1) / 2
        return Planes(
            ByteBuffer.allocate((width + pad) * height),
            ByteBuffer.allocate((cw + pad) * ch),
            ByteBuffer.allocate((cw + pad) * ch),
            pad
        )
    }

    private fun convert(pixels: IntArray, width: Int, height: Int, pad: Int = 0): Planes {
        val buffers = planes(width, height, pad)
        Yuv420.fromArgb(
            pixels, width, height,
            buffers.y, width + pad, 1,
            buffers.u, (width + 1) / 2 + pad, 1,
            buffers.v, (width + 1) / 2 + pad, 1
        )
        return buffers
    }

    private fun ByteBuffer.at(index: Int): Int = get(index).toInt() and 0xFF

    @Test
    fun whiteMapsToFullLumaAndNeutralChroma() {
        val p = convert(IntArray(4 * 4) { 0xFFFFFFFF.toInt() }, 4, 4)
        assertEquals(235, p.y.at(0))
        assertEquals(235, p.y.at(15))
        assertEquals(128, p.u.at(0))
        assertEquals(128, p.v.at(0))
    }

    @Test
    fun blackMapsToStudioSwingBlack() {
        val p = convert(IntArray(4 * 4) { 0xFF000000.toInt() }, 4, 4)
        assertEquals(16, p.y.at(0))
        assertEquals(128, p.u.at(0))
        assertEquals(128, p.v.at(0))
    }

    @Test
    fun redMapsToExpectedYuv() {
        val p = convert(IntArray(2 * 2) { 0xFFFF0000.toInt() }, 2, 2)
        // BT.601: Y = 81, U = 90, V = 240 for pure red (studio swing).
        assertEquals(82, p.y.at(0))
        assertEquals(90, p.u.at(0))
        assertEquals(240, p.v.at(0))
    }

    @Test
    fun chromaAveragesEachBlock() {
        // Left two columns red, right two blue: one chroma sample per column pair.
        val pixels = intArrayOf(
            0xFFFF0000.toInt(), 0xFFFF0000.toInt(), 0xFF0000FF.toInt(), 0xFF0000FF.toInt(),
            0xFFFF0000.toInt(), 0xFFFF0000.toInt(), 0xFF0000FF.toInt(), 0xFF0000FF.toInt()
        )
        val p = convert(pixels, 4, 2)
        assertTrue("red block should be red-ish in chroma", p.v.at(0) > 180)
        assertTrue("blue block should be blue-ish in chroma", p.u.at(1) > 150)
    }

    @Test
    fun rowStridesAreHonoured() {
        val width = 6
        val height = 4
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }
        val pad = 3
        val p = convert(pixels, width, height, pad)
        // Every luma row starts on the stride, and the padding bytes must stay zero.
        for (row in 0 until height) {
            assertEquals("row $row start", 235, p.y.at(row * (width + pad)))
            for (col in width until width + pad) {
                assertEquals("row $row pad $col", 0, p.y.at(row * (width + pad) + col))
            }
        }
    }

    @Test
    fun oddSizesReplicateTheLastColumnAndRow() {
        val pixels = IntArray(3 * 3) { 0xFF808080.toInt() }
        val p = convert(pixels, 3, 3)
        val cw = 2
        val ch = 2
        for (i in 0 until cw * ch) {
            assertEquals(128, p.u.at(i))
            assertEquals(128, p.v.at(i))
        }
    }

    @Test
    fun pixelStrideIsHonouredForSemiPlanarChroma() {
        // Some codecs hand out chroma with pixelStride 2 (interleaved UV): writes must
        // skip every other byte instead of packing them.
        val width = 4
        val height = 2
        val y = ByteBuffer.allocate(width * height)
        val u = ByteBuffer.allocate(4)
        val v = ByteBuffer.allocate(4)
        Yuv420.fromArgb(
            IntArray(width * height) { 0xFF3366CC.toInt() }, width, height,
            y, width, 1,
            u, 4, 2,
            v, 4, 2
        )
        // Two chroma samples (columns 0 and 2) per plane; bytes 1 and 3 stay zero.
        assertTrue("first U sample written", u.get(0).toInt() and 0xFF != 0)
        assertTrue("second U sample written", u.get(2).toInt() and 0xFF != 0)
        assertEquals("U gap must be untouched", 0, u.get(1).toInt() and 0xFF)
        assertEquals("U gap must be untouched", 0, u.get(3).toInt() and 0xFF)
        assertTrue("first V sample written", v.get(0).toInt() and 0xFF != 0)
        assertEquals("V gap must be untouched", 0, v.get(1).toInt() and 0xFF)
    }
}
