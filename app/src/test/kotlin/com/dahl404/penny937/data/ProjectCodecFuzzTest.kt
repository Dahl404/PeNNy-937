package com.dahl404.penny937.data

import com.dahl404.penny937.model.Frame
import com.dahl404.penny937.model.Layer
import com.dahl404.penny937.model.LayerImage
import com.dahl404.penny937.model.Project
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * Randomized round trips for the project codec. The fixed-shape tests cover the
 * common document; this covers odd ids, empty layers, mixed tools and references.
 */
class ProjectCodecFuzzTest {

    private fun randomProject(random: Random): Project {
        var nextId = 1L
        fun id(): Long = (nextId++).also { }
        val frames = (0 until random.nextInt(1, 5)).map { frameIndex ->
            val layers = (0 until random.nextInt(1, 4)).map { layerIndex ->
                val strokes = (0 until random.nextInt(0, 6)).map {
                    val points = FloatArray(random.nextInt(1, 10) * 3) {
                        when (it % 3) {
                            0 -> random.nextFloat() * 1080f
                            1 -> random.nextFloat() * 1920f
                            else -> random.nextFloat()
                        }
                    }
                    Stroke(
                        tool = Tool.entries[random.nextInt(Tool.entries.size)],
                        color = random.nextInt(),
                        width = random.nextFloat() * 100f,
                        opacity = random.nextFloat(),
                        points = points
                    )
                }
                val image = if (random.nextInt(4) == 0) {
                    LayerImage(
                        file = "ref-${random.nextInt(1000)}.png",
                        sourceWidth = random.nextInt(1, 5000),
                        sourceHeight = random.nextInt(1, 5000),
                        centerX = random.nextFloat() * 1080f,
                        centerY = random.nextFloat() * 1920f,
                        width = random.nextFloat() * 1080f,
                        height = random.nextFloat() * 1920f,
                        rotation = random.nextFloat() * 720f - 360f,
                        inOutput = random.nextBoolean()
                    )
                } else null
                Layer(
                    id = id(),
                    name = "Layer $frameIndex-$layerIndex ${random.nextInt(10)}",
                    visible = random.nextBoolean(),
                    opacity = random.nextFloat(),
                    locked = random.nextBoolean(),
                    image = image,
                    strokes = strokes
                )
            }
            Frame(id = id(), hold = random.nextInt(1, 6), layers = layers)
        }
        return Project(
            id = id(),
            name = "fuzz ${random.nextInt()}",
            width = random.nextInt(32, 4097),
            height = random.nextInt(32, 4097),
            fps = random.nextInt(1, 61),
            background = random.nextInt(),
            frames = frames,
            createdAt = random.nextLong(),
            updatedAt = random.nextLong()
        )
    }

    @Test
    fun randomProjectsSurviveEncodeAndDecode() {
        val random = Random(937)
        repeat(40) {
            val project = randomProject(random)
            val bytes = ByteArrayOutputStream().also {
                ProjectCodec.write(project, it)
            }.toByteArray()
            val decoded = ProjectCodec.read(ByteArrayInputStream(bytes))
            assertNotNull(decoded)
            assertSameProject(project, decoded!!)

            val meta = ProjectCodec.readMeta(ByteArrayInputStream(bytes))
            assertNotNull(meta)
            assertEquals(project.id, meta!!.id)
            assertEquals(project.name, meta.name)
            assertEquals(project.frames.size, meta.frameCount)
        }
    }

    private fun assertSameProject(expected: Project, actual: Project) {
        assertEquals(expected.id, actual.id)
        assertEquals(expected.name, actual.name)
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        assertEquals(expected.fps, actual.fps)
        assertEquals(expected.background, actual.background)
        assertEquals(expected.createdAt, actual.createdAt)
        assertEquals(expected.updatedAt, actual.updatedAt)
        assertEquals(expected.frames.size, actual.frames.size)
        for (f in expected.frames.indices) {
            val ef = expected.frames[f]
            val af = actual.frames[f]
            assertEquals(ef.id, af.id)
            assertEquals(ef.hold, af.hold)
            assertEquals(ef.layers.size, af.layers.size)
            for (l in ef.layers.indices) {
                val el = ef.layers[l]
                val al = af.layers[l]
                assertEquals(el.id, al.id)
                assertEquals(el.name, al.name)
                assertEquals(el.visible, al.visible)
                assertEquals(el.opacity, al.opacity, 0.0001f)
                assertEquals(el.locked, al.locked)
                assertEquals(el.image, al.image)
                assertEquals(el.strokes.size, al.strokes.size)
                for (s in el.strokes.indices) {
                    val es = el.strokes[s]
                    val asr = al.strokes[s]
                    assertEquals(es.tool, asr.tool)
                    assertEquals(es.color, asr.color)
                    assertEquals(es.width, asr.width, 0.0001f)
                    assertEquals(es.opacity, asr.opacity, 0.0001f)
                    assertEquals(es.points.size, asr.points.size)
                    for (i in es.points.indices) {
                        assertEquals(es.points[i], asr.points[i], 0.0001f)
                    }
                }
            }
        }
    }
}
