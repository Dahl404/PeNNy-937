package com.masen.penny937.data

import com.masen.penny937.model.ProjectFactory
import com.masen.penny937.model.ProjectOps
import com.masen.penny937.model.Stroke
import com.masen.penny937.model.Tool
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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
    fun rejectsForeignBytes() {
        val bytes = ByteArray(64) { it.toByte() }
        assertNull(ProjectCodec.read(ByteArrayInputStream(bytes)))
        assertNull(ProjectCodec.readMeta(ByteArrayInputStream(bytes)))
    }
}
