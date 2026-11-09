package com.masen.penny937.editor

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.masen.penny937.data.Prefs
import com.masen.penny937.data.ProjectStore
import com.masen.penny937.data.ExtractedVideo
import com.masen.penny937.data.ReferenceFiles
import com.masen.penny937.data.VideoReferenceExtractor
import com.masen.penny937.export.ExportFormat
import com.masen.penny937.export.Exporter
import com.masen.penny937.model.Project
import com.masen.penny937.model.ProjectOps
import com.masen.penny937.model.GridStyle
import com.masen.penny937.model.Ids
import com.masen.penny937.model.Layer
import com.masen.penny937.model.LayerImage
import com.masen.penny937.model.ShapeKind
import com.masen.penny937.model.Stroke
import com.masen.penny937.model.Tool
import com.masen.penny937.paint.FrameRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

data class ReferencePlacement(
    val fileName: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val targetFrame: Int,
    val targetLayerId: Long?,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f
) {
    val isNew: Boolean get() = targetLayerId == null
}

/** A live, uncommitted translation of one layer's contents. */
data class LayerShiftState(
    val frameIndex: Int,
    val layerId: Long,
    val dx: Float = 0f,
    val dy: Float = 0f
)

data class EditorHint(val id: Int, val text: String)

data class EyedropperState(
    val frameIndex: Int,
    val layerId: Long,
    val layerName: String,
    val originalColor: Int
)

data class VideoImportState(
    val done: Int,
    val total: Int,
    val preparing: Boolean = false
)

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float) : ExportState
    data class Done(val file: File, val mime: String) : ExportState
    data class Failed(val message: String) : ExportState
}

