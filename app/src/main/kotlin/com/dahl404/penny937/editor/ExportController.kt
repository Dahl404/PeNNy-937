package com.dahl404.penny937.editor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dahl404.penny937.export.ExportFormat
import com.dahl404.penny937.export.Exporter
import com.dahl404.penny937.model.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float) : ExportState
    data class Done(val file: File, val mime: String) : ExportState
    data class Failed(val message: String) : ExportState
}

/** Owns the export job and its observable progress/result state. */
class ExportController(
    private val scope: CoroutineScope,
    private val context: Context,
    private val project: () -> Project,
    private val currentFrame: () -> Int
) {
    var state by mutableStateOf<ExportState>(ExportState.Idle)
        private set

    private var exportJob: Job? = null

    fun start(format: ExportFormat, targetWidth: Int) {
        if (state is ExportState.Running) return
        state = ExportState.Running(0f)
        val snapshot = project()
        val frameIndex = currentFrame()
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
                        state = ExportState.Running(progress)
                    }
                }
                state = ExportState.Done(file, format.mime)
            } catch (c: kotlinx.coroutines.CancellationException) {
                state = ExportState.Idle
            } catch (t: Throwable) {
                state = ExportState.Failed(t.message ?: "Export failed")
            }
        }
    }

    fun cancel() {
        exportJob?.cancel()
        exportJob = null
        state = ExportState.Idle
    }

    fun dismiss() {
        if (state !is ExportState.Running) state = ExportState.Idle
    }

    fun dispose() {
        exportJob?.cancel()
        exportJob = null
    }
}
