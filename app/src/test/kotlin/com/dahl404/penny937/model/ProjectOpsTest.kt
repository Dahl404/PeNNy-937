package com.dahl404.penny937.model

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

    private fun stroke(tool: Tool = Tool.PEN, opacity: Float = 1f): Stroke = Stroke(
        tool = tool,
        color = 0xFF000000.toInt(),
        width = 8f,
        opacity = opacity,
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
    fun translateLayerMovesItsStrokesAndLeavesOtherLayersAlone() {
        var p = project()
        p = ProjectOps.addLayer(p, 0, 0).first
        val original = stroke()
        p = ProjectOps.addStroke(p, 0, 0, original)
        p = ProjectOps.addStroke(p, 0, 1, stroke(Tool.MARKER))
        val image = LayerImage("ref.png", 10, 10, 50f, 60f, 10f, 10f)
        p = ProjectOps.updateLayer(p, 0, 1) { it.copy(image = image) }

        val moved = ProjectOps.translateLayer(p, 0, 0, 12f, -7f)
        val movedStroke = moved.frames[0].layers[0].strokes[0]
        assertEquals(22f, movedStroke.pointX(0), 0.001f)
        assertEquals(3f, movedStroke.pointY(0), 0.001f)
        assertEquals(32f, movedStroke.pointX(1), 0.001f)
        assertEquals(13f, movedStroke.pointY(1), 0.001f)
        assertEquals(0.5f, movedStroke.pointPressure(1), 0.001f)

        // The original snapshot and the other layer stay put.
        assertEquals(10f, p.frames[0].layers[0].strokes[0].pointX(0), 0.001f)
        val other = moved.frames[0].layers[1]
        assertEquals(10f, other.strokes[0].pointX(0), 0.001f)
        assertEquals(50f, other.image!!.centerX, 0.001f)

        val shiftedReference = ProjectOps.translateLayer(moved, 0, 1, -5f, 6f)
        assertEquals(45f, shiftedReference.frames[0].layers[1].image!!.centerX, 0.001f)
        assertEquals(66f, shiftedReference.frames[0].layers[1].image!!.centerY, 0.001f)
    }

    @Test
    fun transformLayerRotatesStrokesAndReferenceAroundThePivot() {
        var p = project()
        p = ProjectOps.addLayer(p, 0, 0).first
        p = ProjectOps.addStroke(p, 0, 0, stroke())
        val image = LayerImage("ref.png", 10, 10, 50f, 60f, 10f, 10f)
        p = ProjectOps.updateLayer(p, 0, 1) { it.copy(image = image) }

        // 90 degrees clockwise around the origin, then translate by (5, -5).
        val rotated = ProjectOps.transformLayer(p, 0, 0, 5f, -5f, 90f, 0f, 0f)
        val s = rotated.frames[0].layers[0].strokes[0]
        // (10, 10) -> (-10, 10) + (5, -5) = (-5, 5)
        assertEquals(-5f, s.pointX(0), 0.001f)
        assertEquals(5f, s.pointY(0), 0.001f)
        assertEquals(0.5f, s.pointPressure(1), 0.001f)
        // Other layers, and the untouched snapshot, stay put.
        assertEquals(50f, rotated.frames[0].layers[1].image!!.centerX, 0.001f)
        assertEquals(10f, p.frames[0].layers[0].strokes[0].pointX(0), 0.001f)

        // Rotating around the content centre flips it in place.
        val flipped = ProjectOps.transformLayer(p, 0, 0, 0f, 0f, 180f, 20f, 20f)
        val f = flipped.frames[0].layers[0].strokes[0]
        assertEquals(30f, f.pointX(0), 0.001f)
        assertEquals(30f, f.pointY(0), 0.001f)
        assertEquals(10f, f.pointX(2), 0.001f)
        assertEquals(10f, f.pointY(2), 0.001f)

        // A reference rotates with its layer.
        val reference = ProjectOps.transformLayer(p, 0, 1, 0f, 0f, 90f, 0f, 0f)
        val ref = reference.frames[0].layers[1].image!!
        assertEquals(-60f, ref.centerX, 0.001f)
        assertEquals(50f, ref.centerY, 0.001f)
        assertEquals(90f, ref.rotation, 0.001f)
    }

    @Test
    fun movingFrameReordersTheTimeline() {
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
    fun lockedLayersMergeAndFlattenedInkCanBeCleared() {
        var p = project()
        p = ProjectOps.addLayer(p, 0, 0).first
        p = ProjectOps.addStroke(p, 0, 0, stroke(Tool.PEN))
        p = ProjectOps.addStroke(p, 0, 1, stroke(Tool.PEN))
        p = ProjectOps.updateLayer(p, 0, 1) { it.copy(locked = true) }
        val merged = ProjectOps.mergeLayerDown(p, 0, 1).first
        assertEquals(1, merged.frames[0].layers.size)
        assertEquals(2, merged.frames[0].layers[0].strokes.size)

        // Raster ink from a merge is ordinary layer content: Clear removes it.
        val image = LayerImage("merged.png", 10, 10, 5f, 5f, 10f, 10f, inOutput = true)
        p = ProjectOps.updateLayer(merged, 0, 0) {
            it.copy(image = image, flattened = true, strokes = listOf(stroke()))
        }
        val cleared = ProjectOps.clearLayer(p, 0, 0)
        assertEquals(0, cleared.frames[0].layers[0].strokes.size)
        assertEquals(null, cleared.frames[0].layers[0].image)
        assertEquals(false, cleared.frames[0].layers[0].flattened)

        // References still cannot be cleared or drawn over.
        p = ProjectOps.addReferenceLayer(
            p, 0, ProjectFactory.referenceLayer("Reference", image)
        )
        val afterReferenceClear = ProjectOps.clearLayer(p, 0, 0)
        assertEquals(image, afterReferenceClear.frames[0].layers[0].image)
    }

    private fun videoImage(index: Int) = LayerImage(
        file = "video-$index.jpg",
        sourceWidth = 1600,
        sourceHeight = 900,
        centerX = 540f,
        centerY = 960f,
        width = 1080f,
        height = 607.5f
    )

    @Test
    fun videoReferencesFillFramesAndExtendTheTimeline() {
        var p = project()
        p = ProjectOps.addStroke(p, 0, 0, stroke())
        val result = ProjectOps.insertVideoReferenceFrames(
            p, 0, (0 until 3).map { videoImage(it) }, "Video reference"
        )
        assertEquals(3, result.project.frames.size)
        assertEquals(0, result.startFrame)
        assertEquals(2, result.endFrame)
        assertEquals(1, result.selectedLayer)
        for (i in 0..2) {
            val frame = result.project.frames[i]
            assertEquals(2, frame.layers.size)
            assertTrue(frame.layers[0].isReference)
            assertTrue(frame.layers[0].locked)
            assertEquals("video-$i.jpg", frame.layers[0].image!!.file)
            assertEquals(false, frame.layers[1].isReference)
        }
        assertEquals(1, result.project.frames[0].layers[1].strokes.size)
    }

    @Test
    fun videoReferencesAppendFromTheCurrentFrameAndLeaveEarlierFramesAlone() {
        var p = project()
        p = ProjectOps.addFrameAfter(p, 0)
        val before = p.frames[0]
        val result = ProjectOps.insertVideoReferenceFrames(
            p, 2, (0 until 2).map { videoImage(it) }, "Video reference"
        )
        assertEquals(4, result.project.frames.size)
        assertEquals(2, result.startFrame)
        assertEquals(3, result.endFrame)
        assertEquals(before.layers.size, result.project.frames[0].layers.size)
        assertTrue(result.project.frames[2].layers[0].isReference)
        assertTrue(result.project.frames[3].layers[0].isReference)
    }

    @Test
    fun videoOnAReferenceOnlyFrameCreatesADrawingLayer() {
        var p = project()
        p = ProjectOps.addReferenceLayer(
            p, 0, ProjectFactory.referenceLayer("Reference", videoImage(9))
        )
        // Remove the original drawing layer, leaving only a reference.
        p = ProjectOps.deleteLayer(p, 0, 1).first
        assertEquals(1, p.frames[0].layers.size)
        assertTrue(p.frames[0].layers[0].isReference)

        val result = ProjectOps.insertVideoReferenceFrames(
            p, 0, listOf(videoImage(0)), "Video reference"
        )
        val layers = result.project.frames[0].layers
        assertEquals(3, layers.size)
        assertTrue(layers[0].isReference)
        assertTrue(layers[1].isReference)
        assertEquals(false, layers[2].isReference)
        assertEquals(2, result.selectedLayer)
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
    fun mergeDownBakesBothLayerOpacitiesIntoTheStrokes() {
        var p = project()
        p = ProjectOps.addLayer(p, 0, 0).first
        p = ProjectOps.addStroke(p, 0, 0, stroke(Tool.PEN, opacity = 0.8f))
        p = ProjectOps.addStroke(p, 0, 1, stroke(Tool.MARKER, opacity = 0.5f))
        p = ProjectOps.updateLayer(p, 0, 0) { it.copy(opacity = 0.5f) }
        p = ProjectOps.updateLayer(p, 0, 1) { it.copy(opacity = 0.4f) }
        val before = p

        p = ProjectOps.mergeLayerDown(p, 0, 1).first
        val merged = p.frames[0].layers.single()
        assertEquals(1f, merged.opacity, 0.0001f)
        assertEquals(true, merged.visible)
        assertEquals(0.4f, merged.strokes[0].opacity, 0.0001f)
        assertEquals(0.2f, merged.strokes[1].opacity, 0.0001f)
        // The untouched project snapshot still has both layers.
        assertEquals(2, before.frames[0].layers.size)
    }

    @Test
    fun mergeDownResolvesVisibilityByAppearance() {
        var p = project()
        p = ProjectOps.addLayer(p, 0, 0).first
        p = ProjectOps.addStroke(p, 0, 0, stroke())
        p = ProjectOps.addStroke(p, 0, 1, stroke())
        p = ProjectOps.updateLayer(p, 0, 0) { it.copy(visible = false) }
        p = ProjectOps.updateLayer(p, 0, 1) { it.copy(visible = false) }
        assertTrue(ProjectOps.canMergeLayerDown(p.frameAt(0), 1))
        val merged = ProjectOps.mergeLayerDown(p, 0, 1).first.frames[0].layers.single()
        assertEquals(false, merged.visible)
        assertEquals(2, merged.strokes.size)

        // Hidden lower + visible upper: only the upper layer was on screen, so the
        // merge result is the upper content, still visible.
        var mixed = project()
        mixed = ProjectOps.addLayer(mixed, 0, 0).first
        mixed = ProjectOps.addStroke(mixed, 0, 0, stroke(Tool.PEN))
        mixed = ProjectOps.addStroke(mixed, 0, 1, stroke(Tool.MARKER))
        mixed = ProjectOps.updateLayer(mixed, 0, 0) { it.copy(visible = false) }
        assertTrue(ProjectOps.canMergeLayerDown(mixed.frameAt(0), 1))
        val upperOnly = ProjectOps.mergeLayerDown(mixed, 0, 1).first.frames[0].layers.single()
        assertEquals(true, upperOnly.visible)
        assertEquals(Tool.MARKER, upperOnly.strokes.single().tool)

        // Visible lower + hidden upper: the upper layer contributed nothing, so it
        // is dropped and the lower layer is untouched.
        var hiddenUpper = project()
        hiddenUpper = ProjectOps.addLayer(hiddenUpper, 0, 0).first
        hiddenUpper = ProjectOps.addStroke(hiddenUpper, 0, 0, stroke(Tool.PEN))
        hiddenUpper = ProjectOps.addStroke(hiddenUpper, 0, 1, stroke(Tool.MARKER))
        hiddenUpper = ProjectOps.updateLayer(hiddenUpper, 0, 1) { it.copy(visible = false) }
        val lowerOnly = ProjectOps.mergeLayerDown(hiddenUpper, 0, 1).first.frames[0].layers.single()
        assertEquals(Tool.PEN, lowerOnly.strokes.single().tool)
    }

    @Test
    fun mergeDownFlattensUpperErasersAndKeepsLowerErasers() {
        var p = project()
        p = ProjectOps.addLayer(p, 0, 0).first
        p = ProjectOps.addStroke(p, 0, 0, stroke(Tool.ERASER, opacity = 1f))
        p = ProjectOps.addStroke(p, 0, 1, stroke(Tool.ERASER, opacity = 1f))
        assertTrue(ProjectOps.canMergeLayerDown(p.frameAt(0), 1))
        assertTrue(ProjectOps.mergeNeedsFlattening(p.frameAt(0).layers[0], p.frameAt(0).layers[1]))

        val flattenedImage = LayerImage("merged.png", 1080, 1920, 540f, 960f, 1080f, 1920f, inOutput = true)
        val merged = ProjectOps.mergeLayerDown(p, 0, 1, flattenedImage).first.frames[0].layers.single()
        assertEquals(true, merged.flattened)
        assertEquals(flattenedImage, merged.image)
        assertEquals(0, merged.strokes.size)
        assertEquals(1f, merged.opacity, 0.0001f)

        // A lower eraser already only affects the lower layer's earlier ink, so it
        // stays valid after a vector merge; an upper eraser needs raster ink above.
        val lowerOnly = ProjectOps.deleteLayer(p, 0, 1).first
        var withoutUpper = ProjectOps.addStroke(lowerOnly, 0, 0, stroke(Tool.PEN))
        withoutUpper = ProjectOps.addLayer(withoutUpper, 0, 0).first
        withoutUpper = ProjectOps.addStroke(withoutUpper, 0, 1, stroke(Tool.PEN))
        withoutUpper = ProjectOps.updateLayer(withoutUpper, 0, 0) { it.copy(opacity = 0.5f) }
        assertTrue(ProjectOps.canMergeLayerDown(withoutUpper.frameAt(0), 1))
        assertEquals(false, ProjectOps.mergeNeedsFlattening(
            withoutUpper.frameAt(0).layers[0], withoutUpper.frameAt(0).layers[1]
        ))
        val vectorMerged = ProjectOps.mergeLayerDown(withoutUpper, 0, 1).first.frames[0].layers.single()
        assertEquals(Tool.ERASER, vectorMerged.strokes[0].tool)
        assertEquals(1f, vectorMerged.strokes[0].opacity, 0.0001f)
        assertEquals(0.5f, vectorMerged.strokes[1].opacity, 0.0001f)
    }

    @Test
    fun referenceOutputFlagTravelsWithTheImage() {
        var p = project()
        val image = LayerImage("ref.png", 10, 10, 50f, 60f, 10f, 10f)
        p = ProjectOps.addReferenceLayer(p, 0, ProjectFactory.referenceLayer("Guide", image))
        assertEquals(false, p.frames[0].layers[0].image!!.inOutput)
        p = ProjectOps.setReferenceInOutput(p, 0, 0, true)
        assertEquals(true, p.frames[0].layers[0].image!!.inOutput)
        p = ProjectOps.setReferenceInOutput(p, 0, 0, false)
        assertEquals(false, p.frames[0].layers[0].image!!.inOutput)
        // Non-reference layers are untouched.
        p = ProjectOps.setReferenceInOutput(p, 0, 1, true)
        assertEquals(null, p.frames[0].layers[1].image)
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
