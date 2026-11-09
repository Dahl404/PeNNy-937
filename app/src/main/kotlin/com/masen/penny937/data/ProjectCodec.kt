package com.masen.penny937.data

import com.masen.penny937.model.Frame
import com.masen.penny937.model.Layer
import com.masen.penny937.model.Project
import com.masen.penny937.model.Stroke
import com.masen.penny937.model.Tool
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Compact binary project format. Pure JVM so it can be unit-tested without a device.
 *
 * Layout (version 1):
 *   magic, version, id, createdAt, updatedAt, name, width, height, fps, background, frameCount
 *   per frame: id, hold, layerCount
 *     per layer: id, name, visible, opacity, strokeCount
 *       per stroke: tool, color, width, opacity, pointCount, [x, y, pressure] * pointCount
 */
object ProjectCodec {
    const val MAGIC = 0x50393337
    const val VERSION = 1

    fun write(project: Project, output: OutputStream) {
        DataOutputStream(BufferedOutputStream(output, 1 shl 16)).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
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
    }

    fun read(input: InputStream): Project? = runCatching {
        DataInputStream(BufferedInputStream(input, 1 shl 16)).use { data ->
            require(data.readInt() == MAGIC) { "Not a PeNNy project" }
            require(data.readInt() == VERSION) { "Unsupported version" }
            val id = data.readLong()
            val created = data.readLong()
            val updated = data.readLong()
            val name = data.readUTF()
            val width = data.readInt()
            val height = data.readInt()
            val fps = data.readInt()
            val background = data.readInt()
            val frameCount = data.readInt()
            val frames = ArrayList<Frame>(frameCount)
            repeat(frameCount) {
                val frameId = data.readLong()
                val hold = data.readInt().coerceAtLeast(1)
                val layerCount = data.readInt()
                val layers = ArrayList<Layer>(layerCount)
                repeat(layerCount) {
                    val layerId = data.readLong()
                    val layerName = data.readUTF()
                    val visible = data.readBoolean()
                    val opacity = data.readFloat().coerceIn(0f, 1f)
                    val strokeCount = data.readInt()
                    val strokes = ArrayList<Stroke>(strokeCount)
                    repeat(strokeCount) {
                        val tool = Tool.entries.getOrElse(data.readByte().toInt()) { Tool.PEN }
                        val color = data.readInt()
                        val strokeWidth = data.readFloat()
                        val strokeOpacity = data.readFloat().coerceIn(0f, 1f)
                        val pointCount = data.readInt()
                        val points = FloatArray(pointCount * 3)
                        for (i in points.indices) points[i] = data.readFloat()
                        strokes.add(Stroke(tool, color, strokeWidth, strokeOpacity, points))
                    }
                    layers.add(Layer(layerId, layerName, visible, opacity, strokes))
                }
                frames.add(
                    Frame(
                        id = frameId,
                        hold = hold,
                        layers = layers.ifEmpty { listOf(Layer(frameId + 1, "Layer 1")) }
                    )
                )
            }
            Project(
                id = id,
                name = name,
                width = width.coerceAtLeast(16),
                height = height.coerceAtLeast(16),
                fps = fps.coerceIn(1, 120),
                background = background,
                frames = frames.ifEmpty {
                    listOf(
                        Frame(
                            id = if (id > 0) id else 1L,
                            layers = listOf(Layer((if (id > 0) id else 1L) + 1, "Layer 1"))
                        )
                    )
                },
                createdAt = created,
                updatedAt = updated
            )
        }
    }.getOrNull()

    fun readMeta(input: InputStream): ProjectMeta? = runCatching {
        DataInputStream(BufferedInputStream(input)).use { data ->
            require(data.readInt() == MAGIC)
            require(data.readInt() == VERSION)
            val id = data.readLong()
            data.readLong() // createdAt
            val updated = data.readLong()
            val name = data.readUTF()
            val width = data.readInt()
            val height = data.readInt()
            val fps = data.readInt()
            val background = data.readInt()
            val frameCount = data.readInt()
            ProjectMeta(
                id = id,
                name = name,
                width = width,
                height = height,
                fps = fps,
                frameCount = frameCount,
                updatedAt = updated,
                background = background,
                hasThumbnail = false
            )
        }
    }.getOrNull()
}
