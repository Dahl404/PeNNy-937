package com.masen.penny937.model

import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** The four ink tools. Intentionally few — each one is meant to feel different. */
enum class Tool(val label: String) {
    PEN("Pen"),
    PENCIL("Pencil"),
    MARKER("Marker"),
    SHAPE("Shape"),
    ERASER("Eraser");

    val isEraser: Boolean get() = this == ERASER

    /** Markers need a separate stroke buffer so translucent ink never doubles up at joints. */
    val usesOverlay: Boolean get() = this == MARKER

    fun defaultWidth(): Float = when (this) {
        PEN -> 8f
        PENCIL -> 7f
        MARKER -> 40f
        SHAPE -> 8f
        ERASER -> 40f
    }

    fun defaultOpacity(): Float = when (this) {
        MARKER -> 0.42f
        else -> 1f
    }

    fun minWidth(): Float = when (this) {
        MARKER, ERASER -> 4f
        SHAPE -> 1f
        else -> 0.6f
    }

    fun maxWidth(): Float = when (this) {
        MARKER, ERASER -> 260f
        SHAPE -> 160f
        else -> 120f
    }
}

enum class ShapeKind(val label: String) {
    LINE("Line"),
    RECTANGLE("Rectangle"),
    SQUARE("Square"),
    ELLIPSE("Ellipse"),
    CIRCLE("Circle")
}

enum class GridStyle(val label: String) {
    GRID("Squares"),
    THIRDS("Thirds")
}

/**
 * One continuous mark. Points are stored flat as x, y, pressure triples in project pixels.
 * Strokes are immutable; editing creates a new list, so undo snapshots are nearly free.
 */
class Stroke(
    val tool: Tool,
    val color: Int,
    val width: Float,
    val opacity: Float,
    val points: FloatArray
) {
    val pointCount: Int get() = points.size / 3

    fun pointX(i: Int): Float = points[i * 3]
    fun pointY(i: Int): Float = points[i * 3 + 1]
    fun pointPressure(i: Int): Float = points[i * 3 + 2]

    /** Returns a copy whose points are moved by [dx], [dy] project pixels. */
    fun translated(dx: Float, dy: Float): Stroke {
        if (dx == 0f && dy == 0f) return this
        val moved = points.copyOf()
        var i = 0
        while (i < moved.size) {
            moved[i] += dx
            moved[i + 1] += dy
            i += 3
        }
        return Stroke(tool, color, width, opacity, moved)
    }
}

data class LayerImage(
    val file: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f
)

data class Layer(
    val id: Long,
    val name: String,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val locked: Boolean = false,
    val image: LayerImage? = null,
    val strokes: List<Stroke> = emptyList()
) {
    val isReference: Boolean get() = image != null

    /**
     * Cheap identity for the rendered result of this layer. Cached because the
     * canvas and the timeline thumbnails read it while composing, and computing it
     * meant walking every stroke of every layer each time.
     */
    val revision: Int by lazy {
        var h = 31 * (if (visible) 7 else 3) + (opacity * 100).toInt()
        h = h * 31 + (if (locked) 1 else 0)
        h = h * 31 + (image?.hashCode() ?: 0)
        for (s in strokes) h = h * 31 + s.pointCount + s.tool.ordinal
        h
    }
}

data class Frame(
    val id: Long,
    val hold: Int = 1,
    val layers: List<Layer>
) {
    val revision: Int by lazy {
        layers.fold(17 * hold) { acc, l ->
            ((acc * 31 + l.revision) * 31 + l.id.hashCode())
        }
    }

    val strokeCount: Int get() = layers.sumOf { it.strokes.size }
}

data class Project(
    val id: Long,
    val name: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val background: Int,
    val frames: List<Frame>,
    val createdAt: Long,
    val updatedAt: Long
) {
    val totalTicks: Int by lazy { frames.sumOf { it.hold } }

    val durationSeconds: Float get() = totalTicks.toFloat() / fps

    /** First tick covered by each frame; built once and binary-searched. */
    private val tickStarts: IntArray by lazy {
        val starts = IntArray(frames.size)
        var t = 0
        for (i in frames.indices) {
            starts[i] = t
            t += frames[i].hold
        }
        starts
    }

    /** Playback asks for this on every displayed frame, so it must not scan. */
    fun frameIndexAtTick(tick: Int): Int {
        if (frames.isEmpty()) return 0
        val clamped = tick.coerceIn(0, max(0, totalTicks - 1))
        var low = 0
        var high = frames.size - 1
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (tickStarts[mid] <= clamped) low = mid else high = mid - 1
        }
        return low
    }

    fun tickOfFrame(index: Int): Int {
        if (frames.isEmpty()) return 0
        return tickStarts[index.coerceIn(0, frames.lastIndex)]
    }

    fun frameAt(index: Int): Frame = frames[index.coerceIn(0, frames.lastIndex)]

    fun layerAt(frameIndex: Int, layerIndex: Int): Layer {
        val f = frameAt(frameIndex)
        return f.layers[layerIndex.coerceIn(0, f.layers.lastIndex)]
    }

    fun frameCountLabel(): String = "${frames.size} frame${if (frames.size == 1) "" else "s"}"

    fun aspect(): Float = width.toFloat() / height.toFloat()
}

