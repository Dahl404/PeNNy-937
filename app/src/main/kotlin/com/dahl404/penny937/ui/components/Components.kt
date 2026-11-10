package com.dahl404.penny937.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.Image
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.dahl404.penny937.model.Frame
import com.dahl404.penny937.model.Project
import com.dahl404.penny937.paint.FrameRasterizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dahl404.penny937.ui.icons.PennyIcons
import com.dahl404.penny937.ui.theme.LocalPenny
import com.dahl404.penny937.ui.theme.LocalPennyType
import com.dahl404.penny937.ui.theme.PennyDimens
import com.dahl404.penny937.ui.theme.PennyMotion

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = LocalPenny.current.hairline) {
    Box(modifier.fillMaxWidth().height(PennyDimens.hairline).background(color))
}

@Composable
fun PennyCard(
    modifier: Modifier = Modifier,
    corner: Dp = PennyDimens.cardRadius,
    color: Color = LocalPenny.current.surface,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalPenny.current
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .shadow(10.dp, shape, clip = false, ambientColor = palette.shadow, spotColor = palette.shadow)
            .clip(shape)
            .background(color)
            .border(PennyDimens.hairline, palette.hairline, shape),
        content = content
    )
}

@Composable
fun PennyIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    iconSize: Dp = 19.dp,
    enabled: Boolean = true,
    selected: Boolean = false,
    tint: Color = LocalPenny.current.textPrimary,
    background: Color = Color.Transparent,
    contentDescription: String? = null
) {
    val palette = LocalPenny.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.92f else 1f,
        animationSpec = PennyMotion.pressFloat(),
        label = "iconPress"
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (selected) palette.accent else background)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = palette.textPrimary.copy(alpha = 0.1f)),
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = when {
                selected -> Color.White
                enabled -> tint
                else -> palette.textTertiary
            }
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.975f else 1f,
        animationSpec = PennyMotion.pressFloat(),
        label = "primaryPress"
    )
    Row(
        modifier = modifier
            .height(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (enabled) palette.accent else palette.surfaceSunken)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White.copy(alpha = 0.18f)),
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = Color.White)
        }
        Text(
            text = text,
            style = type.headline,
            color = if (enabled) Color.White else palette.textTertiary
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = LocalPenny.current.textPrimary,
    enabled: Boolean = true
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.985f else 1f,
        animationSpec = PennyMotion.pressFloat(),
        label = "secondaryPress"
    )
    Row(
        modifier = modifier
            .height(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(palette.surface)
            .border(PennyDimens.hairline, palette.hairline, shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = palette.textPrimary.copy(alpha = 0.08f)),
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (icon != null) {
            Icon(
                icon,
                null,
                modifier = Modifier.size(17.dp),
                tint = if (enabled) tint else palette.textTertiary
            )
        }
        Text(text, style = type.label, color = if (enabled) tint else palette.textTertiary)
    }
}

@Composable
fun PennySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    accent: Color = LocalPenny.current.accent,
    trackColors: List<Color>? = null
) {
    val palette = LocalPenny.current
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(valueRange) {
                awaitEachGesture {
                    val thumb = 10.dp.toPx()
                    val usable = (size.width.toFloat() - thumb * 2f).coerceAtLeast(1f)
                    fun update(x: Float) {
                        val f = ((x - thumb) / usable).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + f * span)
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    update(down.position.x)
                    down.consume()
                    var event = awaitPointerEvent()
                    while (event.changes.any { it.pressed }) {
                        update(event.changes.first().position.x)
                        event.changes.forEach { it.consume() }
                        event = awaitPointerEvent()
                    }
                }
            }
    ) {
        val cy = size.height / 2f
        val track = 4.dp.toPx()
        val thumb = 10.dp.toPx()
        val radius = CornerRadius(track / 2f, track / 2f)
        drawRoundRect(
            color = palette.surfaceSunken,
            topLeft = Offset(0f, cy - track / 2f),
            size = Size(size.width, track),
            cornerRadius = radius
        )
        if (trackColors != null && trackColors.size >= 2) {
            drawRoundRect(
                brush = Brush.horizontalGradient(trackColors),
                topLeft = Offset(0f, cy - track / 2f),
                size = Size(size.width, track),
                cornerRadius = radius
            )
        } else {
            drawRoundRect(
                color = accent,
                topLeft = Offset(0f, cy - track / 2f),
                size = Size(size.width * fraction, track),
                cornerRadius = radius
            )
        }
        val cx = thumb + (size.width - thumb * 2f) * fraction
        drawCircle(color = palette.shadow, radius = thumb + 3.dp.toPx(), center = Offset(cx, cy + 1.dp.toPx()))
        drawCircle(color = Color.White, radius = thumb, center = Offset(cx, cy))
        drawCircle(
            color = palette.hairlineStrong,
            radius = thumb,
            center = Offset(cx, cy),
            style = Stroke(width = 0.8.dp.toPx())
        )
        drawCircle(color = accent, radius = 3.4.dp.toPx(), center = Offset(cx, cy))
    }
}

