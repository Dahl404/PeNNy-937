package com.dahl404.penny937.data

import com.dahl404.penny937.model.Frame
import com.dahl404.penny937.model.Layer
import com.dahl404.penny937.model.LayerImage
import com.dahl404.penny937.model.Project
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import java.io.Closeable
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** Persistence contract for project documents; the store can be tested with a fake. */
interface ProjectFormat {
    fun write(project: Project, output: OutputStream)
    fun read(input: InputStream): Project?
    fun readMeta(input: InputStream): ProjectMeta?
}

/**
 * Compact binary project format. Pure JVM so it can be unit-tested without a device.
 *
 * Layout (version 1):
 *   magic, version, id, createdAt, updatedAt, name, width, height, fps, background, frameCount
 *   per frame: id, hold, layerCount
 *     per layer: id, name, visible, opacity, strokeCount
 *       per stroke: tool, color, width, opacity, pointCount, [x, y, pressure] * pointCount
 *
 * Version 2 added layer locks and reference images. Version 3 added the
 * [LayerImage.inOutput] flag that includes a reference in the export. Version 4
 * added [Layer.flattened] raster ink produced by merging incompatible layers.
 */
object ProjectCodec : ProjectFormat {
    const val MAGIC = 0x50393337
    const val VERSION = 4
    const val MIN_VERSION = 1

