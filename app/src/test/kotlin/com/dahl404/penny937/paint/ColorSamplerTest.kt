package com.dahl404.penny937.paint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorSamplerTest {

    private fun patch(vararg rows: IntArray): Pair<IntArray, Int> {
        val w = rows[0].size
        val h = rows.size
        val pixels = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) pixels[y * w + x] = rows[y][x]
        return pixels to w
    }

    @Test
    fun samplesTheCenterPixel() {
        val (pixels, w) = patch(
            intArrayOf(0, 0, 0),
            intArrayOf(0, 0xFFFF3B30.toInt(), 0),
            intArrayOf(0, 0, 0)
        )
        assertEquals(0xFFFF3B30.toInt(), ColorSampler.nearestOpaqueColor(pixels, w, 3, 1, 1))
    }

    @Test
    fun findsTheNearestVisiblePixelWhenTheCenterIsTransparent() {
        val (pixels, w) = patch(
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0xFF007AFF.toInt(), 0),
            intArrayOf(0, 0, 0, 0)
        )
        assertEquals(0xFF007AFF.toInt(), ColorSampler.nearestOpaqueColor(pixels, w, 3, 1, 1))
    }

    @Test
    fun prefersTheCloserOfTwoVisiblePixels() {
        val (pixels, w) = patch(
            intArrayOf(0xFF00FF00.toInt(), 0, 0, 0, 0xFFFF0000.toInt())
        )
        assertEquals(0xFF00FF00.toInt(), ColorSampler.nearestOpaqueColor(pixels, w, 1, 0, 0))
        assertEquals(0xFFFF0000.toInt(), ColorSampler.nearestOpaqueColor(pixels, w, 1, 4, 0))
    }

    @Test
    fun ignoresAlmostTransparentEdgePixelsAndReturnsNullWhenEmpty() {
        val (pixels, w) = patch(
            intArrayOf(0x07FF0000, 0),
            intArrayOf(0, 0)
        )
        assertNull(ColorSampler.nearestOpaqueColor(pixels, w, 2, 0, 0))
    }

    @Test
    fun normalizesPickedColorToOpaque() {
        val (pixels, w) = patch(intArrayOf(0x40AF52DE))
        assertEquals(0xFFAF52DE.toInt(), ColorSampler.nearestOpaqueColor(pixels, w, 1, 0, 0))
    }
}
