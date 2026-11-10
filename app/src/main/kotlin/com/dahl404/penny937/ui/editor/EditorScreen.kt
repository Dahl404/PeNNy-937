package com.dahl404.penny937.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.dahl404.penny937.ui.components.FramePreview
import com.dahl404.penny937.ui.components.PennySlider
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dahl404.penny937.editor.EditorModel
import com.dahl404.penny937.model.Frame
import com.dahl404.penny937.model.Project
import com.dahl404.penny937.model.Tool
import com.dahl404.penny937.ui.PennyHaptics
import com.dahl404.penny937.ui.SharedFiles
import com.dahl404.penny937.ui.canvas.DrawingCanvasView
import com.dahl404.penny937.ui.components.BipolarSlider
import com.dahl404.penny937.ui.components.ColorCircle
import com.dahl404.penny937.ui.components.PennyCard
import com.dahl404.penny937.ui.components.PennyIconButton
import com.dahl404.penny937.ui.components.SecondaryButton
import com.dahl404.penny937.ui.components.ToggleRow
import com.dahl404.penny937.ui.icons.PennyIcons
import com.dahl404.penny937.ui.icons.shapeIcon
import com.dahl404.penny937.ui.theme.LocalPenny
import com.dahl404.penny937.ui.theme.LocalPennyType
import com.dahl404.penny937.ui.theme.PennyMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class EditorSheet { NONE, COLOR, BRUSH, LAYERS, SETTINGS, EXPORT, MENU, FRAME }