class EditorModel(
    private val context: Context,
    private val store: ProjectStore,
    private val prefs: Prefs,
    initial: Project,
    private val scope: CoroutineScope
) {
    private data class Snapshot(val project: Project, val frame: Int, val layer: Int)

    var project by mutableStateOf(initial)
        private set
    var currentFrame by mutableStateOf(0)
        private set
    var currentLayer by mutableStateOf(initial.frames[0].layers.lastIndex)
        private set

    var tool by mutableStateOf(prefs.lastTool)
        private set
    var color by mutableStateOf(prefs.lastColor)
    var size by mutableStateOf(prefs.sizeFor(prefs.lastTool))
        private set
    var markerOpacity by mutableStateOf(prefs.opacityFor(Tool.MARKER))
        private set
    var pressureEnabled by mutableStateOf(prefs.pressureEnabled)
        private set
    var stylusOnly by mutableStateOf(prefs.stylusOnly)
        private set
    var onionStrength by mutableStateOf(prefs.onionStrength)
        private set
    var gridEnabled by mutableStateOf(prefs.gridEnabled)
        private set
    var gridStyle by mutableStateOf(prefs.gridStyle)
        private set
    var shapeKind by mutableStateOf(prefs.shapeKind)
        private set
    var placement by mutableStateOf<ReferencePlacement?>(null)
        private set
    var layerShift by mutableStateOf<LayerShiftState?>(null)
        private set
    var importingReference by mutableStateOf(false)
        private set
    var eyedropper by mutableStateOf<EyedropperState?>(null)
        private set
    var hint by mutableStateOf<EditorHint?>(null)
        private set
    var videoImport by mutableStateOf<VideoImportState?>(null)
        private set
    private var hintCounter = 0
    private var videoImportJob: Job? = null
    private var lastVideoStart: Int? = null
    private var lastVideoEnd: Int? = null

    var showGestureHint by mutableStateOf(!prefs.hintSeen)
        private set

    var isPlaying by mutableStateOf(false)
        private set
    var previewFrame by mutableStateOf(0)
        private set

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var lastSavedAt by mutableStateOf(0L)
        private set

    var exportState by mutableStateOf<ExportState>(ExportState.Idle)
        private set

    val renderer = FrameRenderer()

    /**
     * Everything the drawing view reads from the model. Reading this in a
     * composable scope keeps the canvas repainting while the onion slider,
     * grid, frame, layer, ink or playback state changes.
     */
    val canvasRevision: Int
        get() {
            var h = project.updatedAt.hashCode()
            h = h * 31 + project.frames.size
            h = h * 31 + project.frames.sumOf { it.layers.size }
            h = h * 31 + project.frames.sumOf { it.strokeCount }
            h = h * 31 + project.width
            h = h * 31 + project.height
            h = h * 31 + project.background
            h = h * 31 + currentFrame
            h = h * 31 + currentLayer
            h = h * 31 + tool.ordinal
            h = h * 31 + color
            h = h * 31 + size.toBits()
            h = h * 31 + markerOpacity.toBits()
            h = h * 31 + (if (pressureEnabled) 1 else 0)
            h = h * 31 + (if (stylusOnly) 1 else 0)
            h = h * 31 + onionStrength.toBits()
            h = h * 31 + (if (gridEnabled) 1 else 0)
            h = h * 31 + gridStyle.ordinal
            h = h * 31 + (if (isPlaying) 1 else 0)
            h = h * 31 + previewFrame
            h = h * 31 + (placement?.hashCode() ?: 0)
            h = h * 31 + (if (eyedropper != null) 1 else 0)
            return h
        }

    /** The canvas installs this so structural edits never race a stroke in progress. */
    var onBeforeStructuralChange: (() -> Unit)? = null

    private fun cancelActiveInk() {
        onBeforeStructuralChange?.invoke()
    }

    val frame get() = project.frameAt(currentFrame)
    val layer get() = frame.layers[currentLayer.coerceIn(0, frame.layers.lastIndex)]

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var saveJob: Job? = null
    private var exportJob: Job? = null
    private var disposed = false
    private val saveMutex = Mutex()
    private var lastOpacityUndoAt = 0L

    init {
        val frame = project.frameAt(currentFrame)
        currentLayer = frame.layers.lastIndex
        val paperLum = android.graphics.Color.luminance(initial.background)
        val inkLum = android.graphics.Color.luminance(color)
        if (paperLum < 0.25f && inkLum < 0.3f) {
            color = 0xFFF5F5F7.toInt()
        }
    }

    fun snapshotForRestore(): Project = project

    // ---------------------------------------------------------------- strokes

    fun ensureLayerVisibleForInk() {
        if (layer.locked || layer.isReference) return
        if (!layer.visible) {
            project = ProjectOps.updateLayer(project, currentFrame, currentLayer) {
                it.copy(visible = true)
            }
        }
    }

    fun addStroke(
        stroke: Stroke,
        frameIndex: Int = currentFrame,
        layerIndex: Int = currentLayer
    ) {
        if (frameIndex !in project.frames.indices) return
        val targetFrame = project.frameAt(frameIndex)
        if (layerIndex !in targetFrame.layers.indices) return
        val layer = project.layerAt(frameIndex, layerIndex)
        if (layer.locked) {
            showHint("Layer is locked")
            return
        }
        if (layer.isReference) {
            showHint("Reference layers are not drawable")
            return
        }
        var updated = ProjectOps.addStroke(project, frameIndex, layerIndex, stroke)
        if (!layer.visible) {
            updated = ProjectOps.updateLayer(updated, frameIndex, layerIndex) {
                it.copy(visible = true)
            }
        }
        apply(updated, currentFrame, currentLayer)
    }

    private fun clearVideoRange() {
        lastVideoStart = null
        lastVideoEnd = null
    }

    fun undo() {
        cancelActiveInk()
        cancelLayerShift()
        clearVideoRange()
        renderer.invalidateComposites()
        val snap = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(Snapshot(project, currentFrame, currentLayer))
        restore(snap)
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
        scheduleSave()
    }

    fun redo() {
        cancelActiveInk()
        cancelLayerShift()
        clearVideoRange()
        renderer.invalidateComposites()
        val snap = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(Snapshot(project, currentFrame, currentLayer))
        restore(snap)
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
        scheduleSave()
    }

    private fun restore(snap: Snapshot) {
        project = snap.project
        currentFrame = snap.frame.coerceIn(0, project.frames.lastIndex)
        currentLayer = snap.layer.coerceIn(0, project.frameAt(currentFrame).layers.lastIndex)
        if (isPlaying) stopPlayback()
    }

    private fun pushUndo() {
        undoStack.addLast(Snapshot(project, currentFrame, currentLayer))
        while (undoStack.size > 80) undoStack.removeFirst()
        redoStack.clear()
        canUndo = true
        canRedo = false
    }

    private fun apply(
        newProject: Project,
        frameIndex: Int = currentFrame,
        layerIndex: Int = currentLayer,
        recordUndo: Boolean = true
    ) {
        if (recordUndo) pushUndo()
        project = newProject
        currentFrame = frameIndex.coerceIn(0, newProject.frames.lastIndex)
        val f = newProject.frameAt(currentFrame)
        currentLayer = layerIndex.coerceIn(0, f.layers.lastIndex)
        if (isPlaying) stopPlayback()
        scheduleSave()
    }

    fun showHint(text: String) {
        hint = EditorHint(++hintCounter, text)
    }

    fun dismissHint(id: Int) {
        if (hint?.id == id) hint = null
    }

    // ------------------------------------------------------------- references

    fun importReference(uri: Uri) {
        if (placement != null || importingReference || videoImport != null) return
        if (eyedropper != null) cancelEyedropper()
        cancelActiveInk()
        if (isPlaying) stopPlayback()
        val targetFrame = currentFrame
        importingReference = true
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                ReferenceFiles.import(context, uri)
            }
            importingReference = false
            if (imported == null) {
                showHint("Could not import that image")
                return@launch
            }
            cancelActiveInk()
            if (isPlaying) stopPlayback()
            if (currentFrame != targetFrame) {
                currentFrame = targetFrame.coerceIn(0, project.frames.lastIndex)
                currentLayer = currentLayer.coerceIn(0, frame.layers.lastIndex)
            }
            val p = project
            val aspect = imported.width.toFloat() / imported.height.toFloat()
            val maxW = p.width * 0.7f
            val maxH = p.height * 0.7f
            val w = min(maxW, maxH * aspect).coerceAtLeast(16f)
            val h = (w / aspect).coerceAtLeast(16f)
            placement = ReferencePlacement(
                fileName = imported.fileName,
                sourceWidth = imported.width,
                sourceHeight = imported.height,
                targetFrame = targetFrame,
                targetLayerId = null,
                centerX = p.width / 2f,
                centerY = p.height / 2f,
                width = w,
                height = h,
                rotation = 0f
            )
        }
    }

    fun beginReposition(layer: Layer) {
        if (videoImport != null) return
        if (eyedropper != null) cancelEyedropper()
        val image = layer.image ?: return
        cancelActiveInk()
        if (isPlaying) stopPlayback()
        placement = ReferencePlacement(
            fileName = image.file,
            sourceWidth = image.sourceWidth,
            sourceHeight = image.sourceHeight,
            targetFrame = currentFrame,
            targetLayerId = layer.id,
            centerX = image.centerX,
            centerY = image.centerY,
            width = image.width,
            height = image.height,
            rotation = image.rotation
        )
    }

    fun updatePlacement(
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        rotation: Float
    ) {
        val current = placement ?: return
        val maxWidth = project.width * 4f
        val newWidth = width.coerceIn(16f, maxWidth)
        val scale = if (width > 0.001f) newWidth / width else 1f
        placement = current.copy(
            centerX = centerX,
            centerY = centerY,
            width = newWidth,
            height = (height * scale).coerceAtLeast(16f),
            rotation = rotation
        )
    }

    fun confirmPlacement() {
        val current = placement ?: return
        val image = LayerImage(
            file = current.fileName,
            sourceWidth = current.sourceWidth,
            sourceHeight = current.sourceHeight,
            centerX = current.centerX,
            centerY = current.centerY,
            width = current.width,
            height = current.height,
            rotation = current.rotation
        )
        val existingId = current.targetLayerId
        if (existingId == null) {
            val frameIndex = current.targetFrame.coerceIn(0, project.frames.lastIndex)
            val frame = project.frameAt(frameIndex)
            val name = referenceName(frame)
            val layer = com.masen.penny937.model.ProjectFactory.referenceLayer(name, image)
            val updated = ProjectOps.addReferenceLayer(project, frameIndex, layer)
            val selected = if (frameIndex == currentFrame) {
                (currentLayer + 1).coerceIn(0, updated.frameAt(frameIndex).layers.lastIndex)
            } else {
                0
            }
            placement = null
            apply(updated, frameIndex, selected)
        } else {
            val frameIndex = current.targetFrame.coerceIn(0, project.frames.lastIndex)
            val index = project.frameAt(frameIndex).layers.indexOfFirst { it.id == existingId }
            if (index < 0) {
                placement = null
                showHint("That reference layer is gone")
                return
            }
            val updated = ProjectOps.updateLayerImage(project, frameIndex, index, image)
            placement = null
            apply(updated, frameIndex, currentLayer)
        }
    }

    fun importVideoReference(uri: Uri) {
        if (placement != null || importingReference || videoImport != null) return
        if (eyedropper != null) cancelEyedropper()
        cancelActiveInk()
        if (isPlaying) stopPlayback()
        val start = lastVideoStart?.let { previousStart ->
            val previousEnd = lastVideoEnd
            if (previousEnd != null && currentFrame in previousStart..previousEnd) {
                previousEnd + 1
            } else {
                currentFrame
            }
        } ?: currentFrame
        val startFrame = start.coerceIn(0, project.frames.size)
        val targetFps = project.fps
        videoImport = VideoImportState(done = 0, total = 0, preparing = true)
        videoImportJob = scope.launch {
            try {
                val extracted: ExtractedVideo = VideoReferenceExtractor.extract(
                    context = context,
                    uri = uri,
                    targetFps = targetFps,
                    onTotal = { total ->
                        withContext(Dispatchers.Main) {
                            videoImport = VideoImportState(done = 0, total = total)
                        }
                    },
                    onProgress = { done ->
                        withContext(Dispatchers.Main) {
                            videoImport = videoImport?.copy(done = done)
                        }
                    }
                )
                val current = project
                val first = extracted.frames.firstOrNull()
                    ?: throw IllegalStateException("No video frames were extracted")
                val scale = min(
                    current.width.toFloat() / first.width,
                    current.height.toFloat() / first.height
                )
                val displayWidth = (first.width * scale).coerceAtLeast(16f)
                val displayHeight = (first.height * scale).coerceAtLeast(16f)
                val centerX = current.width / 2f
                val centerY = current.height / 2f
                val images = extracted.frames.map { frame ->
                    LayerImage(
                        file = frame.file,
                        sourceWidth = frame.width,
                        sourceHeight = frame.height,
                        centerX = centerX,
                        centerY = centerY,
                        width = displayWidth,
                        height = displayHeight,
                        rotation = 0f
                    )
                }
                val insertion = ProjectOps.insertVideoReferenceFrames(
                    project = current,
                    startFrame = startFrame,
                    images = images,
                    layerName = "Video reference"
                )
                lastVideoStart = insertion.startFrame
                lastVideoEnd = insertion.endFrame
                videoImport = null
                apply(insertion.project, insertion.startFrame, insertion.selectedLayer)
                showHint(
                    "Video reference · ${images.size} frames " +
                        "(${insertion.startFrame + 1}–${insertion.endFrame + 1})"
                )
            } catch (c: kotlinx.coroutines.CancellationException) {
                videoImport = null
                throw c
            } catch (t: Throwable) {
                videoImport = null
                showHint(t.message ?: "Could not import that video")
            }
        }
    }

    fun cancelVideoImport() {
        videoImportJob?.cancel()
        videoImportJob = null
        videoImport = null
    }

    fun cancelPlacement() {
        val current = placement ?: return
        if (current.isNew) ReferenceFiles.delete(current.fileName)
        placement = null
    }

    // -------------------------------------------------------------- layer shift

    /**
     * Starts a free translation of the selected layer. The layer is not changed
     * until [commitLayerShift], so a whole drag is one undoable edit and cancel
     * leaves the project untouched.
     */
    fun beginLayerShift() {
        if (placement != null || importingReference || videoImport != null) return
        if (eyedropper != null) cancelEyedropper()
        val target = layer
        if (target.locked) {
            showHint("Unlock the layer to shift it")
            return
        }
        if (!target.visible) {
            showHint("Make the layer visible to shift it")
            return
        }
        cancelActiveInk()
        if (isPlaying) stopPlayback()
        layerShift = LayerShiftState(
            frameIndex = currentFrame,
            layerId = target.id
        )
    }

    fun updateLayerShift(dx: Float, dy: Float) {
        val current = layerShift ?: return
        layerShift = current.copy(dx = current.dx + dx, dy = current.dy + dy)
    }

    fun commitLayerShift() {
        val current = layerShift ?: return
        layerShift = null
        if (abs(current.dx) < 0.01f && abs(current.dy) < 0.01f) return
        val frame = project.frameAt(current.frameIndex)
        val index = frame.layers.indexOfFirst { it.id == current.layerId }
        if (index < 0) return
        val target = frame.layers[index]
        if (target.strokes.isEmpty() && target.image == null) return
        val updated = ProjectOps.translateLayer(
            project, current.frameIndex, index, current.dx, current.dy
        )
        if (updated === project) return
        // The cached bitmap still shows the old position.
        renderer.invalidateLayer(current.layerId)
        renderer.invalidateComposites()
        apply(updated, current.frameIndex, index)
    }

    fun cancelLayerShift() {
        if (layerShift != null) layerShift = null
    }

    fun setLayerLocked(index: Int, locked: Boolean) {
        if (index !in frame.layers.indices) return
        apply(
            ProjectOps.updateLayer(project, currentFrame, index) {
                it.copy(locked = locked)
            }
        )
    }

    private fun referenceName(frame: com.masen.penny937.model.Frame): String {
        val count = frame.layers.count { it.isReference }
        return if (count == 0) "Reference" else "Reference ${count + 1}"
    }

    // ---------------------------------------------------------------- tools

    fun chooseTool(newTool: Tool) {
        if (newTool == tool) return
        prefs.setSizeFor(tool, size)
        if (tool == Tool.MARKER) prefs.setOpacityFor(tool, markerOpacity)
        tool = newTool
        size = prefs.sizeFor(newTool)
        if (newTool == Tool.MARKER) markerOpacity = prefs.opacityFor(newTool)
        prefs.lastTool = newTool
    }

    fun chooseColor(value: Int) {
        color = value
        prefs.lastColor = value
    }

    /** Eyedropper: live sampler for the currently selected layer only. */
    private var eyedropperFound = false

    fun beginEyedropper(): Boolean {
        if (eyedropper != null) return true
        if (placement != null || importingReference || videoImport != null) return false
        if (isPlaying) stopPlayback()
        cancelActiveInk()
        val target = layer
        hint = null
        eyedropper = EyedropperState(
            frameIndex = currentFrame,
            layerId = target.id,
            layerName = target.name,
            originalColor = color
        )
        eyedropperFound = false
        return true
    }

    fun sampleEyedropper(x: Float, y: Float, radius: Float): Boolean {
        val state = eyedropper ?: return false
        val frame = project.frameAt(state.frameIndex)
        val target = frame.layers.firstOrNull { it.id == state.layerId } ?: return false
        val bitmap = renderer.editableLayerBitmap(project, target)
        if (bitmap.width <= 0 || bitmap.height <= 0) return false
        val cx = x.roundToInt().coerceIn(0, bitmap.width - 1)
        val cy = y.roundToInt().coerceIn(0, bitmap.height - 1)
        val r = radius.roundToInt().coerceIn(0, 64)
        val left = (cx - r).coerceAtLeast(0)
        val top = (cy - r).coerceAtLeast(0)
        val right = (cx + r).coerceAtMost(bitmap.width - 1)
        val bottom = (cy + r).coerceAtMost(bitmap.height - 1)
        val w = right - left + 1
        val h = bottom - top + 1
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, left, top, w, h)
        val picked = com.masen.penny937.paint.ColorSampler.nearestOpaqueColor(
            pixels, w, h, cx - left, cy - top
        ) ?: return false
        color = picked
        eyedropperFound = true
        return true
    }

    fun finishEyedropper() {
        val state = eyedropper ?: return
        eyedropper = null
        if (eyedropperFound) {
            prefs.lastColor = color
            showHint(
                "Picked #${String.format("%06X", 0xFFFFFF and color)} from ${state.layerName}"
            )
        } else {
            color = state.originalColor
            showHint("Nothing to sample on ${state.layerName}")
        }
        eyedropperFound = false
    }

    fun cancelEyedropper() {
        val state = eyedropper ?: return
        color = state.originalColor
        eyedropper = null
        eyedropperFound = false
    }

    fun changeSize(value: Float) {
        size = value.coerceIn(tool.minWidth(), tool.maxWidth())
        prefs.setSizeFor(tool, size)
    }

    fun changeMarkerOpacity(value: Float) {
        markerOpacity = value.coerceIn(0.05f, 0.9f)
        prefs.setOpacityFor(Tool.MARKER, markerOpacity)
    }

    fun togglePressure() {
        pressureEnabled = !pressureEnabled
        prefs.pressureEnabled = pressureEnabled
    }

    fun toggleStylusOnly() {
        stylusOnly = !stylusOnly
        prefs.stylusOnly = stylusOnly
    }

    fun dismissGestureHint() {
        showGestureHint = false
        prefs.hintSeen = true
    }

    fun updateOnionStrength(strength: Float) {
        onionStrength = strength.coerceIn(-1f, 1f)
        prefs.onionStrength = onionStrength
    }

    fun toggleGrid() {
        gridEnabled = !gridEnabled
        prefs.gridEnabled = gridEnabled
    }

    fun chooseGridStyle(style: GridStyle) {
        gridStyle = style
        prefs.gridStyle = style
    }

    fun chooseShape(kind: ShapeKind) {
        shapeKind = kind
        prefs.shapeKind = kind
    }

    // ---------------------------------------------------------------- frames

    fun selectFrame(index: Int) {
        if (eyedropper != null) cancelEyedropper()
        cancelActiveInk()
        cancelLayerShift()
        currentFrame = index.coerceIn(0, project.frames.lastIndex)
        currentLayer = currentLayer.coerceIn(0, frame.layers.lastIndex)
        if (isPlaying) stopPlayback()
    }

    fun selectLayer(index: Int) {
        if (eyedropper != null) cancelEyedropper()
        cancelLayerShift()
        currentLayer = index.coerceIn(0, frame.layers.lastIndex)
    }

    fun addFrame() {
        cancelActiveInk()
        clearVideoRange()
        apply(ProjectOps.addFrameAfter(project, currentFrame), currentFrame + 1, currentLayer)
    }

    fun duplicateFrame() {
        cancelActiveInk()
        clearVideoRange()
        apply(
            ProjectOps.duplicateFrameAfter(project, currentFrame),
            currentFrame + 1,
            currentLayer
        )
    }

    fun moveFrame(from: Int, to: Int) {
        if (from == to) return
        cancelActiveInk()
        clearVideoRange()
        val updated = ProjectOps.moveFrame(project, from, to)
        if (updated === project) return
        apply(updated, to, currentLayer)
    }

    fun deleteFrame() {
        if (project.frames.size <= 1) return
        cancelActiveInk()
        clearVideoRange()
        val target = (currentFrame - 1).coerceAtLeast(0)
        apply(ProjectOps.deleteFrame(project, currentFrame), target, currentLayer)
    }

    fun setFrameHold(hold: Int) {
        apply(ProjectOps.setHold(project, currentFrame, hold))
    }

    // ---------------------------------------------------------------- layers

    fun addLayer() {
        cancelActiveInk()
        val (updated, index) = ProjectOps.addLayer(project, currentFrame, currentLayer)
        apply(updated, currentFrame, index)
    }

    fun duplicateLayer() {
        cancelActiveInk()
        val (updated, index) = ProjectOps.duplicateLayer(project, currentFrame, currentLayer)
        apply(updated, currentFrame, index)
    }

    fun deleteLayer() {
        if (layer.locked) {
            showHint("Unlock the layer to delete it")
            return
        }
        cancelActiveInk()
        val (updated, index) = ProjectOps.deleteLayer(project, currentFrame, currentLayer)
        apply(updated, currentFrame, index)
    }

    fun mergeLayerDown() {
        val lower = frame.layers.getOrNull(currentLayer - 1)
        if (layer.locked || layer.isReference || lower == null || lower.locked || lower.isReference) {
            showHint("Locked and reference layers cannot be merged")
            return
        }
        cancelActiveInk()
        val (updated, index) = ProjectOps.mergeLayerDown(project, currentFrame, currentLayer)
        apply(updated, currentFrame, index)
    }

    fun moveLayerUp() {
        if (currentLayer >= frame.layers.lastIndex) return
        cancelActiveInk()
        val (updated, index) = ProjectOps.moveLayer(project, currentFrame, currentLayer, currentLayer + 1)
        apply(updated, currentFrame, index)
    }

    fun moveLayerDown() {
        if (currentLayer <= 0) return
        cancelActiveInk()
        val (updated, index) = ProjectOps.moveLayer(project, currentFrame, currentLayer, currentLayer - 1)
        apply(updated, currentFrame, index)
    }

    fun setLayerVisible(index: Int, visible: Boolean) {
        apply(ProjectOps.updateLayer(project, currentFrame, index) { it.copy(visible = visible) })
    }

    fun toggleActiveLayerVisible() {
        val layer = layer
        setLayerVisible(currentLayer, !layer.visible)
    }

    fun setLayerOpacity(value: Float) {
        val now = System.currentTimeMillis()
        val recordUndo = now - lastOpacityUndoAt > 600
        if (recordUndo) {
            lastOpacityUndoAt = now
            pushUndo()
        }
        project = ProjectOps.updateLayer(project, currentFrame, currentLayer) {
            it.copy(opacity = value.coerceIn(0.05f, 1f))
        }
        scheduleSave()
    }

    fun clearLayer() {
        if (layer.locked) {
            showHint("Layer is locked")
            return
        }
        if (layer.isReference) {
            showHint("Reference layers cannot be cleared")
            return
        }
        cancelActiveInk()
        apply(ProjectOps.clearLayer(project, currentFrame, currentLayer))
    }

    // ---------------------------------------------------------------- settings

    fun updateSettings(name: String, width: Int, height: Int, fps: Int, background: Int) {
        cancelActiveInk()
        val dimsChanged = width != project.width || height != project.height
        if (dimsChanged) renderer.invalidateAll()
        else renderer.invalidateComposites()
        val updated = ProjectOps.updateSettings(project, name, width, height, fps, background)
        apply(updated)
    }

    // ---------------------------------------------------------------- playback

    fun togglePlayback() {
        if (eyedropper != null) cancelEyedropper()
        cancelActiveInk()
        if (isPlaying) {
            stopPlayback()
        } else if (project.frames.size > 1) {
            previewFrame = 0
            isPlaying = true
        }
    }

    fun stopPlayback() {
        isPlaying = false
        previewFrame = currentFrame
    }

    fun updatePreviewFrame(index: Int) {
        previewFrame = index.coerceIn(0, project.frames.lastIndex)
    }

    // ---------------------------------------------------------------- save

    fun scheduleSave() {
        if (disposed) return
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(900)
            persist()
        }
    }

    fun saveNow() {
        if (disposed) return
        saveJob?.cancel()
        saveJob = scope.launch { persist() }
    }

    private suspend fun persist() {
        if (discarded) return
        val snapshot = project
        val thumbFrame = snapshot.frameAt(currentFrame.coerceIn(0, snapshot.frames.lastIndex))
        withContext(Dispatchers.IO) {
            saveMutex.withLock {
                if (discarded) return@withLock
                store.save(snapshot)
                store.saveThumbnail(snapshot, thumbFrame)
            }
        }
        if (!discarded) {
            withContext(Dispatchers.Main) {
                lastSavedAt = System.currentTimeMillis()
            }
        }
    }

    fun dispose() {
        if (disposed) return
        videoImportJob?.cancel()
        saveNow()
        disposed = true
        exportJob?.cancel()
    }

    var discarded = false
        private set

    fun discard() {
        discarded = true
        disposed = true
        videoImportJob?.cancel()
        saveJob?.cancel()
        exportJob?.cancel()
    }

    suspend fun purge() {
        discard()
        saveMutex.withLock {
            withContext(Dispatchers.IO) { store.delete(project.id) }
        }
    }

    // ---------------------------------------------------------------- export

    fun startExport(format: ExportFormat, targetWidth: Int) {
        if (exportState is ExportState.Running) return
        cancelActiveInk()
        exportState = ExportState.Running(0f)
        val snapshot = project
        val frameIndex = currentFrame
        exportJob = scope.launch {
            try {
                val file = Exporter.export(
                    context = context,
                    project = snapshot,
                    currentFrame = frameIndex,
                    format = format,
                    targetWidth = targetWidth
                ) { progress ->
                    withContext(Dispatchers.Main) {
                        exportState = ExportState.Running(progress)
                    }
                }
                exportState = ExportState.Done(file, format.mime)
            } catch (c: kotlinx.coroutines.CancellationException) {
                exportState = ExportState.Idle
            } catch (t: Throwable) {
                exportState = ExportState.Failed(t.message ?: "Export failed")
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportState = ExportState.Idle
    }

    fun dismissExport() {
        if (exportState !is ExportState.Running) exportState = ExportState.Idle
    }
}
