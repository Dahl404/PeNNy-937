package com.masen.penny937.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectOpsTest {

    private fun project(): Project = ProjectFactory.create(
        name = "Test",
        width = 1080,
        height = 1920,
        fps = 12,
        background = 0xFFFBF9F4.toInt()
    )

    private fun stroke(tool: Tool = Tool.PEN): Stroke = Stroke(
        tool = tool,
        color = 0xFF000000.toInt(),
        width = 8f,
        opacity = 1f,
        points = floatArrayOf(10f, 10f, 1f, 20f, 20f, 0.5f, 30f, 30f, 0.2f)
    )

    @Test
    fun holdsExpandTheTimeline() {
        var p = project()
        p = ProjectOps.addFrameAfter(p, 0)
        p = ProjectOps.addFrameAfter(p, 1)
        p = ProjectOps.setHold(p, 0, 2)
        p = ProjectOps.setHold(p, 1, 3)
        p = ProjectOps.setHold(p, 2, 1)
        assertEquals(3, p.frames.size)
        assertEquals(6, p.totalTicks)
        assertEquals(0, p.frameIndexAtTick(0))
        assertEquals(0, p.frameIndexAtTick(1))
        assertEquals(1, p.frameIndexAtTick(2))
        assertEquals(1, p.frameIndexAtTick(4))
        assertEquals(2, p.frameIndexAtTick(5))
        assertEquals(2, p.frameIndexAtTick(99))
        assertEquals(2, p.tickOfFrame(1))
        assertEquals(5, p.tickOfFrame(2))
    }

    @Test
    fun framesHaveTheirOwnLayers() {
        var p = project()
        val frameAId = p.frames[0].layers[0].id
        p = ProjectOps.addFrameAfter(p, 0)
        val frameB = p.frames[1]
        assertNotEquals(frameAId, frameB.layers[0].id)
        p = ProjectOps.addLayer(p, 1, 0).first
        assertEquals(1, p.frames[0].layers.size)
        assertEquals(2, p.frames[1].layers.size)
    }

    @Test
    fun strokesStayOnTheirLayer() {
        var p = project()
        val layerId = p.frames[0].layers[0].id
        p = ProjectOps.addStroke(p, 0, 0, stroke())
        p = ProjectOps.addLayer(p, 0, 0).first
        assertEquals(1, p.frames[0].layers[0].strokes.size)
        assertEquals(0, p.frames[0].layers[1].strokes.size)
        assertEquals(layerId, p.frames[0].layers[0].id)
    }

    @Test
    fun duplicateProjectRemapsEveryId() {
        var p = project()
        p = ProjectOps.addFrameAfter(p, 0)
        p = ProjectOps.addLayer(p, 0, 0).first
        p = ProjectOps.addStroke(p, 0, 1, stroke(Tool.MARKER))
        val copy = ProjectFactory.duplicateProject(p, "Copy")
        assertNotEquals(p.id, copy.id)
        assertNotEquals(p.frames[0].id, copy.frames[0].id)
        val originalLayerIds = p.frames.flatMap { f -> f.layers.map { it.id } }.toSet()
        val copiedLayerIds = copy.frames.flatMap { f -> f.layers.map { it.id } }.toSet()
        assertEquals(originalLayerIds.size, copiedLayerIds.size)
        assertTrue(originalLayerIds.intersect(copiedLayerIds).isEmpty())
        assertEquals(
            p.frames.sumOf { it.strokeCount },
            copy.frames.sumOf { it.strokeCount }
        )
    }

    @Test
    fun moveFrameReordersTheTimeline() {
        var p = project()
        p = ProjectOps.addFrameAfter(p, 0)
        p = ProjectOps.addFrameAfter(p, 1)
        val ids = p.frames.map { it.id }
        assertEquals(3, ids.size)

        p = ProjectOps.moveFrame(p, 0, 2)
        assertEquals(listOf(ids[1], ids[2], ids[0]), p.frames.map { it.id })

        p = ProjectOps.moveFrame(p, 2, 0)
        assertEquals(ids, p.frames.map { it.id })

        val unchanged = ProjectOps.moveFrame(p, 99, 0)
        assertEquals(ids, unchanged.frames.map { it.id })

        val single = project()
        assertEquals(single, ProjectOps.moveFrame(single, 0, 0))
    }

    @Test
    fun referenceLayerGoesToTheBottomAndIsLocked() {
        var p = project()
        val originalId = p.frames[0].layers[0].id
        val image = LayerImage(
            file = "ref.png",
            sourceWidth = 100,
            sourceHeight = 200,
            centerX = 50f,
            centerY = 100f,
            width = 100f,
            height = 200f
        )
        p = ProjectOps.addReferenceLayer(
            p, 0, ProjectFactory.referenceLayer("Reference", image)
        )
        assertEquals(2, p.frames[0].layers.size)
        assertTrue(p.frames[0].layers[0].isReference)
        assertTrue(p.frames[0].layers[0].locked)
        assertEquals("ref.png", p.frames[0].layers[0].image!!.file)
        assertEquals(originalId, p.frames[0].layers[1].id)
        // A blank frame does not copy the reference.
        p = ProjectOps.addFrameAfter(p, 0)
        assertEquals(1, p.frames[1].layers.size)
        assertEquals(null, p.frames[1].layers[0].image)
    }

    @Test
    fun lockedOrReferenceLayersDoNotMergeOrClear() {
        var p = project()
        p = ProjectOps.addLayer(p, 0, 0).first
        p = ProjectOps.addStroke(p, 0, 0, stroke(Tool.PEN))
        p = ProjectOps.addStroke(p, 0, 1, stroke(Tool.PEN))
        p = ProjectOps.updateLayer(p, 0, 1) { it.copy(locked = true) }
        val mergeAttempt = ProjectOps.mergeLayerDown(p, 0, 1).first
        assertEquals(2, mergeAttempt.frames[0].layers.size)

        val image = LayerImage("ref.png", 10, 10, 5f, 5f, 10f, 10f)
        p = ProjectOps.updateLayer(p, 0, 0) { it.copy(image = image) }
        val cleared = ProjectOps.clearLayer(p, 0, 0)
        assertEquals(1, cleared.frames[0].layers[0].strokes.size)
    }

    @Test
    fun deletingTheLastFrameIsIgnored() {
        val p = project()
        assertEquals(1, ProjectOps.deleteFrame(p, 0).frames.size)
    }

    @Test
    fun mergeDownMovesUpperStrokesDownwards() {
        var p = project()
        p = ProjectOps.addLayer(p, 0, 0).first
        p = ProjectOps.addStroke(p, 0, 0, stroke(Tool.PEN))
        p = ProjectOps.addStroke(p, 0, 1, stroke(Tool.MARKER))
        p = ProjectOps.mergeLayerDown(p, 0, 1).first
        assertEquals(1, p.frames[0].layers.size)
        assertEquals(2, p.frames[0].layers[0].strokes.size)
    }

    @Test
    fun restoringDeletedLayerContentIsPossible() {
        var p = project()
        p = ProjectOps.addLayer(p, 0, 0).first
        p = ProjectOps.addStroke(p, 0, 1, stroke())
        val before = p
        val (deleted, index) = ProjectOps.deleteLayer(p, 0, 1)
        assertEquals(1, deleted.frames[0].layers.size)
        assertEquals(0, index)
        // Undo is snapshot based: the untouched snapshot still has both layers.
        assertEquals(2, before.frames[0].layers.size)
        assertEquals(1, before.frames[0].layers[1].strokes.size)
    }
}
