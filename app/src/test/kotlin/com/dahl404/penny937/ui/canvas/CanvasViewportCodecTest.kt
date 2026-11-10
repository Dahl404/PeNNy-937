package com.dahl404.penny937.ui.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanvasViewportCodecTest {

    @Test
    fun cameraRoundTripsExactly() {
        val state = CanvasViewport.State(
            scale = 1.2345678f,
            offsetX = -987.6543f,
            offsetY = 12345.678f,
            userZoomed = true
        )
        val decoded = CanvasViewportCodec.decode(CanvasViewportCodec.encode(state))
        assertEquals(state, decoded)
    }

    @Test
    fun fittedCameraStaysFitted() {
        val state = CanvasViewport.State(0.35f, 120f, 240f, userZoomed = false)
        assertEquals(state, CanvasViewportCodec.decode(CanvasViewportCodec.encode(state)))
    }

    @Test
    fun malformedOrForeignTextIsRejected() {
        assertNull(CanvasViewportCodec.decode(null))
        assertNull(CanvasViewportCodec.decode(""))
        assertNull(CanvasViewportCodec.decode("v1:1:2:3"))
        assertNull(CanvasViewportCodec.decode("v2:1:2:3:0"))
        assertNull(CanvasViewportCodec.decode("v1:nope:2:3:0"))
        assertNull(CanvasViewportCodec.decode("v1:1:2:3:maybe"))
    }
}