/**
 * A centre-detented slider for the onion skin: -1 shows the previous frame,
 * 0 is off, +1 shows the next frame. Only one direction is active at a time.
 */
@Composable
fun BipolarSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    negativeColor: Color = Color(0xFF3B9CFF),
    positiveColor: Color = Color(0xFFFF5A48)
) {
    val palette = LocalPenny.current
    val v = value.coerceIn(-1f, 1f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val thumbPx = 8.dp.toPx()
                    val usable = (size.width - thumbPx * 2f).coerceAtLeast(1f)
                    fun update(x: Float) {
                        var nv = ((x - thumbPx) / usable) * 2f - 1f
                        nv = nv.coerceIn(-1f, 1f)
                        if (abs(nv) < 0.07f) nv = 0f
                        onValueChange(nv)
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    update(down.position.x)
                    down.consume()
                    var event = awaitPointerEvent()
                    while (event.changes.any { it.pressed }) {
                        update(event.changes.first().position.x)
                        event.changes.forEach { it.consume() }
                        event = awaitPointerEvent()
                    }
                }
            }
    ) {
        val thumbPx = 8.dp.toPx()
        val track = 4.dp.toPx()
        val cy = size.height / 2f
        val left = thumbPx
        val right = size.width - thumbPx
        val usable = (right - left).coerceAtLeast(1f)
        val center = (left + right) / 2f
        val x = center + v * usable / 2f
        val radius = CornerRadius(track / 2f, track / 2f)

        drawRoundRect(
            color = palette.surfaceSunken,
            topLeft = Offset(left, cy - track / 2f),
            size = Size(usable, track),
            cornerRadius = radius
        )
        val activeColor = when {
            v < -0.005f -> negativeColor
            v > 0.005f -> positiveColor
            else -> palette.textTertiary
        }
        if (v < -0.005f) {
            drawRoundRect(
                color = negativeColor,
                topLeft = Offset(x, cy - track / 2f),
                size = Size((center - x).coerceAtLeast(0f), track),
                cornerRadius = radius
            )
        } else if (v > 0.005f) {
            drawRoundRect(
                color = positiveColor,
                topLeft = Offset(center, cy - track / 2f),
                size = Size((x - center).coerceAtLeast(0f), track),
                cornerRadius = radius
            )
        }
        drawCircle(
            color = palette.textTertiary.copy(alpha = 0.6f),
            radius = 1.6.dp.toPx(),
            center = Offset(center, cy)
        )
        drawCircle(
            color = palette.shadow,
            radius = thumbPx + 3.dp.toPx(),
            center = Offset(x, cy + 1.dp.toPx())
        )
        drawCircle(color = Color.White, radius = thumbPx, center = Offset(x, cy))
        drawCircle(color = activeColor, radius = 3.4.dp.toPx(), center = Offset(x, cy))
    }
}

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surfaceSunken)
            .padding(3.dp)
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(9.dp)
            val background by animateColorAsState(
                targetValue = if (selected) palette.surface else Color.Transparent,
                animationSpec = PennyMotion.color(),
                label = "segmentBackground"
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) palette.textPrimary else palette.textSecondary,
                animationSpec = PennyMotion.color(),
                label = "segmentText"
            )
            val elevation by animateDpAsState(
                targetValue = if (selected) 3.dp else 0.dp,
                animationSpec = PennyMotion.settleDp(),
                label = "segmentElevation"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .shadow(
                        elevation,
                        shape,
                        clip = false,
                        ambientColor = palette.shadow,
                        spotColor = palette.shadow
                    )
                    .clip(shape)
                    .background(background)
                    .clickable { onSelect(index) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = type.label.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LocalPennyType.current.caption.copy(letterSpacing = 0.7.sp),
        color = LocalPenny.current.textSecondary,
        modifier = modifier
    )
}

@Composable
fun PennyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    textStyle: TextStyle? = null,
    textAlign: TextAlign = TextAlign.Start
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val resolvedStyle = textStyle ?: type.body
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surfaceSunken)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        textStyle = resolvedStyle.copy(color = palette.textPrimary, textAlign = textAlign),
        cursorBrush = SolidColor(palette.accent),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = resolvedStyle,
                        color = palette.textTertiary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = textAlign
                    )
                }
                inner()
            }
        }
    )
}

@Composable
fun ColorCircle(
    color: Color,
    size: Dp = 28.dp,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val palette = LocalPenny.current
    val shape = CircleShape
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = PennyMotion.settleDp(),
        label = "colorRingWidth"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) palette.accent else palette.hairlineStrong,
        animationSpec = PennyMotion.color(),
        label = "colorRing"
    )
    val inset by animateDpAsState(
        targetValue = if (selected) 3.dp else 0.dp,
        animationSpec = PennyMotion.settleDp(),
        label = "colorRingInset"
    )
    val modifier = Modifier
        .size(size)
        .border(borderWidth, borderColor, shape)
        .padding(inset)
        .clip(shape)
        .background(color)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Box(modifier)
}

