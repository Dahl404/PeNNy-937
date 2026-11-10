package com.dahl404.penny937.ui.editor

import android.graphics.Color as AColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dahl404.penny937.editor.EditorModel
import com.dahl404.penny937.editor.ExportState
import com.dahl404.penny937.export.ExportFormat
import com.dahl404.penny937.export.VideoPlan
import com.dahl404.penny937.model.Frame
import com.dahl404.penny937.model.GridStyle
import com.dahl404.penny937.model.Layer
import com.dahl404.penny937.model.ProjectFactory
import com.dahl404.penny937.model.ShapeKind
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import com.dahl404.penny937.paint.Ink
import com.dahl404.penny937.paint.Shapes
import com.dahl404.penny937.ui.SharedFiles
import com.dahl404.penny937.ui.components.BipolarSlider
import com.dahl404.penny937.ui.components.ChoiceChip
import com.dahl404.penny937.ui.components.ColorCircle
import com.dahl404.penny937.ui.components.FramePreview
import com.dahl404.penny937.ui.components.Hairline
import com.dahl404.penny937.ui.components.PennyIconButton
import com.dahl404.penny937.ui.components.PennySheet
import com.dahl404.penny937.ui.components.PennySlider
import com.dahl404.penny937.ui.components.PennyTextField
import com.dahl404.penny937.ui.components.PennyToggle
import com.dahl404.penny937.ui.components.PrimaryButton
import com.dahl404.penny937.ui.components.SecondaryButton
import com.dahl404.penny937.ui.components.SectionLabel
import com.dahl404.penny937.ui.components.SegmentedControl
import com.dahl404.penny937.ui.components.StatPill
import com.dahl404.penny937.ui.components.ToggleRow
import com.dahl404.penny937.ui.components.ValueStepper
import com.dahl404.penny937.ui.icons.PennyIcons
import com.dahl404.penny937.ui.icons.shapeIcon
import com.dahl404.penny937.ui.theme.LocalPenny
import com.dahl404.penny937.ui.theme.LocalPennyType
import com.dahl404.penny937.ui.theme.PennyDimens
import com.dahl404.penny937.ui.theme.PennyMotion
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val InkSwatches = listOf(
    0xFF1D1D1F,
    0xFF6E6E73,
    0xFFFFFFFF,
    0xFFFF3B30,
    0xFFFF9500,
    0xFFFFCC00,
    0xFF34C759,
    0xFF30B0C7,
    0xFF007AFF,
    0xFF5856D6,
    0xFFAF52DE,
    0xFFFF2D55
).map { it.toInt() }

private val HueWheelColors = listOf(
    Color(0xFFFF3B30),
    Color(0xFFFFCC00),
    Color(0xFF34C759),
    Color(0xFF30B0C7),
    Color(0xFF007AFF),
    Color(0xFFAF52DE),
    Color(0xFFFF3B30)
)

private fun hsvColor(h: Float, s: Float, v: Float): Int =
    AColor.HSVToColor(floatArrayOf(h, s, v))