object Ids {
    private var counter = 0L

    @Synchronized
    fun next(): Long {
        counter += 1
        val t = System.currentTimeMillis() shl 20
        return (t + counter) and Long.MAX_VALUE
    }

    fun fresh(): Long = (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE).coerceAtLeast(1)
}

object ProjectFactory {
    const val MIN_DIM = 32
    const val MAX_DIM = 4096
    const val MIN_FPS = 1
    const val MAX_FPS = 60

    val backgrounds = listOf(
        "Paper" to 0xFFFBF9F4.toInt(),
        "White" to 0xFFFFFFFF.toInt(),
        "Fog" to 0xFFF0F0F2.toInt(),
        "Slate" to 0xFF1E1E20.toInt(),
        "Midnight" to 0xFF101828.toInt()
    )

    val sizePresets = listOf(
        Triple("Portrait", 1080, 1920),
        Triple("Landscape", 1920, 1080),
        Triple("Square", 1080, 1080),
        Triple("Phone", 1440, 3120)
    )

    val fpsPresets = listOf(6, 8, 12, 15, 24, 30, 60)

    fun create(
        name: String,
        width: Int,
        height: Int,
        fps: Int,
        background: Int
    ): Project {
        val now = System.currentTimeMillis()
        val frame = Frame(
            id = Ids.next(),
            layers = listOf(Layer(id = Ids.next(), name = "Layer 1"))
        )
        return Project(
            id = Ids.next(),
            name = name.trim().ifBlank { "Untitled" },
            width = width.coerceIn(MIN_DIM, MAX_DIM),
            height = height.coerceIn(MIN_DIM, MAX_DIM),
            fps = fps.coerceIn(MIN_FPS, MAX_FPS),
            background = background,
            frames = listOf(frame),
            createdAt = now,
            updatedAt = now
        )
    }

    fun blankFrameLike(frame: Frame, nameIndex: Int): Frame = Frame(
        id = Ids.next(),
        hold = frame.hold,
        layers = frame.layers
            .filterNot { it.isReference }
            .mapIndexed { i, l ->
                Layer(
                    id = Ids.next(),
                    name = l.name.ifBlank { "Layer ${i + 1}" },
                    visible = l.visible,
                    opacity = l.opacity
                )
            }
            .ifEmpty { listOf(Layer(id = Ids.next(), name = "Layer 1")) }
    )

    fun duplicateFrame(frame: Frame): Frame = Frame(
        id = Ids.next(),
        hold = frame.hold,
        layers = frame.layers.map { l ->
            l.copy(id = Ids.next(), strokes = l.strokes.toList())
        }
    )

    fun newLayer(name: String): Layer = Layer(id = Ids.next(), name = name)

    fun referenceLayer(name: String, image: LayerImage): Layer = Layer(
        id = Ids.next(),
        name = name,
        locked = true,
        image = image
    )

    fun duplicateProject(project: Project, newName: String = project.name + " copy"): Project {
        val now = System.currentTimeMillis()
        return project.copy(
            id = Ids.next(),
            name = newName,
            createdAt = now,
            updatedAt = now,
            frames = project.frames.map { duplicateFrame(it) }
        )
    }
}

/** Immutable project edits used by the editor. */
object ProjectOps {

    fun updateFrame(project: Project, frameIndex: Int, block: (Frame) -> Frame): Project {
        if (frameIndex !in project.frames.indices) return project
        val frames = project.frames.toMutableList()
        frames[frameIndex] = block(frames[frameIndex])
        return project.copy(frames = frames, updatedAt = System.currentTimeMillis())
    }

    fun updateLayer(project: Project, frameIndex: Int, layerIndex: Int, block: (Layer) -> Layer): Project =
        updateFrame(project, frameIndex) { frame ->
            if (layerIndex !in frame.layers.indices) frame
            else {
                val layers = frame.layers.toMutableList()
                layers[layerIndex] = block(layers[layerIndex])
                frame.copy(layers = layers)
            }
        }

    fun addStroke(project: Project, frameIndex: Int, layerIndex: Int, stroke: Stroke): Project =
        updateLayer(project, frameIndex, layerIndex) { it.copy(strokes = it.strokes + stroke) }