@Composable
fun BoxScope.PennySheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    androidx.activity.compose.BackHandler(enabled = visible) { onDismiss() }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.matchParentSize(),
        enter = fadeIn(PennyMotion.fadeInFloat()),
        exit = fadeOut(PennyMotion.fadeOutFloat())
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                }
        )
    }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(PennyMotion.sheetInOffset()) { it } +
            fadeIn(PennyMotion.fadeInFloat()),
        exit = slideOutVertically(PennyMotion.sheetOutOffset()) { it } +
            fadeOut(PennyMotion.fadeOutFloat())
    ) {
        val shape = RoundedCornerShape(
            topStart = PennyDimens.sheetRadius,
            topEnd = PennyDimens.sheetRadius
        )
        val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .pointerInput(Unit) { detectTapGestures { } }
                .shadow(24.dp, shape, clip = false, ambientColor = palette.shadow, spotColor = palette.shadow)
                .clip(shape)
                .background(palette.surface)
                .border(PennyDimens.hairline, palette.hairline, shape)
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 9.dp, bottom = 18.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(palette.hairlineStrong)
            )
            Spacer(Modifier.height(14.dp))
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leading != null) {
                        leading()
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = type.title, color = palette.textPrimary)
                        if (subtitle != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(subtitle, style = type.label, color = palette.textSecondary)
                        }
                    }
                    PennyIconButton(
                        icon = PennyIcons.Close,
                        onClick = onDismiss,
                        size = 32.dp,
                        iconSize = 15.dp,
                        tint = palette.textSecondary
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
            content()
        }
    }
}

/** A small rasterised preview of one frame, for sheets and pickers. */
@Composable
fun FramePreview(
    project: Project,
    frame: Frame,
    modifier: Modifier = Modifier,
    widthPx: Int = 104
) {
    val revision = frame.revision
    val bitmap by produceState<ImageBitmap?>(
        initialValue = null,
        frame.id,
        revision,
        project.width,
        project.height,
        project.background,
        widthPx
    ) {
        value = withContext(Dispatchers.Default) {
            val width = widthPx.coerceAtLeast(16)
            val height = (width.toFloat() * project.height / project.width)
                .roundToInt()
                .coerceAtLeast(1)
            FrameRasterizer.rasterize(
                project = project,
                frame = frame,
                targetWidth = width,
                targetHeight = height,
                background = project.background
            ).asImageBitmap()
        }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Box(modifier)
    }
}

@Composable
fun StatPill(text: String, modifier: Modifier = Modifier) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surfaceSunken)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, style = type.caption, color = palette.textSecondary)
    }
}

@Composable
fun ValueStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (label != null) {
            Text(label, style = type.label, color = palette.textSecondary)
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(palette.surfaceSunken)
                .heightIn(min = 34.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PennyIconButton(
                    icon = PennyIcons.Minus,
                    onClick = { onValueChange((value - 1).coerceIn(range)) },
                    size = 34.dp,
                    iconSize = 14.dp,
                    enabled = value > range.first
                )
                Text(
                    text = value.toString(),
                    style = type.bodyStrong,
                    color = palette.textPrimary,
                    modifier = Modifier.width(26.dp),
                    textAlign = TextAlign.Center
                )
                PennyIconButton(
                    icon = PennyIcons.Plus,
                    onClick = { onValueChange((value + 1).coerceIn(range)) },
                    size = 34.dp,
                    iconSize = 14.dp,
                    enabled = value < range.last
                )
            }
        }
    }
}

@Composable
fun PennyToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPenny.current
    val offset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        animationSpec = PennyMotion.gentleDp(),
        label = "toggle"
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) palette.accent else palette.surfaceSunken,
        animationSpec = PennyMotion.color(),
        label = "toggleTrack"
    )
    Box(
        modifier = modifier
            .width(44.dp)
            .height(26.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .offset(x = offset)
                .size(22.dp)
                .shadow(2.dp, CircleShape, clip = false, ambientColor = palette.shadow, spotColor = palette.shadow)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = type.bodyStrong, color = palette.textPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = type.caption, color = palette.textSecondary)
            }
        }
        PennyToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val shape = RoundedCornerShape(9.dp)
    val background by animateColorAsState(
        targetValue = if (selected) palette.accentSoft else palette.surfaceSunken,
        animationSpec = PennyMotion.color(),
        label = "chipBackground"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 1.dp else 0.6.dp,
        animationSpec = PennyMotion.settleDp(),
        label = "chipBorderWidth"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) palette.accent.copy(alpha = 0.5f) else palette.hairline,
        animationSpec = PennyMotion.color(),
        label = "chipBorder"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) palette.accent else palette.textSecondary,
        animationSpec = PennyMotion.color(),
        label = "chipText"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(borderWidth, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            style = type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LocalPennyType.current.label,
        color = LocalPenny.current.textTertiary,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}
