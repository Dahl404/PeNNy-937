package com.masen.penny937.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import com.masen.penny937.editor.EditorModel
import com.masen.penny937.model.Frame
import com.masen.penny937.model.Project
import com.masen.penny937.model.Tool
import com.masen.penny937.paint.FrameRasterizer
import com.masen.penny937.ui.PennyHaptics
import com.masen.penny937.ui.canvas.DrawingCanvasView
import com.masen.penny937.ui.components.BipolarSlider
import com.masen.penny937.ui.components.ColorCircle
import com.masen.penny937.ui.components.PennyCard
import com.masen.penny937.ui.components.PennyIconButton
import com.masen.penny937.ui.icons.PennyIcons
import com.masen.penny937.ui.icons.shapeIcon
import com.masen.penny937.ui.theme.LocalPenny
import com.masen.penny937.ui.theme.LocalPennyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

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
    var sheet by remember { mutableStateOf(EditorSheet.NONE) }
    var confirmDelete by remember { mutableStateOf(false) }
    var canvasView by remember { mutableStateOf<DrawingCanvasView?>(null) }
    var hintVisible by remember { mutableStateOf(editor.showGestureHint) }
    val hintAlpha by animateFloatAsState(
        targetValue = if (hintVisible && sheet == EditorSheet.NONE && !editor.isPlaying) 1f else 0f,
        animationSpec = tween(280),
        label = "hint"
    )

    BackHandler {
        when {
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
            if (!editor.isPlaying) {
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

                if (!editor.isPlaying) {
                    ToolRail(
                        editor = editor,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
                        onBrush = { sheet = EditorSheet.BRUSH },
                        onColor = { sheet = EditorSheet.COLOR }
                    )
                    Column(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
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
            }
            if (!editor.isPlaying) {
                EditorDock(
                    editor = editor,
                    onLayers = { sheet = EditorSheet.LAYERS },
                    onFrameMenu = { sheet = EditorSheet.FRAME },
                    onAddFrame = {
                        PennyHaptics.tap(view)
                        editor.addFrame()
                    }
                )
            }
        }

        ColorSheet(visible = sheet == EditorSheet.COLOR, editor = editor) {
            sheet = EditorSheet.NONE
        }
        BrushSheet(visible = sheet == EditorSheet.BRUSH, editor = editor) {
            sheet = EditorSheet.NONE
        }
        LayersSheet(visible = sheet == EditorSheet.LAYERS, editor = editor) {
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
                .clip(RoundedCornerShape(10.dp))
                .clickable { onMenu() }
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
                "${editor.project.width} × ${editor.project.height} · ${editor.project.fps} fps",
                style = type.caption,
                color = palette.textSecondary,
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
private fun ToolRail(
    editor: EditorModel,
    modifier: Modifier = Modifier,
    onBrush: () -> Unit,
    onColor: () -> Unit
) {
    val palette = LocalPenny.current
    val view = LocalView.current
    val tools = listOf(Tool.PEN, Tool.PENCIL, Tool.MARKER, Tool.SHAPE, Tool.ERASER)
    PennyCard(corner = 24.dp, modifier = modifier) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for ((index, tool) in tools.withIndex()) {
                if (index > 0) Spacer(Modifier.height(2.dp))
                ToolRailButton(
                    icon = when (tool) {
                        Tool.PEN -> PennyIcons.Pen
                        Tool.PENCIL -> PennyIcons.Pencil
                        Tool.MARKER -> PennyIcons.Marker
                        Tool.SHAPE -> shapeIcon(editor.shapeKind)
                        Tool.ERASER -> PennyIcons.Eraser
                    },
                    selected = editor.tool == tool,
                    contentDescription = tool.label,
                    onClick = {
                        PennyHaptics.select(view)
                        if (editor.tool == tool) onBrush() else editor.chooseTool(tool)
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .width(18.dp)
                    .height(0.7.dp)
                    .background(palette.hairline)
            )
            Spacer(Modifier.height(4.dp))
            ToolRailButton(
                icon = PennyIcons.Sliders,
                selected = false,
                contentDescription = "Brush settings",
                onClick = onBrush
            )
            Spacer(Modifier.height(4.dp))
            Box(Modifier.padding(3.dp)) {
                ColorCircle(
                    color = Color(editor.color),
                    size = 30.dp,
                    onClick = {
                        PennyHaptics.tap(view)
                        onColor()
                    }
                )
            }
        }
    }
}

@Composable
private fun ToolRailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    val palette = LocalPenny.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.55f,
            stiffness = 900f
        ),
        label = "toolPress"
    )
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) palette.accent else Color.Transparent,
        animationSpec = tween(160),
        label = "toolBg"
    )
    Box(
        Modifier
            .size(38.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(13.dp))
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(
                    color = palette.textPrimary.copy(alpha = 0.1f)
                ),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(19.dp),
            tint = if (selected) Color.White else palette.textPrimary
        )
    }
}