    fun undoStroke(project: Project, frameIndex: Int, layerIndex: Int): Project =
        updateLayer(project, frameIndex, layerIndex) { layer ->
            if (layer.strokes.isEmpty()) layer else layer.copy(strokes = layer.strokes.dropLast(1))
        }

    fun addFrameAfter(project: Project, frameIndex: Int): Project {
        val source = project.frameAt(frameIndex)
        val frames = project.frames.toMutableList()
        frames.add(frameIndex + 1, ProjectFactory.blankFrameLike(source, frames.size))
        return project.copy(frames = frames, updatedAt = System.currentTimeMillis())
    }

    fun duplicateFrameAfter(project: Project, frameIndex: Int): Project {
        val frames = project.frames.toMutableList()
        frames.add(frameIndex + 1, ProjectFactory.duplicateFrame(project.frameAt(frameIndex)))
        return project.copy(frames = frames, updatedAt = System.currentTimeMillis())
    }

    fun moveFrame(project: Project, from: Int, to: Int): Project {
        if (from !in project.frames.indices) return project
        if (project.frames.size < 2) return project
        val target = to.coerceIn(0, project.frames.lastIndex)
        if (from == target) return project
        val frames = project.frames.toMutableList()
        val frame = frames.removeAt(from)
        frames.add(target, frame)
        return project.copy(frames = frames, updatedAt = System.currentTimeMillis())
    }

    fun deleteFrame(project: Project, frameIndex: Int): Project {
        if (project.frames.size <= 1) return project
        val frames = project.frames.toMutableList()
        frames.removeAt(frameIndex.coerceIn(0, frames.lastIndex))
        return project.copy(frames = frames, updatedAt = System.currentTimeMillis())
    }

    fun setHold(project: Project, frameIndex: Int, hold: Int): Project =
        updateFrame(project, frameIndex) { it.copy(hold = hold.coerceIn(1, 240)) }

    fun addLayer(project: Project, frameIndex: Int, aboveLayer: Int): Pair<Project, Int> {
        val frame = project.frameAt(frameIndex)
        val insertAt = (aboveLayer + 1).coerceIn(0, frame.layers.size)
        val layer = ProjectFactory.newLayer(nextLayerName(frame))
        val layers = frame.layers.toMutableList()
        layers.add(insertAt, layer)
        val updated = updateFrame(project, frameIndex) { it.copy(layers = layers) }
        return updated to insertAt
    }

    fun duplicateLayer(project: Project, frameIndex: Int, layerIndex: Int): Pair<Project, Int> {
        val frame = project.frameAt(frameIndex)
        val source = frame.layers.getOrNull(layerIndex) ?: return project to layerIndex
        val copy = source.copy(
            id = Ids.next(),
            name = source.name + " copy",
            strokes = source.strokes.toList()
        )
        val layers = frame.layers.toMutableList()
        layers.add(layerIndex + 1, copy)
        val updated = updateFrame(project, frameIndex) { it.copy(layers = layers) }
        return updated to (layerIndex + 1)
    }

    fun deleteLayer(project: Project, frameIndex: Int, layerIndex: Int): Pair<Project, Int> {
        val frame = project.frameAt(frameIndex)
        if (frame.layers.size <= 1) {
            val only = frame.layers[layerIndex.coerceIn(0, frame.layers.lastIndex)]
            val cleared = if (only.isReference) {
                updateLayer(project, frameIndex, layerIndex) {
                    Layer(id = it.id, name = "Layer 1")
                }
            } else {
                updateLayer(project, frameIndex, layerIndex) {
                    it.copy(strokes = emptyList(), visible = true, opacity = 1f)
                }
            }
            return cleared to layerIndex
        }
        val layers = frame.layers.toMutableList()
        layers.removeAt(layerIndex.coerceIn(0, layers.lastIndex))
        val updated = updateFrame(project, frameIndex) { it.copy(layers = layers) }
        return updated to (layerIndex.coerceIn(0, layers.lastIndex))
    }

    fun mergeLayerDown(project: Project, frameIndex: Int, layerIndex: Int): Pair<Project, Int> {
        val frame = project.frameAt(frameIndex)
        if (layerIndex <= 0) return project to layerIndex
        val lower = frame.layers[layerIndex - 1]
        val upper = frame.layers[layerIndex]
        if (lower.locked || upper.locked || lower.image != null || upper.image != null) {
            return project to layerIndex
        }
        val merged = lower.copy(
            strokes = lower.strokes + upper.strokes.map { s ->
                if (s.opacity >= 1f) s
                else Stroke(s.tool, s.color, s.width, s.opacity, s.points)
            },
            visible = true,
            opacity = 1f
        )
        val layers = frame.layers.toMutableList()
        layers[layerIndex - 1] = merged
        layers.removeAt(layerIndex)
        val updated = updateFrame(project, frameIndex) { it.copy(layers = layers) }
        return updated to (layerIndex - 1)
    }

