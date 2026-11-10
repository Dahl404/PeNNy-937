package com.dahl404.penny937.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import com.dahl404.penny937.PennyViewModel
import com.dahl404.penny937.ui.editor.EditorScreen
import com.dahl404.penny937.ui.theme.LocalPenny
import com.dahl404.penny937.ui.theme.PennyMotion

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
                // Keep the canvas stationary while it fades in: transforming an
                // AndroidView mid-composition reads as a stutter. The home screen
                // carries the direction instead.
                val enter = if (enteringEditor) {
                    fadeIn(PennyMotion.fadeInFloat())
                } else {
                    fadeIn(PennyMotion.fadeInFloat()) +
                        slideInHorizontally(PennyMotion.gentleOffset()) { it / 14 }
                }
                val exit = if (enteringEditor) {
                    fadeOut(PennyMotion.fadeOutFloat()) +
                        slideOutHorizontally(PennyMotion.gentleOffset()) { -it / 14 }
                } else {
                    fadeOut(PennyMotion.fadeOutFloat())
                }
                enter togetherWith exit
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
                    onDelete = vm::deleteProject,
                    onImportArchive = vm::importArchive
                )
            }
        }
    }
}
