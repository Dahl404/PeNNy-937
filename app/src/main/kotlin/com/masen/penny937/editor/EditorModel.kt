package com.masen.penny937.editor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.masen.penny937.data.Prefs
import com.masen.penny937.data.ProjectStore
import com.masen.penny937.export.ExportFormat
import com.masen.penny937.export.Exporter
import com.masen.penny937.model.Project
import com.masen.penny937.model.ProjectOps
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
    var onionEnabled by mutableStateOf(prefs.onionEnabled)
        private set

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
        var updated = ProjectOps.addStroke(project, frameIndex, layerIndex, stroke)
        if (!layer.visible) {
            updated = ProjectOps.updateLayer(updated, frameIndex, layerIndex) {
                it.copy(visible = true)
            }
        }
        apply(updated, currentFrame, currentLayer)
    }

    fun undo() {
        cancelActiveInk()
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

    fun toggleOnion() {
        onionEnabled = !onionEnabled
        prefs.onionEnabled = onionEnabled
    }

    // ---------------------------------------------------------------- frames

    fun selectFrame(index: Int) {
        cancelActiveInk()
        currentFrame = index.coerceIn(0, project.frames.lastIndex)
        currentLayer = currentLayer.coerceIn(0, frame.layers.lastIndex)
        if (isPlaying) stopPlayback()
    }

    fun selectLayer(index: Int) {
        currentLayer = index.coerceIn(0, frame.layers.lastIndex)
    }

    fun addFrame() {
        cancelActiveInk()
        apply(ProjectOps.addFrameAfter(project, currentFrame), currentFrame + 1, currentLayer)
    }

    fun duplicateFrame() {
        cancelActiveInk()
        apply(
            ProjectOps.duplicateFrameAfter(project, currentFrame),
            currentFrame + 1,
            currentLayer
        )
    }

    fun deleteFrame() {
        if (project.frames.size <= 1) return
        cancelActiveInk()
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
        cancelActiveInk()
        val (updated, index) = ProjectOps.deleteLayer(project, currentFrame, currentLayer)
        apply(updated, currentFrame, index)
    }

    fun mergeLayerDown() {
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
        saveNow()
        disposed = true
        exportJob?.cancel()
    }

    var discarded = false
        private set

    fun discard() {
        discarded = true
        disposed = true
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
