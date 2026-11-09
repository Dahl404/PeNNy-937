package com.masen.penny937.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masen.penny937.PennyViewModel
import com.masen.penny937.ui.editor.EditorScreen
import com.masen.penny937.ui.theme.LocalPenny

@Composable
fun PennyApp(vm: PennyViewModel = viewModel()) {
    val palette = LocalPenny.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.saveNow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        AnimatedContent(
            targetState = vm.editor,
            transitionSpec = {
                val enteringEditor = targetState != null
                (fadeIn(tween(240)) + slideInHorizontally(tween(320)) { value ->
                    if (enteringEditor) value / 12 else -value / 12
                }).togetherWith(fadeOut(tween(180)))
            },
            label = "screen"
        ) { model ->
            if (model != null) {
                EditorScreen(
                    editor = model,
                    onClose = { vm.closeEditor() },
                    onDuplicateProject = { vm.duplicateAndOpen(model.project) },
                    onDeleteProject = { vm.deleteCurrentAndClose() }
                )
            } else {
                HomeScreen(
                    store = vm.store,
                    projects = vm.projects,
                    loading = vm.loading,
                    onOpen = vm::openProject,
                    onCreate = vm::createProject,
                    onDuplicate = vm::duplicateProject,
                    onDelete = vm::deleteProject
                )
            }
        }
    }
}