private fun hexOf(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

@Composable
fun BoxScope.ColorSheet(
    visible: Boolean,
    editor: EditorModel,
    onDismiss: () -> Unit,
    onPickFromLayer: (Long) -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val initial = remember { FloatArray(3).also { AColor.colorToHSV(editor.color, it) } }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var sat by remember { mutableFloatStateOf(initial[1]) }
    var value by remember { mutableFloatStateOf(initial[2]) }
    var sampleLayerId by remember { mutableStateOf<Long?>(null) }
    var sampleMenuOpen by remember { mutableStateOf(false) }
    var editingSwatch by remember { mutableStateOf<Int?>(null) }
    val layers = editor.frame.layers
    val sampleLayer = sampleLayerId
        ?.let { id -> layers.firstOrNull { it.id == id } }
        ?: editor.layer
    androidx.compose.runtime.LaunchedEffect(visible) {
        if (visible) {
            sampleMenuOpen = false
            editingSwatch = null
            AColor.colorToHSV(editor.color, initial)
            hue = initial[0]
            sat = initial[1]
            value = initial[2]
        } else {
            sampleMenuOpen = false
            editingSwatch = null
        }
    }

    fun commit() {
        val next = hsvColor(hue, sat, value)
        editor.chooseColor(next)
        editingSwatch?.let { editor.setCustomSwatch(it, next) }
    }

    fun applyColor(color: Int) {
        val hsv = FloatArray(3)
        AColor.colorToHSV(color, hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        editor.chooseColor(color)
    }

    fun select(color: Int) {
        applyColor(color)
        editingSwatch = null
    }

    fun tapCustomSwatch(index: Int) {
        val saved = editor.customSwatches.getOrNull(index)
        if (saved == null) {
            editor.setCustomSwatch(index, editor.color)
            editingSwatch = null
        } else {
            applyColor(saved)
            editingSwatch = null
        }
    }

    fun editCustomSwatch(index: Int) {
        val saved = editor.customSwatches.getOrNull(index) ?: editor.color
        if (editor.customSwatches.getOrNull(index) == null) {
            editor.setCustomSwatch(index, saved)
        }
        editingSwatch = index
        applyColor(saved)
    }

    PennySheet(
        visible = visible,
        onDismiss = onDismiss,
        title = "Color",
        subtitle = "Ink for the current tool"
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorCircle(color = Color(editor.color), size = 54.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        hexOf(editor.color),
                        style = type.title.copy(letterSpacing = 0.5.sp),
                        color = palette.textPrimary
                    )
                    Text("Current ink", style = type.caption, color = palette.textSecondary)
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${hue.roundToInt()}°", style = type.mono, color = palette.textSecondary)
                    Text(
                        "${(sat * 100).roundToInt()}% · ${(value * 100).roundToInt()}%",
                        style = type.caption,
                        color = palette.textTertiary
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("COLOR PICKER")
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    val pickerShape = RoundedCornerShape(14.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(pickerShape)
                            .background(palette.surface)
                            .border(PennyDimens.hairline, palette.hairline, pickerShape)
                            .clickable { sampleMenuOpen = true }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            PennyIcons.Eyedropper,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = palette.accent
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "SAMPLE FROM",
                                style = type.caption,
                                color = palette.textTertiary
                            )
                            Text(
                                sampleLayer.name,
                                style = type.label,
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            PennyIcons.ChevronDown,
                            contentDescription = "Choose a layer to sample",
                            modifier = Modifier.size(16.dp),
                            tint = palette.textSecondary
                        )
                    }
                    DropdownMenu(
                        expanded = sampleMenuOpen,
                        onDismissRequest = { sampleMenuOpen = false }
                    ) {
                        for (layer in layers.reversed()) {
                            val isSource = layer.id == sampleLayer.id
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            layer.name,
                                            style = type.bodyStrong,
                                            color = if (isSource) palette.accent else palette.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            layerDescriptor(editor, layer),
                                            style = type.caption,
                                            color = palette.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                trailingIcon = if (isSource) {
                                    {
                                        Icon(
                                            PennyIcons.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = palette.accent
                                        )
                                    }
                                } else null,
                                onClick = {
                                    sampleLayerId = layer.id
                                    sampleMenuOpen = false
                                }
                            )
                        }
                    }
                }
                SecondaryButton(
                    text = "Pick",
                    icon = PennyIcons.Eyedropper,
                    onClick = { onPickFromLayer(sampleLayer.id) },
                    modifier = Modifier.width(96.dp)
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "Samples only ${sampleLayer.name}; you keep drawing on ${editor.layer.name}.",
                style = type.caption,
                color = palette.textTertiary,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(18.dp))
            SliderBlock(
                label = "Saturation",
                valueText = "${(sat * 100).roundToInt()}%"
            ) {
                val from = Color(hsvColor(hue, 0f, value))
                val to = Color(hsvColor(hue, 1f, value))
                PennySlider(
                    value = sat,
                    onValueChange = { sat = it; commit() },
                    valueRange = 0f..1f,
                    trackColors = listOf(from, to)
                )
            }
            SliderBlock(
                label = "Brightness",
                valueText = "${(value * 100).roundToInt()}%"
            ) {
                PennySlider(
                    value = value,
                    onValueChange = { value = it; commit() },
                    valueRange = 0.02f..1f,
                    trackColors = listOf(Color.Black, Color(hsvColor(hue, sat, 1f)))
                )
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("SWATCHES")
            Spacer(Modifier.height(10.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columnGap = 14.dp
                // Keep the swatch grid at least six readable columns wide; the wheel
                // takes what is left, capped at roughly twice its old 108.dp size.
                val wheelSize = (maxWidth - 186.dp - columnGap).coerceIn(80.dp, 216.dp)
                val swatchArea = maxWidth - wheelSize - columnGap
                val swatchSize = ((swatchArea - 15.dp) / 6f).coerceIn(22.dp, 36.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(columnGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        repeat(3) { rowIndex ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (column in 0 until 4) {
                                    val color = InkSwatches[column * 3 + rowIndex]
                                    ColorCircle(
                                        color = Color(color),
                                        size = swatchSize,
                                        selected = editor.color == color,
                                        onClick = { select(color) }
                                    )
                                }
                                for (customColumn in 0 until 2) {
                                    val index = customColumn * 3 + rowIndex
                                    CustomSwatchSlot(
                                        color = editor.customSwatches.getOrNull(index),
                                        size = swatchSize,
                                        selected = editingSwatch == index,
                                        onClick = { tapCustomSwatch(index) },
                                        onLongClick = { editCustomSwatch(index) }
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.width(wheelSize),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "HUE · ${hue.roundToInt()}°",
                            style = type.caption,
                            color = palette.textTertiary
                        )
                        Spacer(Modifier.height(5.dp))
                        HueWheel(
                            hue = hue,
                            color = Color(hsvColor(hue, sat, value)),
                            onHueChange = { hue = it; commit() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (editingSwatch != null) {
                    "Editing a custom swatch — the wheel and sliders now set it."
                } else {
                    "Tap a custom slot to save the current ink; hold it to edit with the wheel."
                },
                style = type.caption,
                color = if (editingSwatch != null) palette.accent else palette.textTertiary,
                lineHeight = 16.sp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomSwatchSlot(
    color: Int?,
    size: Dp,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val palette = LocalPenny.current
    val shape = CircleShape
    val ringWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = PennyMotion.settleDp(),
        label = "customSwatchRing"
    )
    val ringColor by animateColorAsState(
        targetValue = if (selected) palette.accent else palette.hairlineStrong,
        animationSpec = PennyMotion.color(),
        label = "customSwatchRingColor"
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(if (color == null) palette.surfaceSunken else Color(color))
            .border(ringWidth, ringColor, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        if (color == null) {
            Icon(
                PennyIcons.Plus,
                contentDescription = "Empty custom color",
                modifier = Modifier.size(11.dp),
                tint = palette.textTertiary
            )
        }
    }
}

@Composable
private fun HueWheel(
    hue: Float,
    color: Color,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPenny.current
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    fun update(position: Offset) {
                        val dx = position.x - cx
                        val dy = position.y - cy
                        if (dx * dx + dy * dy < 1f) return
                        val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                        onHueChange(if (angle < 0f) angle + 360f else angle)
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    update(down.position)
                    down.consume()
                    var event = awaitPointerEvent()
                    while (event.changes.any { it.pressed }) {
                        update(event.changes.first().position)
                        event.changes.forEach { it.consume() }
                        event = awaitPointerEvent()
                    }
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(cx, cy)
        val wheelSize = min(size.width, size.height)
        val ringWidth = (wheelSize * 0.16f).coerceAtLeast(12.dp.toPx())
        val radius = wheelSize / 2f - ringWidth / 2f - 1.dp.toPx()

        drawCircle(
            brush = Brush.sweepGradient(HueWheelColors),
            radius = radius,
            center = center,
            style = DrawStroke(width = ringWidth)
        )
        drawCircle(
            color = palette.hairlineStrong,
            radius = radius + ringWidth / 2f,
            center = center,
            style = DrawStroke(width = 0.8.dp.toPx())
        )
        drawCircle(
            color = palette.hairlineStrong,
            radius = radius - ringWidth / 2f,
            center = center,
            style = DrawStroke(width = 0.8.dp.toPx())
        )

        val innerRadius = radius - ringWidth / 2f - 2.dp.toPx()
        drawCircle(color = color, radius = innerRadius, center = center)
        drawCircle(
            color = palette.hairline,
            radius = innerRadius,
            center = center,
            style = DrawStroke(width = 0.8.dp.toPx())
        )

        val angle = Math.toRadians(hue.toDouble()).toFloat()
        val thumb = Offset(cx + cos(angle) * radius, cy + sin(angle) * radius)
        drawCircle(
            color = palette.shadow,
            radius = ringWidth * 0.66f,
            center = Offset(thumb.x, thumb.y + 1.dp.toPx())
        )
        drawCircle(color = Color.White, radius = ringWidth * 0.62f, center = thumb)
        drawCircle(
            color = Color(hsvColor(hue, 1f, 1f)),
            radius = ringWidth * 0.38f,
            center = thumb
        )
    }
}

private fun layerDescriptor(editor: EditorModel, layer: Layer): String {
    val parts = buildList {
        if (layer.id == editor.layer.id) add("Active layer")
        if (layer.isReference) add("Reference")
        if (!layer.visible) add("Hidden")
        if (isEmpty()) {
            add(
                if (layer.strokes.size == 1) "1 stroke"
                else "${layer.strokes.size} strokes"
            )
        }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun SliderBlock(
    label: String,
    valueText: String,
    content: @Composable () -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = type.label, color = palette.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(valueText, style = type.mono, color = palette.textPrimary)
        }
        Spacer(Modifier.height(2.dp))
        content()
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun BoxScope.BrushSheet(visible: Boolean, editor: EditorModel, onDismiss: () -> Unit) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val tool = editor.tool
    val minWidth = tool.minWidth()
    val maxWidth = tool.maxWidth()
    val quickSizes = when (tool) {
        Tool.PEN, Tool.PENCIL, Tool.SHAPE -> listOf(2f, 4f, 8f, 16f, 32f)
        Tool.MORPH -> listOf(24f, 48f, 72f, 120f, 200f)
        else -> listOf(16f, 32f, 64f, 120f)
    }
    val previewColor = if (tool == Tool.ERASER) 0xFF8E8E93.toInt() else editor.color
    val previewStroke = remember(
        tool, editor.color, editor.size, editor.markerOpacity, editor.shapeKind
    ) {
        if (tool == Tool.SHAPE) {
            val g = when (editor.shapeKind) {
                ShapeKind.LINE -> floatArrayOf(40f, 236f, 560f, 64f)
                ShapeKind.RECTANGLE -> floatArrayOf(80f, 58f, 520f, 242f)
                ShapeKind.SQUARE -> floatArrayOf(210f, 58f, 430f, 278f)
                ShapeKind.ELLIPSE -> floatArrayOf(70f, 58f, 530f, 242f)
                ShapeKind.CIRCLE -> floatArrayOf(210f, 58f, 430f, 278f)
            }
            Shapes.build(
                kind = editor.shapeKind,
                x0 = g[0],
                y0 = g[1],
                x1 = g[2],
                y1 = g[3],
                color = previewColor,
                width = editor.size
            ) ?: Stroke(
                Tool.PEN, previewColor, editor.size, 1f,
                floatArrayOf(60f, 150f, 1f, 540f, 150f, 1f)
            )
        } else {
            val n = 96
            val points = FloatArray(n * 3)
            for (i in 0 until n) {
                val t = i / (n - 1f)
                points[i * 3] = 8f + t * 584f
                points[i * 3 + 1] = 150f + sin(t * 4.1f) * 32f
                points[i * 3 + 2] = 0.15f + 0.85f * sin(t * Math.PI).toFloat()
            }
            Stroke(
                tool = if (tool == Tool.ERASER) Tool.PEN else tool,
                color = previewColor,
                width = editor.size,
                opacity = if (tool == Tool.MARKER) editor.markerOpacity else 1f,
                points = points
            )
        }
    }

    PennySheet(
        visible = visible,
        onDismiss = onDismiss,
        title = tool.label,
        subtitle = when (tool) {
            Tool.PEN -> "Pressure-tapered ink"
            Tool.PENCIL -> "Grainy graphite"
            Tool.MARKER -> "Broad translucent colour"
            Tool.SHAPE -> "Drag to place a vector outline"
            Tool.ERASER -> "Removes ink from the active layer"
            Tool.MORPH -> "Pulls the ink under the brush"
        },
        leading = {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(palette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (tool) {
                        Tool.PEN -> PennyIcons.Pen
                        Tool.PENCIL -> PennyIcons.Pencil
                        Tool.MARKER -> PennyIcons.Marker
                        Tool.SHAPE -> shapeIcon(editor.shapeKind)
                        Tool.ERASER -> PennyIcons.Eraser
                        Tool.MORPH -> PennyIcons.Morph
                    },
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (tool == Tool.SHAPE) {
                SectionLabel("KIND")
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (kind in ShapeKind.entries) {
                        ShapeChoice(
                            kind = kind,
                            selected = editor.shapeKind == kind,
                            modifier = Modifier.weight(1f),
                            onClick = { editor.chooseShape(kind) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            if (tool != Tool.MORPH) {
                PreviewBox(projectBackground = editor.project.background, stroke = previewStroke)
                Spacer(Modifier.height(16.dp))
            }
            SliderBlock(
                label = if (tool == Tool.MORPH) "Area" else "Size",
                valueText = "${editor.size.roundToInt()} px"
            ) {
                PennySlider(
                    value = editor.size,
                    onValueChange = { editor.changeSize(it) },
                    valueRange = minWidth..maxWidth
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (size in quickSizes) {
                    ChoiceChip(
                        label = "${size.roundToInt()}",
                        selected = (editor.size - size).let { kotlin.math.abs(it) < 0.6f },
                        onClick = { editor.changeSize(size) }
                    )
                }
            }
            if (tool != Tool.SHAPE && tool != Tool.MORPH) {
                Spacer(Modifier.height(16.dp))
                Hairline()
                Spacer(Modifier.height(14.dp))
                ToggleRow(
                    title = "Pressure",
                    subtitle = "Vary width with stylus pressure",
                    checked = editor.pressureEnabled,
                    onCheckedChange = { editor.togglePressure() }
                )
            }
            if (tool == Tool.MARKER) {
                Spacer(Modifier.height(14.dp))
                Hairline()
                Spacer(Modifier.height(14.dp))
                SliderBlock(
                    label = "Marker opacity",
                    valueText = "${(editor.markerOpacity * 100).roundToInt()}%"
                ) {
                    PennySlider(
                        value = editor.markerOpacity,
                        onValueChange = { editor.changeMarkerOpacity(it) },
                        valueRange = 0.05f..0.9f
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when (tool) {
                    Tool.PEN -> "Pen width follows stylus pressure. Hold the pen's side button for a temporary eraser."
                    Tool.PENCIL -> "Graphite texture is generated at your ink colour, so it feels the same at every zoom level."
                    Tool.MARKER -> "Marker ink is composited once per stroke, so overlaps never turn darker at the seams."
                    Tool.SHAPE -> "Drag from corner to corner. Square and circle keep their aspect; release to commit the outline. Shapes are vectors, so undo and export treat them like any stroke."
                    Tool.ERASER -> "The eraser only touches the active layer. Hide a layer to protect it."
                    Tool.MORPH -> "Drag to pull the ink under the brush. Each drag is one undoable step; two fingers pan and zoom."
                },
                style = type.caption,
                color = palette.textTertiary,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun PreviewBox(projectBackground: Int, stroke: Stroke) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    Column {
        Row {
            SectionLabel("PREVIEW")
            Spacer(Modifier.weight(1f))
            Text(
                "${stroke.width.roundToInt()} px",
                style = type.caption,
                color = palette.textTertiary
            )
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(projectBackground))
                .border(0.6.dp, palette.hairline, RoundedCornerShape(16.dp))
        ) {
            val designWidth = 600f
            val scale = size.width / designWidth
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.translate(0f, size.height / 2f)
                native.scale(scale, scale)
                native.translate(0f, -150f)
                Ink.renderStroke(native, stroke)
                native.restore()
            }
        }
    }
}

@Composable
private fun ShapeChoice(
    kind: ShapeKind,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val shape = RoundedCornerShape(11.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) palette.accentSoft else palette.surfaceSunken)
            .border(
                if (selected) 1.2.dp else 0.6.dp,
                if (selected) palette.accent else palette.hairline,
                shape
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            shapeIcon(kind),
            contentDescription = kind.label,
            tint = if (selected) palette.accent else palette.textSecondary,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            kind.label,
            style = type.caption.copy(fontSize = 9.sp),
            color = if (selected) palette.accent else palette.textSecondary,
            maxLines = 1
        )
    }
}

@Composable
fun BoxScope.LayersSheet(
    visible: Boolean,
    editor: EditorModel,
    onImportReference: () -> Unit,
    onImportVideo: () -> Unit,
    onPositionReference: () -> Unit,
    onShiftLayer: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val frame = editor.frame
    val layerCount = frame.layers.size
    val indices = remember(frame.layers.size) { frame.layers.indices.reversed().toList() }
    val selectedLayer = editor.layer
    val canMerge = com.dahl404.penny937.model.ProjectOps.canMergeLayerDown(
        frame, editor.currentLayer
    )

    PennySheet(
        visible = visible,
        onDismiss = onDismiss,
        title = "Layers",
        subtitle = "Frame ${editor.currentFrame + 1} · $layerCount ${if (layerCount == 1) "layer" else "layers"}",
        leading = {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(palette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    PennyIcons.Layers,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SecondaryButton(
                text = "Add layer",
                onClick = { editor.addLayer() },
                modifier = Modifier.fillMaxWidth(),
                icon = PennyIcons.Plus,
                tint = palette.accent
            )
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = "References in the export",
                subtitle = "New imports are drawn into the final video",
                checked = editor.referenceInOutput,
                onCheckedChange = { editor.setReferenceOutputDefault(it) }
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Import reference image",
                onClick = onImportReference,
                modifier = Modifier.fillMaxWidth(),
                icon = PennyIcons.Image,
                tint = palette.accent
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Import reference video",
                onClick = onImportVideo,
                modifier = Modifier.fillMaxWidth(),
                icon = PennyIcons.Video,
                tint = palette.accent
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "References stay locked guides unless you turn on export above. " +
                    "Video clips are sampled at ${editor.project.fps} fps and fill frames from the current frame to the end of the clip.",
                style = type.caption,
                color = palette.textTertiary,
                modifier = Modifier.padding(horizontal = 4.dp),
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 276.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(indices, key = { frame.layers[it].id }) { index ->
                    val layer = frame.layers[index]
                    val selected = index == editor.currentLayer
                    val shape = RoundedCornerShape(12.dp)
                    val rowBackground by animateColorAsState(
                        targetValue = if (selected) palette.accentSoft else palette.surfaceSunken,
                        animationSpec = PennyMotion.color(),
                        label = "layerRowBackground"
                    )
                    val rowBorderWidth by animateDpAsState(
                        targetValue = if (selected) 1.dp else 0.5.dp,
                        animationSpec = PennyMotion.settleDp(),
                        label = "layerRowBorderWidth"
                    )
                    val rowBorderColor by animateColorAsState(
                        targetValue = if (selected) palette.accent.copy(alpha = 0.45f) else palette.hairline,
                        animationSpec = PennyMotion.color(),
                        label = "layerRowBorder"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(rowBackground)
                            .border(rowBorderWidth, rowBorderColor, shape)
                            .clickable { editor.selectLayer(index) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PennyIconButton(
                            icon = if (layer.visible) PennyIcons.Eye else PennyIcons.EyeOff,
                            onClick = { editor.setLayerVisible(index, !layer.visible) },
                            size = 32.dp,
                            iconSize = 16.dp,
                            tint = if (layer.visible) palette.textSecondary else palette.textTertiary
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 3.dp)) {
                            Text(
                                layer.name,
                                style = type.label.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = palette.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                buildString {
                                    if (layer.isReference) {
                                        append(if (layer.image?.inOutput == true) "Reference · in export"
                                        else "Reference · guide only")
                                    } else if (layer.flattened) {
                                        append("Merged image")
                                    } else {
                                        append("${layer.strokes.size} strokes")
                                    }
                                    append(" · ${(layer.opacity * 100).roundToInt()}%")
                                    if (layer.locked) append(" · locked")
                                },
                                style = type.caption,
                                color = palette.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        PennyIconButton(
                            icon = if (layer.locked) PennyIcons.Lock else PennyIcons.LockOpen,
                            onClick = { editor.setLayerLocked(index, !layer.locked) },
                            size = 28.dp,
                            iconSize = 14.dp,
                            tint = if (layer.locked) palette.accent else palette.textTertiary
                        )
                        PennyIconButton(
                            icon = PennyIcons.ArrowUp,
                            onClick = { editor.selectLayer(index); editor.moveLayerUp() },
                            size = 28.dp,
                            iconSize = 14.dp,
                            enabled = index < layerCount - 1,
                            tint = palette.textSecondary
                        )
                        PennyIconButton(
                            icon = PennyIcons.ArrowDown,
                            onClick = { editor.selectLayer(index); editor.moveLayerDown() },
                            size = 28.dp,
                            iconSize = 14.dp,
                            enabled = index > 0,
                            tint = palette.textSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Layer opacity", style = type.label, color = palette.textSecondary)
                Spacer(Modifier.weight(1f))
                Text(
                    "${(editor.layer.opacity * 100).roundToInt()}%",
                    style = type.mono,
                    color = palette.textPrimary
                )
            }
            PennySlider(
                value = editor.layer.opacity,
                onValueChange = { editor.setLayerOpacity(it) },
                valueRange = 0.05f..1f
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Move / rotate layer",
                onClick = onShiftLayer,
                modifier = Modifier.fillMaxWidth(),
                icon = PennyIcons.Rotate,
                tint = palette.accent,
                enabled = !selectedLayer.locked
            )
            if (selectedLayer.locked && !selectedLayer.isReference) {
                Spacer(Modifier.height(5.dp))
                Text(
                    "Unlock the layer to move its contents.",
                    style = type.caption,
                    color = palette.textTertiary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            if (selectedLayer.isReference) {
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    title = "Show in export",
                    subtitle = "Draw this reference into the final video",
                    checked = selectedLayer.image?.inOutput == true,
                    onCheckedChange = {
                        editor.setReferenceLayerInOutput(editor.currentLayer, it)
                    }
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = "Position reference",
                    onClick = onPositionReference,
                    modifier = Modifier.fillMaxWidth(),
                    icon = PennyIcons.Move,
                    tint = palette.accent,
                    enabled = !selectedLayer.locked
                )
                if (selectedLayer.locked) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Unlock the layer to reposition it.",
                        style = type.caption,
                        color = palette.textTertiary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = "Duplicate",
                    onClick = { editor.duplicateLayer() },
                    modifier = Modifier.weight(1f),
                    icon = PennyIcons.Copy
                )
                SecondaryButton(
                    text = "Merge down",
                    onClick = { editor.mergeLayerDown() },
                    modifier = Modifier.weight(1f),
                    icon = PennyIcons.ArrowDown,
                    enabled = canMerge
                )
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = if (layerCount <= 1 && !selectedLayer.isReference) "Clear layer"
                else "Delete layer",
                onClick = { editor.deleteLayer() },
                modifier = Modifier.fillMaxWidth(),
                icon = PennyIcons.Trash,
                tint = palette.danger,
                enabled = !selectedLayer.locked
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun BoxScope.SettingsSheet(visible: Boolean, editor: EditorModel, onDismiss: () -> Unit) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    var name by remember { mutableStateOf(editor.project.name) }
    var widthText by remember { mutableStateOf(editor.project.width.toString()) }
    var heightText by remember { mutableStateOf(editor.project.height.toString()) }
    var fps by remember { mutableFloatStateOf(editor.project.fps.toFloat()) }
    var background by remember { mutableStateOf(editor.project.background) }
    androidx.compose.runtime.LaunchedEffect(visible) {
        if (visible) {
            name = editor.project.name
            widthText = editor.project.width.toString()
            heightText = editor.project.height.toString()
            fps = editor.project.fps.toFloat()
            background = editor.project.background
        }
    }

    val width = widthText.toIntOrNull()
    val height = heightText.toIntOrNull()
    val valid = width != null && height != null &&
        width in ProjectFactory.MIN_DIM..ProjectFactory.MAX_DIM &&
        height in ProjectFactory.MIN_DIM..ProjectFactory.MAX_DIM
    val dimsChanged = width != editor.project.width || height != editor.project.height

    PennySheet(
        visible = visible,
        onDismiss = onDismiss,
        title = "Canvas",
        subtitle = "Everything is vector — resize at any time."
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionLabel("PROJECT NAME")
            Spacer(Modifier.height(7.dp))
            PennyTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                placeholder = "Untitled",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))

            SectionLabel("SIZE")
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PennyTextField(
                    value = widthText,
                    onValueChange = { widthText = it.filter(Char::isDigit).take(4) },
                    placeholder = "W",
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number
                )
                Text(
                    "×",
                    style = type.label,
                    color = palette.textTertiary,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                PennyTextField(
                    value = heightText,
                    onValueChange = { heightText = it.filter(Char::isDigit).take(4) },
                    placeholder = "H",
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                if (valid) {
                    val pixels = (width ?: 0).toLong() * (height ?: 0)
                    when {
                        pixels > 8_000_000L -> "Large canvas — rendering will use a lot of memory."
                        dimsChanged -> "Existing artwork stays at its current scale."
                        else -> "32 to 4096 pixels per side"
                    }
                } else {
                    "Each side needs to be between 32 and 4096 px"
                },
                style = type.caption,
                color = if (valid) palette.textTertiary else palette.danger
            )
            Spacer(Modifier.height(18.dp))

            SectionLabel("FRAME RATE")
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${fps.roundToInt()}",
                    style = type.title,
                    color = palette.textPrimary
                )
                Spacer(Modifier.width(5.dp))
                Text("fps", style = type.caption, color = palette.textSecondary)
                Spacer(Modifier.weight(1f))
                Text(
                    "${editor.project.frames.size} ${if (editor.project.frames.size == 1) "frame" else "frames"}",
                    style = type.caption,
                    color = palette.textTertiary
                )
            }
            PennySlider(
                value = fps,
                onValueChange = { fps = it },
                valueRange = ProjectFactory.MIN_FPS.toFloat()..ProjectFactory.MAX_FPS.toFloat()
            )
            Spacer(Modifier.height(14.dp))

            SectionLabel("PAPER")
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((_, color) in ProjectFactory.backgrounds) {
                    ColorCircle(
                        color = Color(color),
                        size = 34.dp,
                        selected = background == color,
                        onClick = { background = color }
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            PrimaryButton(
                text = "Apply changes",
                onClick = {
                    editor.updateSettings(
                        name = name.ifBlank { editor.project.name },
                        width = width ?: editor.project.width,
                        height = height ?: editor.project.height,
                        fps = fps.roundToInt(),
                        background = background
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = valid
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Projects have no maximum length. Add frames for as long as the idea lasts.",
                style = type.caption,
                color = palette.textTertiary,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun BoxScope.FrameSheet(
    visible: Boolean,
    editor: EditorModel,
    onDismiss: () -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val frame = editor.frame
    val frames = editor.project.frames
    val listState = rememberLazyListState()
    var lastShownFrame by remember { mutableIntStateOf(-1) }
    LaunchedEffect(visible, editor.currentFrame) {
        if (!visible) {
            lastShownFrame = -1
            return@LaunchedEffect
        }
        // Jump into place on open; animate only when the selection changes
        // while the sheet is already visible, so the entrance stays clean.
        if (lastShownFrame < 0) {
            listState.scrollToItem(editor.currentFrame)
        } else if (lastShownFrame != editor.currentFrame) {
            listState.animateScrollToItem(editor.currentFrame)
        }
        lastShownFrame = editor.currentFrame
    }
    PennySheet(
        visible = visible,
        onDismiss = onDismiss,
        title = "Frames",
        subtitle = "${frames.size} frames · ${editor.project.fps} fps · frame ${editor.currentFrame + 1}",
        leading = {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(palette.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    PennyIcons.Frame,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(frames, key = { _, item -> item.id }) { index, item ->
                    FrameStripItem(
                        project = editor.project,
                        frame = item,
                        index = index,
                        selected = index == editor.currentFrame,
                        onClick = { editor.selectFrame(index) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = "Move left",
                    onClick = { editor.moveFrame(editor.currentFrame, editor.currentFrame - 1) },
                    modifier = Modifier.weight(1f),
                    icon = PennyIcons.ChevronLeft,
                    enabled = editor.currentFrame > 0
                )
                SecondaryButton(
                    text = "Move right",
                    onClick = { editor.moveFrame(editor.currentFrame, editor.currentFrame + 1) },
                    modifier = Modifier.weight(1f),
                    icon = PennyIcons.ChevronRight,
                    enabled = editor.currentFrame < frames.lastIndex
                )
            }
            Spacer(Modifier.height(14.dp))
            Hairline()
            Spacer(Modifier.height(14.dp))
            OnionSliderRow(editor)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hold", style = type.bodyStrong, color = palette.textPrimary)
                    Text(
                        "Repeat this frame for extra ticks.",
                        style = type.caption,
                        color = palette.textSecondary
                    )
                }
                ValueStepper(
                    value = frame.hold,
                    onValueChange = { editor.setFrameHold(it) },
                    range = 1..24
                )
            }
            Spacer(Modifier.height(14.dp))
            SecondaryButton(
                text = "Empty frame",
                onClick = {
                    editor.emptyFrame()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                icon = PennyIcons.Square
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Delete frame",
                onClick = {
                    editor.deleteFrame()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                icon = PennyIcons.Trash,
                tint = palette.danger,
                enabled = frames.size > 1
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun FrameStripItem(
    project: com.dahl404.penny937.model.Project,
    frame: Frame,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val shape = RoundedCornerShape(9.dp)
    val width = (56f * project.width / project.height).coerceIn(34f, 82f).dp
    val ringWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 0.6.dp,
        animationSpec = PennyMotion.settleDp(),
        label = "frameStripRingWidth"
    )
    val ringColor by animateColorAsState(
        targetValue = if (selected) palette.accent else palette.hairline,
        animationSpec = PennyMotion.color(),
        label = "frameStripRing"
    )
    Box(
        modifier = Modifier
            .width(width)
            .height(56.dp)
            .clip(shape)
            .background(Color(project.background))
            .border(ringWidth, ringColor, shape)
            .clickable(onClick = onClick)
    ) {
        FramePreview(
            project = project,
            frame = frame,
            modifier = Modifier.fillMaxSize(),
            widthPx = 112
        )
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

@Composable
private fun OnionSliderRow(editor: EditorModel) {
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
            modifier = Modifier.width(66.dp)
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
fun BoxScope.ExportSheet(visible: Boolean, editor: EditorModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    var format by remember { mutableStateOf(ExportFormat.MP4) }
    var scale by remember { mutableIntStateOf(720) }
    val state = editor.exportState
    val project = editor.project
    val videoPlan = remember(project.width, project.height, scale, format) {
        if (format == ExportFormat.MP4) {
            val width = if (scale <= 0) project.width else min(scale, project.width)
            VideoPlan.dimensions(project, width)
        } else null
    }
    val targetWidth = if (format == ExportFormat.CURRENT_FRAME) project.width
    else if (scale <= 0) project.width else kotlin.math.min(scale, project.width)

    PennySheet(
        visible = visible,
        onDismiss = onDismiss,
        title = "Export",
        subtitle = "${project.frames.size} ${if (project.frames.size == 1) "frame" else "frames"} · ${project.fps} fps · ${
            String.format("%.1f", project.durationSeconds)
        }s"
    ) {
        Column {
            SegmentedControl(
                options = listOf("MP4", "GIF", "Frames", "Still"),
                selectedIndex = when (format) {
                    ExportFormat.MP4 -> 0
                    ExportFormat.GIF -> 1
                    ExportFormat.PNG_SEQUENCE -> 2
                    ExportFormat.CURRENT_FRAME -> 3
                },
                onSelect = {
                    if (editor.exportState is ExportState.Running) editor.cancelExport()
                    format = when (it) {
                        0 -> ExportFormat.MP4
                        1 -> ExportFormat.GIF
                        2 -> ExportFormat.PNG_SEQUENCE
                        else -> ExportFormat.CURRENT_FRAME
                    }
                    editor.dismissExport()
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (format.isTimeline) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(
                        label = "Full",
                        selected = scale <= 0,
                        onClick = { scale = 0 }
                    )
                    ChoiceChip(
                        label = "720px",
                        selected = scale == 720,
                        onClick = { scale = 720 }
                    )
                    ChoiceChip(
                        label = "480px",
                        selected = scale == 480,
                        onClick = { scale = 480 }
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surfaceSunken)
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        when (format) {
                            ExportFormat.MP4 -> "H.264 video. Plays anywhere, including Photos and messages."
                            ExportFormat.GIF -> "Animated GIF with per-frame palettes and dithering."
                            ExportFormat.PNG_SEQUENCE -> "A zip of lossless PNG frames — the best master copy."
                            ExportFormat.CURRENT_FRAME -> "The current frame as a lossless PNG."
                        },
                        style = type.body,
                        color = palette.textPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (format) {
                            ExportFormat.MP4 -> {
                                val (w, h) = videoPlan ?: (project.width to project.height)
                                "Output $w × $h px at ${project.fps} fps · ${String.format("%.1f", project.durationSeconds)}s"
                            }
                            ExportFormat.GIF -> "Output ${targetWidth} px wide · timing quantized to 1/100s"
                            ExportFormat.PNG_SEQUENCE -> "Output ${project.width} × ${project.height} px per frame"
                            ExportFormat.CURRENT_FRAME -> "${project.width} × ${project.height} px"
                        },
                        style = type.caption,
                        color = palette.textSecondary
                    )
                }
            }

            if (editor.project.frames.any { frame -> frame.layers.any { it.isReference } }) {
                val referencesInOutput = editor.project.frames.any { frame ->
                    frame.layers.any { it.image?.inOutput == true }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (referencesInOutput) {
                        "References marked \"in export\" are included; guide-only references stay out."
                    } else {
                        "Reference layers are guides and will not be included in the export."
                    },
                    style = type.caption,
                    color = palette.textTertiary,
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.height(16.dp))
            when (state) {
                is ExportState.Idle, is ExportState.Failed -> {
                    if (state is ExportState.Failed) {
                        Text(
                            "Export failed: ${(state as ExportState.Failed).message}",
                            style = type.caption,
                            color = palette.danger
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    PrimaryButton(
                        text = when (format) {
                            ExportFormat.MP4 -> "Export MP4"
                            ExportFormat.GIF -> "Export GIF"
                            ExportFormat.PNG_SEQUENCE -> "Export PNG frames"
                            ExportFormat.CURRENT_FRAME -> "Export still"
                        },
                        onClick = { editor.startExport(format, targetWidth) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = PennyIcons.Share
                    )
                }
                is ExportState.Running -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rendering", style = type.label, color = palette.textSecondary)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${(state.progress * 100).roundToInt()}%",
                            style = type.mono,
                            color = palette.textPrimary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(palette.surfaceSunken)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(palette.accent)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    SecondaryButton(
                        text = "Cancel export",
                        onClick = { editor.cancelExport() },
                        modifier = Modifier.fillMaxWidth(),
                        icon = PennyIcons.Close
                    )
                }
                is ExportState.Done -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatPill("Ready")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${state.file.nameWithoutExtension} · ${state.file.length() / 1024} KB",
                            style = type.caption,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "Share export",
                        onClick = { SharedFiles.share(context, state.file, state.mime) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = PennyIcons.Share
                    )
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(
                        text = "Export another",
                        onClick = { editor.dismissExport() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Exports are written to the app's cache and shared through the system sheet. Save them to Photos or Files from there.",
                style = type.caption,
                color = palette.textTertiary,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun BoxScope.ProjectMenuSheet(
    visible: Boolean,
    editor: EditorModel,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExport: () -> Unit,
    onShareProject: (() -> Unit)? = null,
    onDuplicateProject: () -> Unit,
    onDeleteProject: () -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    PennySheet(
        visible = visible,
        onDismiss = onDismiss,
        title = editor.project.name,
        subtitle = "${editor.project.width} × ${editor.project.height} · ${editor.project.fps} fps"
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SecondaryButton(
                text = "Canvas & frame rate",
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                icon = PennyIcons.Sliders
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Export animation",
                onClick = onOpenExport,
                modifier = Modifier.fillMaxWidth(),
                icon = PennyIcons.Share
            )
            Spacer(Modifier.height(16.dp))
            Hairline()
            Spacer(Modifier.height(14.dp))
            ToggleRow(
                title = "Stylus only",
                subtitle = "Fingers navigate instead of drawing",
                checked = editor.stylusOnly,
                onCheckedChange = { editor.toggleStylusOnly() }
            )
            Spacer(Modifier.height(12.dp))
            ToggleRow(
                title = "Pressure",
                subtitle = "Use stylus pressure to vary stroke width",
                checked = editor.pressureEnabled,
                onCheckedChange = { editor.togglePressure() }
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Grid style", style = type.label, color = palette.textSecondary)
                Spacer(Modifier.width(10.dp))
                for (style in GridStyle.entries) {
                    ChoiceChip(
                        label = style.label,
                        selected = editor.gridStyle == style,
                        onClick = { editor.chooseGridStyle(style) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            if (onShareProject != null) {
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Export project (.zip)",
                    onClick = onShareProject,
                    modifier = Modifier.fillMaxWidth(),
                    icon = PennyIcons.Share
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                "The zip bundles the document and every reference image or video frame it uses.",
                style = type.caption,
                color = palette.textTertiary,
                modifier = Modifier.padding(horizontal = 4.dp),
                lineHeight = 16.sp
            )
            if (editor.saveError != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    editor.saveError!!,
                    style = type.caption,
                    color = palette.danger,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Retry save",
                    onClick = { editor.retrySave() },
                    modifier = Modifier.fillMaxWidth(),
                    icon = PennyIcons.Check,
                    tint = palette.accent
                )
            } else if (editor.saving) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Saving…",
                    style = type.caption,
                    color = palette.textTertiary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Hairline()
            Spacer(Modifier.height(14.dp))
            SecondaryButton(
                text = "Duplicate project",
                onClick = onDuplicateProject,
                modifier = Modifier.fillMaxWidth(),
                icon = PennyIcons.Copy
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Delete project",
                onClick = onDeleteProject,
                modifier = Modifier.fillMaxWidth(),
                icon = PennyIcons.Trash,
                tint = palette.danger
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (editor.stylusOnly) {
                    "Stylus draws. Two fingers pan and zoom. Palms are ignored."
                } else {
                    "Finger or stylus draws. Two fingers pan and zoom."
                },
                style = type.caption,
                color = palette.textTertiary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
fun BoxScope.ConfirmDeleteSheet(
    visible: Boolean,
    projectName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    PennySheet(
        visible = visible,
        onDismiss = onDismiss,
        title = "Delete project?",
        subtitle = projectName
    ) {
        Column {
            Text(
                "This removes every frame and layer from this device. It cannot be undone.",
                style = type.body,
                color = palette.textSecondary
            )
            Spacer(Modifier.height(18.dp))
            SecondaryButton(
                text = "Delete permanently",
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                icon = PennyIcons.Trash,
                tint = palette.danger
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Keep project",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}
