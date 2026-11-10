package com.dahl404.penny937

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dahl404.penny937.data.Prefs
import com.dahl404.penny937.data.ProjectMeta
import com.dahl404.penny937.data.ReferenceFiles
import com.dahl404.penny937.data.ProjectStore
import com.dahl404.penny937.editor.EditorModel
import com.dahl404.penny937.model.Project
import com.dahl404.penny937.model.ProjectFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PennyViewModel(application: Application) : AndroidViewModel(application) {

    val store = ProjectStore(application)
    val prefs = Prefs(application)

    private val editorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var projects by mutableStateOf<List<ProjectMeta>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var editor by mutableStateOf<EditorModel?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            val list = withContext(Dispatchers.IO) { store.list() }
            projects = list
            loading = false
        }
    }

    fun createProject(name: String, width: Int, height: Int, fps: Int, background: Int) {
        editorScope.launch {
            val project = ProjectFactory.create(name, width, height, fps, background)
            withContext(Dispatchers.IO) { store.save(project) }
            prefs.lastProjectId = project.id
            editor = newEditor(project)
            refresh()
        }
    }

    fun importArchive(uri: Uri) {
        editorScope.launch {
            val imported = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.use { store.importArchive(it) }
            } ?: return@launch
            prefs.lastProjectId = imported.id
            editor = newEditor(imported)
            refresh()
        }
    }

    fun openProject(id: Long) {
        editorScope.launch {
            val project = withContext(Dispatchers.IO) { store.load(id) } ?: return@launch
            prefs.lastProjectId = id
            editor = newEditor(project)
        }
    }

    fun closeEditor() {
        val model = editor ?: return
        model.dispose()
        editor = null
        editorScope.launch {
            delay(450)
            refresh()
        }
    }

    fun duplicateProject(id: Long) {
        editorScope.launch {
            val source = withContext(Dispatchers.IO) { store.load(id) } ?: return@launch
            val copy = ProjectFactory.duplicateProject(source)
            withContext(Dispatchers.IO) {
                ReferenceFiles.copyProject(source.id, copy.id, source.referencedFiles())
                store.save(copy)
            }
            refresh()
        }
    }

    fun duplicateAndOpen(project: Project) {
        editorScope.launch {
            val copy = ProjectFactory.duplicateProject(project)
            withContext(Dispatchers.IO) {
                ReferenceFiles.copyProject(project.id, copy.id, project.referencedFiles())
                store.save(copy)
            }
            editor?.dispose()
            prefs.lastProjectId = copy.id
            editor = newEditor(copy)
            refresh()
        }
    }

    fun deleteProject(id: Long) {
        if (editor?.project?.id == id) {
            editor?.discard()
            editor = null
        }
        if (prefs.lastProjectId == id) prefs.lastProjectId = null
        editorScope.launch {
            withContext(Dispatchers.IO) { store.delete(id) }
            prefs.setViewportFor(id, null)
            refresh()
        }
    }

    fun deleteCurrentAndClose() {
        val model = editor ?: return
        val id = model.project.id
        editor = null
        if (prefs.lastProjectId == id) prefs.lastProjectId = null
        editorScope.launch {
            model.purge()
            prefs.setViewportFor(id, null)
            refresh()
        }
    }

    fun saveNow() {
        editor?.saveNow()
    }

    private fun newEditor(project: Project): EditorModel = EditorModel(
        context = getApplication(),
        store = store,
        prefs = prefs,
        initial = project,
        scope = editorScope
    )

    override fun onCleared() {
        editor?.saveNow()
        super.onCleared()
    }
}
