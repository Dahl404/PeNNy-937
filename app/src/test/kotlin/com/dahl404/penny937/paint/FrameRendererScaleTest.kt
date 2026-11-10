package com.dahl404.penny937.paint

import android.graphics.Color
import com.dahl404.penny937.model.Frame
import com.dahl404.penny937.model.Layer
import com.dahl404.penny937.model.Project
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
class FrameRendererScaleTest {

    private fun project(): Project {
        val layer = Layer(
            id = 2L,
            name = "Ink",
            strokes = listOf(
                Stroke(
                    Tool.PEN, Color.BLACK, 12f, 1f,
                    floatArrayOf(10f, 50f, 1f, 90f, 50f, 1f)
                )
            )
        )
        return Project(
            id = 1L,
            name = "scale",
            width = 100,
            height = 100,
            fps = 12,
            background = Color.WHITE,
            frames = listOf(Frame(id = 3L, layers = listOf(layer))),
            createdAt = 1,
            updatedAt = 1
        )
    }

    @Test
    fun pinnedLayerBitmapSurvivesCacheEviction() {
        val renderer = FrameRenderer()
        renderer.layerBudget = 1L // force eviction on every extra layer
        val active = project().frames[0].layers[0]
        val others = (1..3).map {
            Layer(
                id = 10L + it,
                name = "Layer $it",
                strokes = listOf(
                    Stroke(
                        Tool.PEN, Color.BLACK, 12f, 1f,
                        floatArrayOf(10f, 50f + it, 1f, 90f, 50f + it, 1f)
                    )
                )
            )
        }
        val project = project().copy(
            frames = listOf(Frame(id = 3L, layers = listOf(active) + others))
        )

        val pinned = renderer.editableLayerBitmap(project, active)
        renderer.pinLayer(active.id)
        for (layer in others) renderer.editableLayerBitmap(project, layer)

        assertFalse("the layer being drawn on must not be recycled", pinned.isRecycled)
        assertSame(pinned, renderer.editableLayerBitmap(project, active))

        renderer.unpinLayer(active.id)
        renderer.invalidateAll()
    }

    @Test
    fun scaleChangeWaitsForThePinnedStroke() {
        val project = project()
        val layer = project.frames[0].layers[0]
        val renderer = FrameRenderer()

        renderer.setInteractiveScale(0.5f)
        val half = renderer.editableLayerBitmap(project, layer)
        renderer.pinLayer(layer.id)
        renderer.setInteractiveScale(1f)

        assertFalse("a scale change during a stroke must not recycle its bitmap", half.isRecycled)
        assertEquals(50, renderer.editableLayerBitmap(project, layer).width)

        renderer.unpinLayer(layer.id)
        renderer.setInteractiveScale(1f)
        val full = renderer.editableLayerBitmap(project, layer)
        assertEquals(100, full.width)
        assertTrue(half.isRecycled)
        renderer.invalidateAll()
    }

    @Test
    fun interactiveLayerCacheFollowsTheDisplayScale() {
        val project = project()
        val layer = project.frames[0].layers[0]
        val renderer = FrameRenderer()

        renderer.setInteractiveScale(0.5f)
        val half = renderer.editableLayerBitmap(project, layer)
        assertEquals(50, half.width)
        assertEquals(50, half.height)
        assertNotEquals(Color.WHITE, half.getPixel(25, 25))

        renderer.setInteractiveScale(1f)
        val full = renderer.editableLayerBitmap(project, layer)
        assertEquals(100, full.width)
        assertEquals(100, full.height)
        assertNotEquals(Color.WHITE, full.getPixel(50, 50))
        renderer.invalidateAll()
    }

    @Test
    fun liveInkOverlayUsesTheSameDisplayScale() {
        val project = project()
        val renderer = FrameRenderer()

        renderer.setInteractiveScale(0.25f)
        val quarter = renderer.inkOverlay(project)
        assertEquals(25, quarter.width)
        assertEquals(25, quarter.height)

        renderer.setInteractiveScale(0.75f)
        val threeQuarters = renderer.inkOverlay(project)
        assertEquals(75, threeQuarters.width)
        assertEquals(75, threeQuarters.height)
        renderer.invalidateAll()
    }

    @Test
    fun cacheScaleIsClampedToAUsefulRange() {
        val project = project()
        val layer = project.frames[0].layers[0]
        val renderer = FrameRenderer()

        renderer.setInteractiveScale(0f)
        val tiny = renderer.editableLayerBitmap(project, layer)
        assertEquals(6, tiny.width) // 100 * 1/16, rounded

        renderer.setInteractiveScale(4f)
        val capped = renderer.editableLayerBitmap(project, layer)
        assertEquals(100, capped.width)
        renderer.invalidateAll()
    }
}
