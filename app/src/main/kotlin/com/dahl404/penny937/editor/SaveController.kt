package com.dahl404.penny937.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dahl404.penny937.data.ProjectStore
import com.dahl404.penny937.model.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns autosave scheduling, write serialisation and the visible save state.
 * The editor only provides the current document snapshot.
 */
class SaveController(
    private val scope: CoroutineScope,
    private val store: ProjectStore,
    private val project: () -> Project,
    private val currentFrame: () -> Int
) {
    var lastSavedAt by mutableStateOf(0L)
        private set
    var saving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set

    private var saveJob: Job? = null
    private var disposed = false
    private var discarded = false
    private val saveMutex = Mutex()

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

    /** Retries a failed autosave without waiting for the next edit. */
    fun retrySave() {
        if (disposed || discarded) return
        saveNow()
    }

    private suspend fun persist(): Boolean {
        if (discarded) return false
        val snapshot = project()
        val thumbFrame = snapshot.frameAt(currentFrame().coerceIn(0, snapshot.frames.lastIndex))
        saving = true
        saveError = null
        val saved = withContext(Dispatchers.IO) {
            saveMutex.withLock {
                if (discarded) {
                    false
                } else {
                    val documentSaved = store.save(snapshot)
                    if (documentSaved) store.saveThumbnail(snapshot, thumbFrame)
                    documentSaved
                }
            }
        }
        if (!discarded) {
            withContext(Dispatchers.Main) {
                saving = false
                if (saved) {
                    lastSavedAt = System.currentTimeMillis()
                    saveError = null
                } else {
                    saveError = "Could not save. Check storage space and try again."
                }
            }
        }
        return saved
    }

    /** Flushes once, then stops accepting further saves. */
    fun dispose() {
        if (disposed) return
        saveNow()
        disposed = true
    }

    fun discard() {
        discarded = true
        disposed = true
        saveJob?.cancel()
    }

    suspend fun deleteCurrentProject() {
        val id = project().id
        saveMutex.withLock {
            withContext(Dispatchers.IO) { store.delete(id) }
        }
    }
}