@Composable
fun EditorScreen(
    editor: EditorModel,
    onClose: () -> Unit,
    onDuplicateProject: () -> Unit,
    onDeleteProject: () -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf(EditorSheet.NONE) }
    var confirmDelete by remember { mutableStateOf(false) }
    var canvasView by remember { mutableStateOf<DrawingCanvasView?>(null) }
    var hintVisible by remember { mutableStateOf(editor.showGestureHint) }
    val placing by remember { derivedStateOf { editor.placement != null } }
    val shifting by remember { derivedStateOf { editor.layerShift != null } }
    val picking by remember { derivedStateOf { editor.eyedropper != null } }
    val pickReference = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) editor.importReference(uri)
    }
    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) editor.importVideoReference(uri)
    }
    val hintAlpha by animateFloatAsState(
        targetValue = if (
            hintVisible && sheet == EditorSheet.NONE && !editor.isPlaying &&
            !placing && !shifting && !picking && editor.hint == null
        ) 1f else 0f,
        animationSpec = PennyMotion.contentFloat(),
        label = "hint"
    )

    LaunchedEffect(placing, shifting) {
        if (placing || shifting) canvasView?.fitToPaper()
    }

    LaunchedEffect(editor.hint?.id) {
        val current = editor.hint ?: return@LaunchedEffect
        delay(2600)
        editor.dismissHint(current.id)
    }

    BackHandler {
        when {
            editor.videoImport != null -> editor.cancelVideoImport()
            editor.eyedropper != null -> editor.cancelEyedropper()
            editor.placement != null -> editor.cancelPlacement()
            editor.layerShift != null -> {
                editor.cancelLayerShift()
                canvasView?.invalidate()
            }
            confirmDelete -> confirmDelete = false
            sheet != EditorSheet.NONE -> sheet = EditorSheet.NONE
            editor.isPlaying -> editor.stopPlayback()
            else -> onClose()
        }
    }

    LaunchedEffect(editor.isPlaying, editor.project.fps, editor.project.totalTicks) {
        if (!editor.isPlaying) return@LaunchedEffect
        canvasView?.fitToPaper()
        val total = editor.project.totalTicks.coerceAtLeast(1)
        var origin = 0L
        var lastIndex = -1
        while (editor.isPlaying) {
            withFrameNanos { now ->
                if (origin == 0L) origin = now
                val ticks = (((now - origin) * editor.project.fps) / 1_000_000_000L).toInt() % total
                val index = editor.project.frameIndexAtTick(ticks)
                if (index != lastIndex) {
                    lastIndex = index
                    editor.updatePreviewFrame(index)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(5200)
        hintVisible = false
        editor.dismissGestureHint()
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        Column(Modifier.fillMaxSize()) {
            if (picking) {
                EyedropperTopBar(editor, onCancel = { editor.cancelEyedropper() })
            } else if (placing) {
                PlacementTopBar(
                    inOutput = editor.placement?.inOutput == true,
                    onToggleOutput = {
                        editor.setPlacementOutput(editor.placement?.inOutput != true)
                    },
                    onCancel = { editor.cancelPlacement() },
                    onDone = { editor.confirmPlacement() }
                )
            } else if (shifting) {
                ShiftTopBar(
                    onCancel = {
                        editor.cancelLayerShift()
                        canvasView?.invalidate()
                    },
                    onDone = { editor.commitLayerShift() }
                )
            } else if (!editor.isPlaying) {
                EditorTopBar(
                    editor = editor,
                    onBack = onClose,
                    onMenu = { sheet = EditorSheet.MENU }
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CanvasHost(
                    editor = editor,
                    darkTheme = palette.isDark,
                    modifier = Modifier.fillMaxSize(),
                    onViewCreated = { canvasView = it }
                )

                if (picking) {
                    // Picking mode is canvas-only; the top bar and footer explain it.
                } else if (placing) {
                    // The canvas draws the reference placement overlay itself.
                } else if (shifting) {
                    // The canvas moves the selected layer directly while dragging.
                } else if (!editor.isPlaying) {
                    LeftWindows(
                        editor = editor,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 10.dp, top = 6.dp),
                        onBrush = { sheet = EditorSheet.BRUSH },
                        onColor = { sheet = EditorSheet.COLOR },
                        onLayers = { sheet = EditorSheet.LAYERS },
                        onFrameMenu = { sheet = EditorSheet.FRAME },
                        onAddFrame = {
                            PennyHaptics.tap(view)
                            editor.addFrame()
                        }
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PennyIconButton(
                            icon = PennyIcons.Grid,
                            onClick = {
                                PennyHaptics.tap(view)
                                editor.toggleGrid()
                            },
                            selected = editor.gridEnabled,
                            size = 38.dp,
                            iconSize = 18.dp,
                            tint = palette.textSecondary,
                            background = palette.surface,
                            contentDescription = "Toggle grid"
                        )
                        PennyIconButton(
                            icon = PennyIcons.Fit,
                            onClick = {
                                canvasView?.cancelActiveStroke()
                                canvasView?.fitToPaper(animate = true)
                            },
                            size = 38.dp,
                            iconSize = 18.dp,
                            tint = palette.textSecondary,
                            background = palette.surface,
                            contentDescription = "Fit canvas"
                        )
                    }
                    if (hintAlpha > 0.01f) {
                        HintPill(
                            text = if (editor.stylusOnly) {
                                "Stylus draws · two fingers navigate"
                            } else {
                                "Draw with finger or stylus · two fingers navigate"
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 18.dp)
                                .graphicsLayer { alpha = hintAlpha }
                        )
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { editor.stopPlayback() }
                    ) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 26.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.62f))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                PennyIcons.Pause,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Frame ${editor.previewFrame + 1} of ${editor.project.frames.size} · tap to stop",
                                style = type.caption.copy(fontWeight = FontWeight.Medium),
                                color = Color.White
                            )
                        }
                    }
                }
                if (editor.importingReference) {
                    ImportingPill(Modifier.align(Alignment.Center))
                }
                val activeHint = editor.hint
                if (activeHint != null) {
                    HintPill(
                        text = activeHint.text,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 18.dp)
                    )
                }
            }
            if (picking) {
                EyedropperFooter()
            } else if (placing) {
                PlacementFooter(inOutput = editor.placement?.inOutput == true)
            } else if (shifting) {
                ShiftFooter()
            }
        }

        ColorSheet(
            visible = sheet == EditorSheet.COLOR,
            editor = editor,
            onDismiss = { sheet = EditorSheet.NONE },
            onPickFromLayer = {
                sheet = EditorSheet.NONE
                PennyHaptics.tap(view)
                editor.beginEyedropper()
            }
        )
        BrushSheet(visible = sheet == EditorSheet.BRUSH, editor = editor) {
            sheet = EditorSheet.NONE
        }
        LayersSheet(
            visible = sheet == EditorSheet.LAYERS,
            editor = editor,
            onImportReference = {
                sheet = EditorSheet.NONE
                pickReference.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onImportVideo = {
                sheet = EditorSheet.NONE
                pickVideo.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            onPositionReference = {
                sheet = EditorSheet.NONE
                editor.beginReposition(editor.layer)
            },
            onShiftLayer = {
                sheet = EditorSheet.NONE
                editor.beginLayerShift()
            }
        ) {
            sheet = EditorSheet.NONE
        }
        SettingsSheet(visible = sheet == EditorSheet.SETTINGS, editor = editor) {
            sheet = EditorSheet.NONE
        }
        ExportSheet(visible = sheet == EditorSheet.EXPORT, editor = editor) {
            sheet = EditorSheet.NONE
        }
        ProjectMenuSheet(
            visible = sheet == EditorSheet.MENU,
            editor = editor,
            onDismiss = { sheet = EditorSheet.NONE },
            onOpenSettings = { sheet = EditorSheet.SETTINGS },
            onOpenExport = { sheet = EditorSheet.EXPORT },
            onShareProject = {
                sheet = EditorSheet.NONE
                scope.launch {
                    val archive = withContext(Dispatchers.IO) { editor.createArchive() }
                    if (archive != null) {
                        SharedFiles.share(context, archive, "application/zip")
                    } else {
                        editor.showHint("Could not prepare the project zip")
                    }
                }
            },
            onDuplicateProject = {
                sheet = EditorSheet.NONE
                onDuplicateProject()
            },
            onDeleteProject = {
                sheet = EditorSheet.NONE
                confirmDelete = true
            }
        )
        FrameSheet(visible = sheet == EditorSheet.FRAME, editor = editor) {
            sheet = EditorSheet.NONE
        }
        ConfirmDeleteSheet(
            visible = confirmDelete,
            projectName = editor.project.name,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDeleteProject()
            }
        )

        VideoImportOverlay(editor)
    }
}