    fun moveLayer(project: Project, frameIndex: Int, from: Int, to: Int): Pair<Project, Int> {
        val frame = project.frameAt(frameIndex)
        if (from !in frame.layers.indices || to !in frame.layers.indices || from == to) {
            return project to from
        }
        val layers = frame.layers.toMutableList()
        val item = layers.removeAt(from)
        layers.add(to, item)
        return updateFrame(project, frameIndex) { it.copy(layers = layers) } to to
    }

    fun clearLayer(project: Project, frameIndex: Int, layerIndex: Int): Project =
        updateLayer(project, frameIndex, layerIndex) {
            if (it.locked || it.isReference) it else it.copy(strokes = emptyList())
        }

    /** Moves every stroke and the reference placement of one layer by [dx], [dy]. */
    fun translateLayer(
        project: Project,
        frameIndex: Int,
        layerIndex: Int,
        dx: Float,
        dy: Float
    ): Project =
        updateLayer(project, frameIndex, layerIndex) { layer ->
            if (dx == 0f && dy == 0f) layer
            else layer.copy(
                image = layer.image?.let {
                    it.copy(centerX = it.centerX + dx, centerY = it.centerY + dy)
                },
                strokes = layer.strokes.map { it.translated(dx, dy) }
            )
        }

    data class VideoInsertResult(
        val project: Project,
        val startFrame: Int,
        val endFrame: Int,
        val selectedLayer: Int
    )

    /**
     * Adds one video reference layer to each frame from [startFrame] onwards,
     * appending blank frames as needed. The returned endFrame is the last frame
     * that received a reference; the next clip can continue at endFrame + 1.
     */
    fun insertVideoReferenceFrames(
        project: Project,
        startFrame: Int,
        images: List<LayerImage>,
        layerName: String
    ): VideoInsertResult {
        if (images.isEmpty()) {
            return VideoInsertResult(project, startFrame, startFrame - 1, 0)
        }
        val frames = project.frames.toMutableList()
        val start = startFrame.coerceIn(0, frames.size)
        val base = project.frameAt(start.coerceAtMost(project.frames.lastIndex))
        for (i in images.indices) {
            val index = start + i
            while (frames.size <= index) {
                frames.add(ProjectFactory.blankFrameLike(base, frames.size))
            }
            val frame = frames[index]
            val layer = ProjectFactory.referenceLayer(layerName, images[i])
            frames[index] = frame.copy(layers = listOf(layer) + frame.layers)
        }

        val startFrameData = frames[start]
        var layers = startFrameData.layers
        var selected = layers.indexOfFirst { !it.isReference }
        if (selected < 0) {
            val editable = Layer(id = Ids.next(), name = "Layer 1")
            layers = layers + editable
            frames[start] = startFrameData.copy(layers = layers)
            selected = layers.lastIndex
        }

        val updated = project.copy(frames = frames, updatedAt = System.currentTimeMillis())
        return VideoInsertResult(
            project = updated,
            startFrame = start,
            endFrame = start + images.size - 1,
            selectedLayer = selected
        )
    }

    /** Inserts a reference image layer at the bottom of a frame. */
    fun addReferenceLayer(project: Project, frameIndex: Int, layer: Layer): Project =
        updateFrame(project, frameIndex) { frame ->
            frame.copy(layers = listOf(layer) + frame.layers)
        }

    fun updateLayerImage(
        project: Project,
        frameIndex: Int,
        layerIndex: Int,
        image: LayerImage
    ): Project = updateLayer(project, frameIndex, layerIndex) { it.copy(image = image) }

    fun updateSettings(
        project: Project,
        name: String,
        width: Int,
        height: Int,
        fps: Int,
        background: Int
    ): Project = project.copy(
        name = name.trim().ifBlank { project.name },
        width = width.coerceIn(ProjectFactory.MIN_DIM, ProjectFactory.MAX_DIM),
        height = height.coerceIn(ProjectFactory.MIN_DIM, ProjectFactory.MAX_DIM),
        fps = fps.coerceIn(ProjectFactory.MIN_FPS, ProjectFactory.MAX_FPS),
        background = background,
        updatedAt = System.currentTimeMillis()
    )

    private fun nextLayerName(frame: Frame): String {
        val used = frame.layers.mapNotNull { l ->
            Regex("^Layer (\\d+)$").find(l.name)?.groupValues?.get(1)?.toIntOrNull()
        }.toSet()
        var n = 1
        while (n in used) n++
        return "Layer $n"
    }

    fun clampLayerIndex(frame: Frame, index: Int): Int = min(max(0, index), frame.layers.size - 1)
}
