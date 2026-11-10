package com.dahl404.penny937.data

import com.dahl404.penny937.model.LayerImage
import com.dahl404.penny937.model.ProjectFactory
import com.dahl404.penny937.model.ProjectOps
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class ProjectCodecTest {

    @Test
    fun roundTripsAWholeProject() {
        var project = ProjectFactory.create("Moth study", 1080, 1920, 12, 0xFFFBF9F4.toInt())
        project = ProjectOps.addFrameAfter(project, 0)
        project = ProjectOps.addLayer(project, 0, 0).first
        project = ProjectOps.setHold(project, 0, 3)
        project = ProjectOps.addStroke(
            project, 0, 1,
            Stroke(
                Tool.PENCIL, 0xFFAA3311.toInt(), 7.5f, 1f,
                floatArrayOf(1f, 2f, 0.25f, 3f, 4f, 0.75f, 5f, 6f, 0.5f)
            )
        )
        project = ProjectOps.addStroke(
            project, 0, 0,
            Stroke(
                Tool.MARKER, 0xFF11AA33.toInt(), 42f, 0.4f,
                floatArrayOf(10f, 20f, 1f, 30f, 40f, 1f)
            )
        )
        project = ProjectOps.updateLayer(project, 0, 1) { it.copy(visible = false, opacity = 0.35f) }
        project = ProjectOps.updateFrame(project, 1) { it.copy(hold = 2) }

        val bytes = ByteArrayOutputStream().also { ProjectCodec.write(project, it) }.toByteArray()
        assertTrue(bytes.size > 100)

        val loaded = ProjectCodec.read(ByteArrayInputStream(bytes))
        assertNotNull(loaded)
        loaded!!
        assertEquals(project.id, loaded.id)
        assertEquals(project.name, loaded.name)
        assertEquals(project.width, loaded.width)
        assertEquals(project.height, loaded.height)
        assertEquals(project.fps, loaded.fps)
        assertEquals(project.background, loaded.background)
        assertEquals(project.createdAt, loaded.createdAt)
        assertEquals(project.updatedAt, loaded.updatedAt)
        assertEquals(project.frames.size, loaded.frames.size)
        for (i in project.frames.indices) {
            val a = project.frames[i]
            val b = loaded.frames[i]
            assertEquals(a.id, b.id)
            assertEquals(a.hold, b.hold)
            assertEquals(a.layers.size, b.layers.size)
            for (j in a.layers.indices) {
                val la = a.layers[j]
                val lb = b.layers[j]
                assertEquals(la.id, lb.id)
                assertEquals(la.name, lb.name)
                assertEquals(la.visible, lb.visible)
                assertEquals(la.opacity, lb.opacity, 0.0001f)
                assertEquals(la.strokes.size, lb.strokes.size)
                for (k in la.strokes.indices) {
                    val sa = la.strokes[k]
                    val sb = lb.strokes[k]
                    assertEquals(sa.tool, sb.tool)
                    assertEquals(sa.color, sb.color)
                    assertEquals(sa.width, sb.width, 0.0001f)
                    assertEquals(sa.opacity, sb.opacity, 0.0001f)
                    assertArrayEquals(sa.points, sb.points, 0.0001f)
                }
            }
        }

        val meta = ProjectCodec.readMeta(ByteArrayInputStream(bytes))
        assertNotNull(meta)
        assertEquals(project.id, meta!!.id)
        assertEquals(project.name, meta.name)
        assertEquals(project.width, meta.width)
        assertEquals(project.height, meta.height)
        assertEquals(project.fps, meta.fps)
        assertEquals(project.frames.size, meta.frameCount)
        assertEquals(project.updatedAt, meta.updatedAt)
        assertEquals(project.background, meta.background)
        assertEquals(false, meta.hasThumbnail)
    }

    @Test
    fun roundTripsReferenceImagesAndLocks() {
        var project = ProjectFactory.create("Refs", 1080, 1920, 12, 0xFFFFFFFF.toInt())
        project = ProjectOps.addLayer(project, 0, 0).first
        project = ProjectOps.updateLayer(project, 0, 1) { it.copy(locked = true) }
        val image = LayerImage(
            file = "ref-123.png",
            sourceWidth = 1200,
            sourceHeight = 900,
            centerX = 540f,
            centerY = 960f,
            width = 800f,
            height = 600f,
            rotation = 12.5f,
            inOutput = true
        )
        project = ProjectOps.addReferenceLayer(
            project, 0,
            ProjectFactory.referenceLayer("Reference", image)
        )

        val bytes = ByteArrayOutputStream().also { ProjectCodec.write(project, it) }.toByteArray()
        val loaded = ProjectCodec.read(ByteArrayInputStream(bytes))!!
        val layers = loaded.frames[0].layers
        assertEquals(3, layers.size)
        assertTrue(layers[0].isReference)
        assertTrue(layers[0].locked)
        assertEquals("ref-123.png", layers[0].image!!.file)
        assertEquals(1200, layers[0].image!!.sourceWidth)
        assertEquals(800f, layers[0].image!!.width, 0.001f)
        assertEquals(12.5f, layers[0].image!!.rotation, 0.001f)
        assertEquals(true, layers[0].image!!.inOutput)
        assertTrue(layers[2].locked)
        assertEquals(null, layers[1].image)
        assertEquals(null, layers[2].image)
    }

    @Test
    fun roundTripsFlattenedRasterInk() {
        var project = ProjectFactory.create("Merge", 640, 480, 12, 0xFFFFFFFF.toInt())
        val image = LayerImage(
            file = "merged-1.png",
            sourceWidth = 640,
            sourceHeight = 480,
            centerX = 320f,
            centerY = 240f,
            width = 640f,
            height = 480f,
            inOutput = true
        )
        project = ProjectOps.updateLayer(project, 0, 0) {
            it.copy(image = image, flattened = true)
        }

        val bytes = ByteArrayOutputStream().also { ProjectCodec.write(project, it) }.toByteArray()
        val loaded = ProjectCodec.read(ByteArrayInputStream(bytes))!!
        val layer = loaded.frames[0].layers[0]
        assertEquals(true, layer.flattened)
        assertEquals(false, layer.isReference)
        assertEquals("merged-1.png", layer.image!!.file)
        assertEquals(image, layer.image)
    }

    @Test
    fun readsVersionOneProjects() {
        var project = ProjectFactory.create("Old", 640, 480, 8, 0xFFFFFFFF.toInt())
        project = ProjectOps.addFrameAfter(project, 0)
        project = ProjectOps.addStroke(
            project, 0, 0,
            Stroke(Tool.PEN, 0xFF112233.toInt(), 6f, 1f, floatArrayOf(1f, 2f, 1f, 3f, 4f, 1f))
        )

        val bytes = ByteArrayOutputStream()
        DataOutputStream(BufferedOutputStream(bytes)).use { out ->
            out.writeInt(ProjectCodec.MAGIC)
            out.writeInt(1)
            out.writeLong(project.id)
            out.writeLong(project.createdAt)
            out.writeLong(project.updatedAt)
            out.writeUTF(project.name)
            out.writeInt(project.width)
            out.writeInt(project.height)
            out.writeInt(project.fps)
            out.writeInt(project.background)
            out.writeInt(project.frames.size)
            for (frame in project.frames) {
                out.writeLong(frame.id)
                out.writeInt(frame.hold)
                out.writeInt(frame.layers.size)
                for (layer in frame.layers) {
                    out.writeLong(layer.id)
                    out.writeUTF(layer.name)
                    out.writeBoolean(layer.visible)
                    out.writeFloat(layer.opacity)
                    out.writeInt(layer.strokes.size)
                    for (stroke in layer.strokes) {
                        out.writeByte(stroke.tool.ordinal)
                        out.writeInt(stroke.color)
                        out.writeFloat(stroke.width)
                        out.writeFloat(stroke.opacity)
                        out.writeInt(stroke.pointCount)
                        for (v in stroke.points) out.writeFloat(v)
                    }
                }
            }
        }

        val loaded = ProjectCodec.read(ByteArrayInputStream(bytes.toByteArray()))
        assertNotNull(loaded)
        loaded!!
        assertEquals(project.frames.size, loaded.frames.size)
        assertEquals(1, loaded.frames[0].layers[0].strokes.size)
        assertEquals(false, loaded.frames[0].layers[0].locked)
        assertEquals(null, loaded.frames[0].layers[0].image)
        assertNotNull(ProjectCodec.readMeta(ByteArrayInputStream(bytes.toByteArray())))
    }

    @Test
    fun readsVersionTwoReferencesAsGuideOnly() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(BufferedOutputStream(bytes)).use { out ->
            out.writeInt(ProjectCodec.MAGIC)
            out.writeInt(2)
            out.writeLong(42L)
            out.writeLong(1L)
            out.writeLong(2L)
            out.writeUTF("Old refs")
            out.writeInt(640)
            out.writeInt(480)
            out.writeInt(8)
            out.writeInt(0xFFFFFFFF.toInt())
            out.writeInt(1) // frames
            out.writeLong(100L)
            out.writeInt(1) // hold
            out.writeInt(1) // layers
            out.writeLong(200L)
            out.writeUTF("Reference")
            out.writeBoolean(true)
            out.writeFloat(1f)
            out.writeBoolean(true) // locked
            out.writeBoolean(true) // has image
            out.writeUTF("ref-old.png")
            out.writeInt(640)
            out.writeInt(480)
            out.writeFloat(320f)
            out.writeFloat(240f)
            out.writeFloat(640f)
            out.writeFloat(480f)
            out.writeFloat(0f)
            out.writeInt(0) // strokes
        }

        val loaded = ProjectCodec.read(ByteArrayInputStream(bytes.toByteArray()))!!
        val image = loaded.frames[0].layers[0].image!!
        assertEquals("ref-old.png", image.file)
        assertEquals(false, image.inOutput)
    }

    @Test
    fun rejectsForeignBytes() {
        val bytes = ByteArray(64) { it.toByte() }
        assertNull(ProjectCodec.read(ByteArrayInputStream(bytes)))
        assertNull(ProjectCodec.readMeta(ByteArrayInputStream(bytes)))
    }
}