@Composable
private fun VideoImportOverlay(editor: EditorModel) {
    val state = editor.videoImport ?: return
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Separate scrim keeps it from swallowing the Cancel button.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.38f))
                .pointerInput(Unit) { detectTapGestures { } }
        )
        PennyCard(corner = 24.dp) {
            Column(
                Modifier
                    .width(300.dp)
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        PennyIcons.Video,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Importing video", style = type.title, color = palette.textPrimary)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        state.preparing -> "Reading the clip…"
                        state.total > 0 -> "Extracting frame ${state.done} of ${state.total}"
                        else -> "Extracting frames…"
                    },
                    style = type.body,
                    color = palette.textSecondary
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(palette.surfaceSunken)
                ) {
                    if (state.total > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth((state.done.toFloat() / state.total).coerceIn(0f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(palette.accent)
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "Each extracted frame becomes a locked reference layer, " +
                        "at ${editor.project.fps} fps.",
                    style = type.caption,
                    color = palette.textTertiary,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    title = "Show video in export",
                    subtitle = "Use the clip as footage under your drawing",
                    checked = editor.referenceInOutput,
                    onCheckedChange = { editor.setReferenceOutputDefault(it) }
                )
                Spacer(Modifier.height(14.dp))
                SecondaryButton(
                    text = "Cancel import",
                    onClick = { editor.cancelVideoImport() },
                    modifier = Modifier.fillMaxWidth(),
                    icon = PennyIcons.Close
                )
            }
        }
    }
}

private fun hexColor(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

@Composable
private fun EyedropperTopBar(editor: EditorModel, onCancel: () -> Unit) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val pillShape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PennyIconButton(
            icon = PennyIcons.Close,
            onClick = onCancel,
            size = 38.dp,
            iconSize = 18.dp,
            background = palette.surface,
            contentDescription = "Cancel color pick"
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Pick color",
                style = type.headline,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Sampling ${editor.eyedropper?.layerName ?: "the selected layer"} only",
                style = type.caption,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier
                .clip(pillShape)
                .background(palette.surface)
                .border(0.5.dp, palette.hairline, pillShape)
                .padding(start = 7.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorCircle(color = Color(editor.color), size = 24.dp)
            Spacer(Modifier.width(7.dp))
            Text(
                hexColor(editor.color),
                style = type.mono,
                color = palette.textPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EyedropperFooter() {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(palette.surfaceSunken)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                PennyIcons.Eyedropper,
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "Tap or drag to sample · lift to keep",
                style = type.caption,
                color = palette.textSecondary
            )
        }
    }
}

@Composable
private fun PlacementTopBar(
    inOutput: Boolean,
    onToggleOutput: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PennyIconButton(
            icon = PennyIcons.Close,
            onClick = onCancel,
            size = 38.dp,
            iconSize = 18.dp,
            background = palette.surface,
            contentDescription = "Cancel placement"
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Place reference",
                style = type.headline,
                color = palette.textPrimary,
                maxLines = 1
            )
            Text(
                "Drag to move · pinch to scale & rotate",
                style = type.caption,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(6.dp))
        PennyIconButton(
            icon = if (inOutput) PennyIcons.Eye else PennyIcons.EyeOff,
            onClick = {
                PennyHaptics.tap(view)
                onToggleOutput()
            },
            size = 38.dp,
            iconSize = 18.dp,
            selected = inOutput,
            background = if (inOutput) palette.accent.copy(alpha = 0.16f) else palette.surface,
            tint = if (inOutput) palette.accent else palette.textSecondary,
            contentDescription = if (inOutput) {
                "Exclude reference from export"
            } else {
                "Include reference in export"
            }
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(palette.accent)
                .clickable {
                    PennyHaptics.tap(view)
                    onDone()
                }
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(
                "Done",
                style = type.label.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
        }
    }
}

@Composable
private fun ShiftTopBar(onCancel: () -> Unit, onDone: () -> Unit) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PennyIconButton(
            icon = PennyIcons.Close,
            onClick = onCancel,
            size = 38.dp,
            iconSize = 18.dp,
            background = palette.surface,
            contentDescription = "Cancel shift"
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Shift layer",
                style = type.headline,
                color = palette.textPrimary,
                maxLines = 1
            )
            Text(
                "Drag to move · twist two fingers to rotate · Done to apply",
                style = type.caption,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(palette.accent)
                .clickable {
                    PennyHaptics.tap(view)
                    onDone()
                }
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Text(
                "Done",
                style = type.label.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
        }
    }
}