    override fun write(project: Project, output: OutputStream) {
        ProjectBytes.Out(output).use { out ->
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
                    out.writeBoolean(layer.locked)
                    val image = layer.image
                    out.writeBoolean(image != null)
                    if (image != null) {
                        out.writeUTF(image.file)
                        out.writeInt(image.sourceWidth)
                        out.writeInt(image.sourceHeight)
                        out.writeFloat(image.centerX)
                        out.writeFloat(image.centerY)
                        out.writeFloat(image.width)
                        out.writeFloat(image.height)
                        out.writeFloat(image.rotation)
                        out.writeBoolean(image.inOutput)
                    }
                    out.writeBoolean(layer.flattened)
                    out.writeInt(layer.strokes.size)
                    for (stroke in layer.strokes) {
                        out.writeByte(stroke.tool.ordinal)
                        out.writeInt(stroke.color)
                        out.writeFloat(stroke.width)
                        out.writeFloat(stroke.opacity)
                        out.writeInt(stroke.pointCount)
                        out.writeFloats(stroke.points, stroke.points.size)
                    }
                }
            }
        }
    }

    override fun read(input: InputStream): Project? = runCatching {
        ProjectBytes.In(input).use { data ->
            require(data.readInt() == MAGIC) { "Not a PeNNy project" }
            val version = data.readInt()
            require(version in MIN_VERSION..VERSION) { "Unsupported version" }
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
                    val locked = if (version >= 2) data.readBoolean() else false
                    val image = if (version >= 2 && data.readBoolean()) {
                        LayerImage(
                            file = data.readUTF(),
                            sourceWidth = data.readInt(),
                            sourceHeight = data.readInt(),
                            centerX = data.readFloat(),
                            centerY = data.readFloat(),
                            width = data.readFloat(),
                            height = data.readFloat(),
                            rotation = data.readFloat(),
                            inOutput = version >= 3 && data.readBoolean()
                        )
                    } else null
                    val flattened = version >= 4 && data.readBoolean()
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
                    layers.add(
                        Layer(
                            id = layerId,
                            name = layerName,
                            visible = visible,
                            opacity = opacity,
                            locked = locked,
                            image = image,
                            strokes = strokes,
                            flattened = flattened
                        )
                    )
                }
                frames.add(
                    Frame(
                        id = frameId,
                        hold = hold,
                        layers = layers.ifEmpty { listOf(Layer(id = frameId + 1, name = "Layer 1")) }
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
                            layers = listOf(Layer(id = (if (id > 0) id else 1L) + 1, name = "Layer 1"))
                        )
                    )
                },
                createdAt = created,
                updatedAt = updated
            )
        }
    }.getOrNull()

    override fun readMeta(input: InputStream): ProjectMeta? = runCatching {
        ProjectBytes.In(input).use { data ->
            require(data.readInt() == MAGIC)
            require(data.readInt() in MIN_VERSION..VERSION)
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

/**
 * Minimal big-endian byte plumbing for the project format.
 *
 * `DataOutputStream.writeFloat` costs four buffered-write calls per component, and a
 * stroke holds three components per point: a large project was spending most of its
 * save time in call overhead rather than I/O. These helpers pack into one byte array
 * per 64 KiB chunk instead, and stay format-compatible with DataInputStream.
 */
internal object ProjectBytes {

    class Out(private val out: OutputStream) : Closeable {
        private var buf = ByteArray(1 shl 16)
        private var pos = 0

        private fun ensure(n: Int) {
            if (pos + n > buf.size) {
                out.write(buf, 0, pos)
                pos = 0
            }
        }

        fun writeByte(v: Int) {
            ensure(1)
            buf[pos++] = v.toByte()
        }

        fun writeBoolean(v: Boolean) = writeByte(if (v) 1 else 0)

        fun writeInt(v: Int) {
            ensure(4)
            buf[pos++] = (v ushr 24).toByte()
            buf[pos++] = (v ushr 16).toByte()
            buf[pos++] = (v ushr 8).toByte()
            buf[pos++] = v.toByte()
        }

        fun writeLong(v: Long) {
            writeInt((v ushr 32).toInt())
            writeInt(v.toInt())
        }

        fun writeFloat(v: Float) = writeInt(java.lang.Float.floatToRawIntBits(v))

        /** Modified UTF-8, byte-for-byte compatible with DataOutputStream.writeUTF. */
        fun writeUTF(s: String) {
            var length = 0
            for (ch in s) {
                length += if (ch.code < 0x80) 1 else if (ch.code < 0x800) 2 else 3
            }
            if (length > 65535) throw IllegalArgumentException("string too long")
            ensure(2 + length)
            buf[pos++] = (length ushr 8).toByte()
            buf[pos++] = length.toByte()
            for (ch in s) {
                val c = ch.code
                when {
                    c < 0x80 -> buf[pos++] = c.toByte()
                    c < 0x800 -> {
                        buf[pos++] = (0xC0 or (c shr 6)).toByte()
                        buf[pos++] = (0x80 or (c and 0x3F)).toByte()
                    }
                    else -> {
                        buf[pos++] = (0xE0 or (c shr 12)).toByte()
                        buf[pos++] = (0x80 or ((c shr 6) and 0x3F)).toByte()
                        buf[pos++] = (0x80 or (c and 0x3F)).toByte()
                    }
                }
            }
        }

        /** Bulk write of a point array keeps the per-stroke hot path tight. */
        fun writeFloats(values: FloatArray, count: Int) {
            var i = 0
            while (i < count) {
                writeFloat(values[i])
                i++
            }
        }

        override fun close() {
            if (pos > 0) out.write(buf, 0, pos)
            pos = 0
            out.flush()
        }
    }

    class In(private val input: InputStream) : Closeable {
        private val buf = ByteArray(1 shl 16)
        private var pos = 0
        private var limit = 0

        private fun fill(n: Int) {
            if (limit - pos >= n) return
            var remaining = limit - pos
            if (remaining > 0) System.arraycopy(buf, pos, buf, 0, remaining)
            pos = 0
            limit = remaining
            while (limit < n) {
                val read = input.read(buf, limit, buf.size - limit)
                if (read < 0) throw EOFException()
                limit += read
            }
        }

        fun readByte(): Int {
            fill(1)
            return buf[pos++].toInt() and 0xFF
        }

        fun readBoolean(): Boolean = readByte() != 0

        fun readInt(): Int {
            fill(4)
            val v = ((buf[pos].toInt() and 0xFF) shl 24) or
                ((buf[pos + 1].toInt() and 0xFF) shl 16) or
                ((buf[pos + 2].toInt() and 0xFF) shl 8) or
                (buf[pos + 3].toInt() and 0xFF)
            pos += 4
            return v
        }

        fun readLong(): Long {
            val hi = readInt().toLong()
            val lo = readInt().toLong() and 0xFFFFFFFFL
            return (hi shl 32) or lo
        }

        fun readFloat(): Float = java.lang.Float.intBitsToFloat(readInt())

        fun readUTF(): String {
            val length = (readByte() shl 8) or readByte()
            fill(length)
            val chars = StringBuilder(length)
            var i = 0
            while (i < length) {
                val a = buf[pos + i].toInt() and 0xFF
                when {
                    a and 0x80 == 0 -> {
                        chars.append(a.toChar())
                        i += 1
                    }
                    a and 0xE0 == 0xC0 -> {
                        val b = buf[pos + i + 1].toInt() and 0x3F
                        chars.append((((a and 0x1F) shl 6) or b).toChar())
                        i += 2
                    }
                    else -> {
                        val b = buf[pos + i + 1].toInt() and 0x3F
                        val c = buf[pos + i + 2].toInt() and 0x3F
                        chars.append((((a and 0x0F) shl 12) or (b shl 6) or c).toChar())
                        i += 3
                    }
                }
            }
            pos += length
            return chars.toString()
        }

        override fun close() {
            input.close()
        }
    }
}