@Composable
private fun EditorDock(
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
    LaunchedEffect(editor.currentFrame) {
        listState.animateScrollToItem(editor.currentFrame.coerceAtLeast(0))
    }

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        PennyCard(
            corner = 26.dp,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .widthIn(max = 760.dp)
                .fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Frame ${editor.currentFrame + 1}",
                        style = type.label.copy(fontWeight = FontWeight.SemiBold),
                        color = palette.textPrimary
                    )
                    Text(
                        " / ${frames.size}",
                        style = type.label,
                        color = palette.textTertiary
                    )
                    Spacer(Modifier.weight(1f))
                    DockPill(
                        icon = PennyIcons.Layers,
                        label = "Layers ${editor.frame.layers.size}",
                        active = false,
                        onClick = onLayers
                    )
                }
                Spacer(Modifier.height(3.dp))
                OnionRow(editor)
                Spacer(Modifier.height(7.dp))
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
                        item(key = "add") {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AddFrameCard(onClick = onAddFrame)
                                Spacer(Modifier.height(17.dp))
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
                        icon = PennyIcons.Copy,
                        onClick = {
                            PennyHaptics.tap(view)
                            editor.duplicateFrame()
                        },
                        size = 34.dp,
                        iconSize = 17.dp,
                        tint = palette.textSecondary,
                        contentDescription = "Duplicate frame"
                    )
                    Spacer(Modifier.width(2.dp))
                    PennyIconButton(
                        icon = PennyIcons.Plus,
                        onClick = onAddFrame,
                        size = 36.dp,
                        iconSize = 19.dp,
                        tint = palette.accent,
                        background = palette.accentSoft,
                        contentDescription = "Add frame"
                    )
                    Spacer(Modifier.width(2.dp))
                    PennyIconButton(
                        icon = PennyIcons.More,
                        onClick = onFrameMenu,
                        size = 34.dp,
                        iconSize = 17.dp,
                        tint = palette.textSecondary,
                        contentDescription = "Frame options"
                    )
                }
            }
        }
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
    val ring by animateDpAsState(if (selected) 2.dp else 0.6.dp, label = "ring")
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
        FrameThumbnail(project = project, frame = frame, modifier = Modifier.fillMaxSize())
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
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(2.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected || dragging) palette.accent
                                else palette.textTertiary.copy(alpha = 0.55f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun FrameThumbnail(project: Project, frame: Frame, modifier: Modifier = Modifier) {
    val revision = frame.revision
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        frame.id,
        revision,
        project.width,
        project.height,
        project.background
    ) {
        value = withContext(Dispatchers.Default) {
            val width = 104
            val height = (width.toFloat() * project.height / project.width).roundToInt().coerceAtLeast(1)
            FrameRasterizer.rasterize(
                project = project,
                frame = frame,
                targetWidth = width,
                targetHeight = height,
                background = project.background
            ).asImageBitmap()
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(modifier)
    }
}

@Composable
private fun AddFrameCard(onClick: () -> Unit) {
    val palette = LocalPenny.current
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(58.dp)
            .clip(shape)
            .background(palette.surfaceSunken)
            .border(0.7.dp, palette.hairline, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            PennyIcons.Plus,
            contentDescription = "Add frame",
            tint = palette.accent,
            modifier = Modifier.size(18.dp)
        )
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
