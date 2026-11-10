package com.dahl404.penny937.paint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarpTest {

    @Test
    fun falloffIsFullAtTheCentreAndZeroAtTheEdge() {
        assertEquals(1f, Warp.falloff(0f, 50f), 0.0001f)
        assertEquals(0f, Warp.falloff(50f, 50f), 0.0001f)
        assertEquals(0f, Warp.falloff(80f, 50f), 0.0001f)
        assertTrue(Warp.falloff(10f, 50f) > Warp.falloff(30f, 50f))
        assertTrue(Warp.falloff(30f, 50f) > Warp.falloff(45f, 50f))
    }

    @Test
    fun theCentreFollowsTheFingerAndTheEdgeStaysPut() {
        // x, y, pressure triples
        val points = floatArrayOf(
            100f, 100f, 0.7f,   // centre
            130f, 100f, 0.5f,   // inside, partway out
            200f, 100f, 0.9f    // outside the radius
        )
        val moved = Warp.apply(
            points,
            centerX = 100f,
            centerY = 100f,
            dx = 20f,
            dy = -10f,
            radius = 50f
        )
        assertTrue(moved)
        assertEquals(120f, points[0], 0.0001f)
        assertEquals(90f, points[1], 0.0001f)
        assertEquals(0.7f, points[2], 0.0001f)
        // 30/50 of the way out: moved, but less than the centre.
        val partial = (points[3] - 130f) / 20f
        assertTrue("falloff should taper: $partial", partial > 0f && partial < 1f)
        assertEquals(100f - 10f * Warp.falloff(30f, 50f), points[4], 0.0001f)
        assertEquals(0.5f, points[5], 0.0001f)
        // Outside the brush is untouched.
        assertEquals(200f, points[6], 0.0001f)
        assertEquals(100f, points[7], 0.0001f)
    }

    @Test
    fun aStrokeOutsideTheBrushIsNotTouched() {
        val points = floatArrayOf(500f, 500f, 1f, 600f, 600f, 1f)
        val moved = Warp.apply(points, 0f, 0f, 10f, 10f, 32f)
        assertFalse(moved)
        assertEquals(500f, points[0], 0.0001f)
        assertEquals(600f, points[4], 0.0001f)
    }

    @Test
    fun zeroDeltaDoesNothing() {
        val points = floatArrayOf(10f, 10f, 1f)
        assertFalse(Warp.apply(points, 10f, 10f, 0f, 0f, 40f))
        assertEquals(10f, points[0], 0.0001f)
    }
}