@Composable
private fun ShiftFooter() {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(palette.surfaceSunken)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                PennyIcons.Rotate,
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "Moves and rotates the whole layer as one",
                style = type.caption,
                color = palette.textSecondary
            )
        }
    }
}

@Composable
private fun PlacementFooter(inOutput: Boolean) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(palette.surfaceSunken)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                PennyIcons.Image,
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                if (inOutput) "Reference will be drawn into the export"
                else "Reference is a guide · not exported or played",
                style = type.caption,
                color = palette.textSecondary
            )
        }
    }
}

@Composable
private fun ImportingPill(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.68f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = Color.White,
            strokeWidth = 2.dp
        )
        Spacer(Modifier.width(9.dp))
        Text(
            "Importing reference…",
            style = LocalPennyType.current.caption,
            color = Color.White
        )
    }
}

@Composable
private fun CanvasHost(
    editor: EditorModel,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    onViewCreated: (DrawingCanvasView) -> Unit
) {
    // Subscribing to this value is what makes the canvas repaint continuously
    // during onion-slider drags and after frame / layer / grid / ink changes.
    val revision = editor.canvasRevision
    AndroidView(
        factory = { context ->
            DrawingCanvasView(context).also(onViewCreated)
        },
        modifier = modifier,
        update = { view ->
            view.darkTheme = darkTheme
            view.bind(editor, revision)
            view.previewFrameIndex = if (editor.isPlaying) editor.previewFrame else -1
        }
    )
}

@Composable
private fun EditorTopBar(
    editor: EditorModel,
    onBack: () -> Unit,
    onMenu: () -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        PennyIconButton(
            icon = PennyIcons.ChevronLeft,
            onClick = onBack,
            size = 38.dp,
            iconSize = 19.dp,
            background = palette.surface,
            contentDescription = "Back to projects"
        )
        Spacer(Modifier.width(6.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(
                editor.project.name,
                style = type.headline,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append("${editor.project.width} × ${editor.project.height} · ${editor.project.fps} fps")
                    when {
                        editor.saveError != null -> append(" · not saved")
                        editor.saving -> append(" · saving…")
                    }
                },
                style = type.caption,
                color = if (editor.saveError != null) palette.danger else palette.textSecondary,
                maxLines = 1
            )
        }
        PennyIconButton(
            icon = PennyIcons.Undo,
            onClick = {
                PennyHaptics.tap(view)
                editor.undo()
            },
            size = 36.dp,
            iconSize = 18.dp,
            enabled = editor.canUndo,
            contentDescription = "Undo"
        )
        PennyIconButton(
            icon = PennyIcons.Redo,
            onClick = {
                PennyHaptics.tap(view)
                editor.redo()
            },
            size = 36.dp,
            iconSize = 18.dp,
            enabled = editor.canRedo,
            contentDescription = "Redo"
        )
        PennyIconButton(
            icon = PennyIcons.More,
            onClick = onMenu,
            size = 38.dp,
            iconSize = 18.dp,
            background = palette.surface,
            contentDescription = "Project menu"
        )
    }
}

