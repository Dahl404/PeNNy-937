package com.dahl404.penny937.ui.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasViewportTest {

    private fun viewport(width: Int = 1000, height: Int = 1000): CanvasViewport =
        CanvasViewport(
            viewWidth = { width },
            viewHeight = { height },
            fitPaddingPx = { 0f },
            edgeMarginPx = { 0f },
            onChange = { }
        )

    @Test
    fun fitCentresThePaperAndSetsTheFitScale() {
        val viewport = viewport(1000, 1000)
        viewport.updateProject(100, 200)
        viewport.fitToPaper()
        assertEquals(5f, viewport.fitScale, 0.001f)
        assertEquals(5f, viewport.scale, 0.001f)
        assertEquals(250f, viewport.offsetX, 0.001f)
        assertEquals(0f, viewport.offsetY, 0.001f)
    }

    @Test
    fun zoomKeepsTheWorldPointUnderTheFocus() {
        val viewport = viewport(1000, 1000)
        viewport.updateProject(100, 200)
        viewport.fitToPaper()
        viewport.zoomTo(2f, focusX = 500f, focusY = 500f, worldX = 50f, worldY = 50f)
        assertEquals(2f, viewport.scale, 0.001f)
        // The paper is smaller than the viewport at this scale, so it is centred.
        assertEquals(400f, viewport.offsetX, 0.001f)
        assertEquals(300f, viewport.offsetY, 0.001f)
        assertTrue(viewport.userZoomed)
    }

    @Test
    fun panIsIgnoredWhenThePaperFitsAndClampedWhenItDoesNot() {
        val viewport = viewport(1000, 1000)
        viewport.updateProject(100, 200)
        viewport.fitToPaper()
        viewport.panBy(100f, 100f)
        assertEquals("paper is centred while it fits", 250f, viewport.offsetX, 0.001f)

        viewport.zoomTo(20f, 500f, 500f, 50f, 50f)
        val beforeX = viewport.offsetX
        val beforeY = viewport.offsetY
        viewport.panBy(100f, 200f)
        assertEquals(beforeX + 100f, viewport.offsetX, 0.001f)
        assertEquals(beforeY + 200f, viewport.offsetY, 0.001f)
    }

    @Test
    fun aSizeChangeRefitsOnlyWhenTheUserHasNotZoomed() {
        val viewport = viewport(1000, 1000)
        viewport.updateProject(100, 200)
        viewport.onSizeChanged(1000, 1000, 0, 0)
        assertEquals(5f, viewport.scale, 0.001f)
        assertFalse(viewport.userZoomed)

        viewport.zoomTo(2f, 500f, 500f, 50f, 50f)
        viewport.onSizeChanged(500, 500, 1000, 1000)
        assertEquals("user zoom is preserved", 2f, viewport.scale, 0.001f)
    }
}