@Composable
private fun LeftWindows(
    editor: EditorModel,
    modifier: Modifier = Modifier,
    onBrush: () -> Unit,
    onColor: () -> Unit,
    onLayers: () -> Unit,
    onFrameMenu: () -> Unit,
    onAddFrame: () -> Unit
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ToolWindow(
            editor = editor,
            onBrush = onBrush,
            onColor = onColor
        )
        FrameWindow(
            editor = editor,
            onLayers = onLayers,
            onFrameMenu = onFrameMenu,
            onAddFrame = onAddFrame
        )
    }
}

/** First window: the inks and their settings. */
@Composable
private fun ToolWindow(
    editor: EditorModel,
    onBrush: () -> Unit,
    onColor: () -> Unit
) {
    var collapsed by remember { mutableStateOf(false) }

    PennyCard(corner = 20.dp) {
        AnimatedContent(
            targetState = collapsed,
            transitionSpec = {
                // Grow/shrink with the shared soft size spring and let the two
                // tool layouts cross-fade instead of swapping in one frame.
                (
                    fadeIn(PennyMotion.fadeInFloat()) +
                        scaleIn(
                            initialScale = 0.96f,
                            animationSpec = PennyMotion.contentFloat()
                        )
                    ).togetherWith(
                    fadeOut(PennyMotion.fadeOutFloat()) +
                        scaleOut(
                            targetScale = 0.96f,
                            animationSpec = PennyMotion.contentFloat()
                        )
                )
            },
            contentAlignment = Alignment.TopCenter,
            label = "toolWindow"
        ) { isCollapsed ->
            Column(
                modifier = Modifier.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isCollapsed) {
                    ToolWindowCollapsed(editor) { collapsed = false }
                } else {
                    ToolWindowExpanded(
                        editor = editor,
                        onBrush = onBrush,
                        onColor = onColor,
                        onCollapse = { collapsed = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolWindowCollapsed(editor: EditorModel, onExpand: () -> Unit) {
    ToolRailButton(
        icon = toolIcon(editor, editor.tool),
        selected = true,
        contentDescription = "Expand tools",
        onClick = onExpand
    )
    Spacer(Modifier.height(1.dp))
    ToolRailButton(
        icon = PennyIcons.ChevronDown,
        selected = false,
        contentDescription = "Expand tools",
        size = 26.dp,
        iconSize = 14.dp,
        onClick = onExpand
    )
}

@Composable
private fun ToolWindowExpanded(
    editor: EditorModel,
    onBrush: () -> Unit,
    onColor: () -> Unit,
    onCollapse: () -> Unit
) {
    val view = LocalView.current
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        ToolSlot(editor, Tool.PEN)
        ToolSlot(editor, Tool.PENCIL)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        ToolSlot(editor, Tool.MARKER)
        ToolSlot(editor, Tool.SHAPE)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        ToolSlot(editor, Tool.ERASER)
        ToolSlot(editor, Tool.MORPH)
    }
    ToolbarDivider()
    Row(
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolRailButton(
            icon = PennyIcons.Sliders,
            selected = false,
            contentDescription = "Brush settings",
            onClick = onBrush
        )
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            ColorCircle(
                color = Color(editor.color),
                size = 27.dp,
                onClick = {
                    PennyHaptics.tap(view)
                    onColor()
                }
            )
        }
    }
    ToolbarDivider()
    ToolRailButton(
        icon = PennyIcons.ChevronUp,
        selected = false,
        contentDescription = "Collapse tools",
        size = 26.dp,
        iconSize = 14.dp,
        onClick = onCollapse
    )
}

/** Second window: the frame strip, onion skin, layers and transport. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FrameWindow(
    editor: EditorModel,
    onLayers: () -> Unit,
    onFrameMenu: () -> Unit,
    onAddFrame: () -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val view = LocalView.current
    val listState = rememberLazyListState()
    val frames = editor.project.frames
    val lineThreshold = with(LocalDensity.current) { 2.dp.toPx() }
    var drag by remember { mutableStateOf<FrameDrag?>(null) }
    var collapsed by remember { mutableStateOf(false) }
    var lastShownFrame by remember { mutableIntStateOf(-1) }
    LaunchedEffect(collapsed, editor.currentFrame) {
        if (collapsed) {
            lastShownFrame = -1
            return@LaunchedEffect
        }
        // Request the position before the expanding list's first layout so it
        // does not flash frame 1 and then jump sideways.
        if (lastShownFrame < 0) {
            listState.requestScrollToItem(editor.currentFrame.coerceAtLeast(0))
        } else if (lastShownFrame != editor.currentFrame) {
            listState.animateScrollToItem(editor.currentFrame.coerceAtLeast(0))
        }
        lastShownFrame = editor.currentFrame
    }
    PennyCard(corner = 20.dp) {
        AnimatedContent(
            targetState = collapsed,
            transitionSpec = {
                (
                    fadeIn(PennyMotion.fadeInFloat()) +
                        scaleIn(
                            initialScale = 0.97f,
                            animationSpec = PennyMotion.contentFloat()
                        )
                    ).togetherWith(
                    fadeOut(PennyMotion.fadeOutFloat()) +
                        scaleOut(
                            targetScale = 0.97f,
                            animationSpec = PennyMotion.contentFloat()
                        )
                )
            },
            contentAlignment = Alignment.TopStart,
            label = "frameWindow"
        ) { isCollapsed ->
            Column(
                modifier = Modifier
                    .width(if (isCollapsed) 44.dp else 250.dp)
                    .padding(
                        horizontal = if (isCollapsed) 4.dp else 8.dp,
                        vertical = 7.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isCollapsed) {
                    ToolRailButton(
                        icon = PennyIcons.Layers,
                        selected = false,
                        contentDescription = "Layers ${editor.frame.layers.size}",
                        onClick = onLayers
                    )
                    Spacer(Modifier.height(1.dp))
                    ToolRailButton(
                        icon = PennyIcons.Frame,
                        selected = false,
                        contentDescription = "New frame, copy of current",
                        onClick = onAddFrame
                    )
                    Spacer(Modifier.height(1.dp))
                    ToolRailButton(
                        icon = PennyIcons.ChevronDown,
                        selected = false,
                        contentDescription = "Expand frames",
                        size = 26.dp,
                        iconSize = 14.dp,
                        onClick = { collapsed = false }
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DockPill(
                            icon = PennyIcons.Layers,
                            label = "Layers ${editor.frame.layers.size}",
                            active = false,
                            onClick = onLayers
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Frame ${editor.currentFrame + 1}",
                            style = type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = palette.textPrimary
                        )
                        Text(
                            " / ${frames.size}",
                            style = type.caption,
                            color = palette.textTertiary
                        )
                        Spacer(Modifier.weight(1f))
                        ToolRailButton(
                            icon = PennyIcons.ChevronUp,
                            selected = false,
                            contentDescription = "Collapse frames",
                            size = 26.dp,
                            iconSize = 14.dp,
                            onClick = { collapsed = true }
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    OnionRow(editor)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth()) {
                        LazyRow(
                            state = listState,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(frames, key = { _, frame -> frame.id }) { index, frame ->
                                val isDragged = drag?.frameId == frame.id
                                val cardWidth = frameCardWidth(editor.project)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .graphicsLayer {
                                            if (isDragged) {
                                                scaleX = 1.05f
                                                scaleY = 1.05f
                                            }
                                        }
                                        .zIndex(if (isDragged) 2f else 0f)
                                        .shadow(
                                            elevation = if (isDragged) 10.dp else 0.dp,
                                            shape = RoundedCornerShape(12.dp),
                                            clip = false,
                                            ambientColor = palette.shadow,
                                            spotColor = palette.shadow
                                        )
                                ) {
                                    FrameCard(
                                        project = editor.project,
                                        frame = frame,
                                        index = index,
                                        selected = index == editor.currentFrame,
                                        onClick = {
                                            PennyHaptics.tap(view)
                                            editor.selectFrame(index)
                                        },
                                        onLongClick = {
                                            PennyHaptics.select(view)
                                            editor.selectFrame(index)
                                            onFrameMenu()
                                        }
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    FrameGrip(
                                        width = cardWidth,
                                        selected = index == editor.currentFrame,
                                        dragging = isDragged,
                                        onDragStart = { startOffset ->
                                            val info = listState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.key == frame.id }
                                            if (info != null) {
                                                PennyHaptics.select(view)
                                                drag = FrameDrag(
                                                    frameId = frame.id,
                                                    from = index,
                                                    startPointerX = info.offset + startOffset.x,
                                                    lineX = info.offset.toFloat()
                                                )
                                            }
                                        },
                                        onDrag = { amount ->
                                            val current = drag
                                            if (current != null && current.frameId == frame.id) {
                                                val newOffset = current.offsetX + amount
                                                val pointer = current.startPointerX + newOffset
                                                val target = resolveFrameTarget(
                                                    pointerX = pointer,
                                                    from = current.from,
                                                    draggedId = current.frameId,
                                                    frames = frames,
                                                    visible = listState.layoutInfo.visibleItemsInfo
                                                )
                                                drag = current.copy(
                                                    offsetX = newOffset,
                                                    target = target?.first ?: current.target,
                                                    lineX = target?.second ?: current.lineX
                                                )
                                            }
                                        },
                                        onDragEnd = {
                                            val current = drag
                                            drag = null
                                            if (current != null && current.frameId == frame.id &&
                                                current.target != current.from
                                            ) {
                                                PennyHaptics.tap(view)
                                                editor.moveFrame(current.from, current.target)
                                            }
                                        },
                                        onDragCancel = { drag = null }
                                    )
                                }
                            }
                        }
                        val currentDrag = drag
                        if (currentDrag != null && abs(currentDrag.offsetX) > lineThreshold) {
                            Canvas(Modifier.matchParentSize()) {
                                val x = currentDrag.lineX.coerceIn(
                                    2.dp.toPx(),
                                    size.width - 2.dp.toPx()
                                )
                                val top = 3.dp.toPx()
                                val bottom = size.height - 3.dp.toPx()
                                drawLine(
                                    color = palette.accent,
                                    start = Offset(x, top),
                                    end = Offset(x, bottom),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                drawCircle(palette.accent, 3.dp.toPx(), Offset(x, top))
                                drawCircle(palette.accent, 3.dp.toPx(), Offset(x, bottom))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PennyIconButton(
                            icon = PennyIcons.ChevronLeft,
                            onClick = { editor.selectFrame(editor.currentFrame - 1) },
                            size = 34.dp,
                            iconSize = 18.dp,
                            enabled = editor.currentFrame > 0,
                            tint = palette.textSecondary,
                            contentDescription = "Previous frame"
                        )
                        Spacer(Modifier.width(4.dp))
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(palette.accent)
                                .clickable {
                                    PennyHaptics.select(view)
                                    editor.togglePlayback()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                PennyIcons.Play,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        PennyIconButton(
                            icon = PennyIcons.ChevronRight,
                            onClick = { editor.selectFrame(editor.currentFrame + 1) },
                            size = 34.dp,
                            iconSize = 18.dp,
                            enabled = editor.currentFrame < frames.lastIndex,
                            tint = palette.textSecondary,
                            contentDescription = "Next frame"
                        )
                        Spacer(Modifier.weight(1f))
                        PennyIconButton(
                            icon = PennyIcons.Square,
                            onClick = {
                                PennyHaptics.tap(view)
                                editor.emptyFrame()
                            },
                            size = 34.dp,
                            iconSize = 17.dp,
                            tint = palette.textSecondary,
                            contentDescription = "Empty frame"
                        )
                        Spacer(Modifier.width(2.dp))
                        PennyIconButton(
                            icon = PennyIcons.Plus,
                            onClick = onAddFrame,
                            size = 36.dp,
                            iconSize = 19.dp,
                            tint = palette.accent,
                            background = palette.accentSoft,
                            contentDescription = "New frame, copy of current"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolSlot(editor: EditorModel, tool: Tool) {
    val view = LocalView.current
    ToolRailButton(
        icon = toolIcon(editor, tool),
        selected = editor.tool == tool,
        contentDescription = tool.label,
        onClick = {
            if (editor.tool != tool) {
                PennyHaptics.select(view)
                editor.chooseTool(tool)
            }
        }
    )
}

private fun toolIcon(
    editor: EditorModel,
    tool: Tool
): androidx.compose.ui.graphics.vector.ImageVector = when (tool) {
    Tool.PEN -> PennyIcons.Pen
    Tool.PENCIL -> PennyIcons.Pencil
    Tool.MARKER -> PennyIcons.Marker
    Tool.SHAPE -> shapeIcon(editor.shapeKind)
    Tool.ERASER -> PennyIcons.Eraser
    Tool.MORPH -> PennyIcons.Morph
}

@Composable
private fun ToolbarDivider() {
    val palette = LocalPenny.current
    Spacer(Modifier.height(3.dp))
    Box(
        Modifier
            .width(22.dp)
            .height(0.7.dp)
            .background(palette.hairline)
    )
    Spacer(Modifier.height(3.dp))
}

@Composable
private fun ToolRailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    enabled: Boolean = true
) {
    val palette = LocalPenny.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.93f else 1f,
        animationSpec = PennyMotion.pressFloat(),
        label = "toolPress"
    )
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) palette.accent else Color.Transparent,
        animationSpec = PennyMotion.color(),
        label = "toolBg"
    )
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(
                    color = palette.textPrimary.copy(alpha = 0.1f)
                ),
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = when {
                selected -> Color.White
                enabled -> palette.textPrimary
                else -> palette.textTertiary
            }
        )
    }
}

private data class FrameDrag(
    val frameId: Long,
    val from: Int,
    val startPointerX: Float,
    val offsetX: Float = 0f,
    val target: Int = from,
    val lineX: Float = 0f
)

private fun resolveFrameTarget(
    pointerX: Float,
    from: Int,
    draggedId: Long,
    frames: List<Frame>,
    visible: List<LazyListItemInfo>
): Pair<Int, Float>? {
    val others = visible.filter { info ->
        info.key != draggedId && frames.any { it.id == info.key }
    }
    if (others.isEmpty()) return null
    val hovered = others.minByOrNull {
        abs((it.offset + it.size / 2f) - pointerX)
    } ?: return null
    val hoveredIndex = frames.indexOfFirst { it.id == hovered.key }
    if (hoveredIndex < 0) return null
    val leftHalf = pointerX < hovered.offset + hovered.size / 2f
    val gap = if (leftHalf) hoveredIndex else hoveredIndex + 1
    val target = if (gap > from) gap - 1 else gap
    val lineX = if (leftHalf) hovered.offset.toFloat() else (hovered.offset + hovered.size).toFloat()
    return target to lineX
}

@Composable
private fun OnionRow(editor: EditorModel) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val strength = editor.onionStrength
    val active = abs(strength) > 0.01f
    val forward = strength > 0f
    val tint = when {
        !active -> palette.textTertiary
        forward -> Color(0xFFFF5A48)
        else -> Color(0xFF3B9CFF)
    }
    val label = when {
        !active -> "Off"
        forward -> "Next ${(strength * 100).roundToInt()}%"
        else -> "Last ${(-strength * 100).roundToInt()}%"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            PennyIcons.Onion,
            contentDescription = "Onion skin",
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = type.caption,
            color = tint,
            maxLines = 1,
            modifier = Modifier.width(62.dp)
        )
        Spacer(Modifier.width(2.dp))
        BipolarSlider(
            value = strength,
            onValueChange = { editor.updateOnionStrength(it) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DockPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) palette.accentSoft else palette.surfaceSunken)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) palette.accent else palette.textSecondary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = type.caption.copy(
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = if (active) palette.accent else palette.textSecondary,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FrameCard(
    project: Project,
    frame: Frame,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val height = 58.dp
    val width = frameCardWidth(project)
    val shape = RoundedCornerShape(9.dp)
    val ring by animateDpAsState(
        targetValue = if (selected) 2.dp else 0.6.dp,
        animationSpec = PennyMotion.settleDp(),
        label = "ring"
    )
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(Color(project.background))
            .border(
                ring,
                if (selected) palette.accent else palette.hairline,
                shape
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        FramePreview(project = project, frame = frame, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(3.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(
                "${index + 1}",
                style = type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 9.sp),
                color = Color.White
            )
        }
        if (frame.hold > 1) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    "×${frame.hold}",
                    style = type.caption.copy(fontSize = 9.sp),
                    color = Color.White
                )
            }
        }
    }
}

private fun frameCardWidth(project: Project): androidx.compose.ui.unit.Dp =
    (58f * project.width.toFloat() / project.height).coerceIn(38f, 92f).dp

@Composable
private fun FrameGrip(
    width: androidx.compose.ui.unit.Dp,
    selected: Boolean,
    dragging: Boolean,
    onDragStart: (Offset) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    val palette = LocalPenny.current
    val startState = androidx.compose.runtime.rememberUpdatedState(onDragStart)
    val dragState = androidx.compose.runtime.rememberUpdatedState(onDrag)
    val endState = androidx.compose.runtime.rememberUpdatedState(onDragEnd)
    val cancelState = androidx.compose.runtime.rememberUpdatedState(onDragCancel)
    Box(
        modifier = Modifier
            .width(width)
            .height(14.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { startState.value(it) },
                    onDrag = { change, amount ->
                        change.consume()
                        dragState.value(amount.x)
                    },
                    onDragEnd = { endState.value() },
                    onDragCancel = { cancelState.value() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(28.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (dragging) palette.accentSoft else palette.surfaceSunken),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .width(16.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected || dragging) palette.accent
                        else palette.textTertiary.copy(alpha = 0.55f)
                    )
            )
        }
    }
}




@Composable
private fun HintPill(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.66f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            PennyIcons.Stylus,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            style = LocalPennyType.current.caption,
            color = Color.White.copy(alpha = 0.92f)
        )
    }
}
