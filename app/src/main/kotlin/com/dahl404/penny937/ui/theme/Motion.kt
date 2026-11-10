package com.dahl404.penny937.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * One motion vocabulary for the whole app.
 *
 * Apple-style motion is mostly about timing and settling: interactions start
 * immediately, decelerate gently, and never overshoot far enough to read as a
 * bounce. Long movements use a soft spring; opacity and colour use a matching
 * ease so layers arrive together instead of stepping.
 */
object PennyMotion {
    /** Quick, confident ease-out for things arriving on screen. */
    val standard: Easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)

    /** Symmetric ease for cross-fades and colour changes. */
    val easeInOut: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    /** Ease-in used when something leaves; it should not linger. */
    val exit: Easing = CubicBezierEasing(0.55f, 0.06f, 0.68f, 0.19f)

    const val fadeInMillis = 220
    const val fadeOutMillis = 160
    const val contentMillis = 260

    /** Small controls: press in fast, release with a short, stable spring. */
    fun pressFloat(): SpringSpec<Float> =
        spring(dampingRatio = 0.74f, stiffness = 900f, visibilityThreshold = 0.001f)

    /** Selection rings and chevrons; the settle is barely perceptible. */
    fun settleDp(): SpringSpec<Dp> =
        spring(dampingRatio = 0.9f, stiffness = 420f, visibilityThreshold = 0.01.dp)

    /** Larger surfaces: floating windows, toggles, layout size changes. */
    fun gentleDp(): SpringSpec<Dp> =
        spring(dampingRatio = 0.94f, stiffness = 300f, visibilityThreshold = 0.01.dp)

    fun gentleSize(): SpringSpec<IntSize> =
        spring(dampingRatio = 0.94f, stiffness = 300f, visibilityThreshold = IntSize(1, 1))

    fun gentleOffset(): SpringSpec<IntOffset> =
        spring(dampingRatio = 0.94f, stiffness = 300f, visibilityThreshold = IntOffset(1, 1))

    /** Sheets rise quickly and settle without a visible bounce. */
    fun sheetInOffset(): SpringSpec<IntOffset> =
        spring(dampingRatio = 0.92f, stiffness = 340f, visibilityThreshold = IntOffset(1, 1))

    /** Sheets leave faster than they arrive, with a clean ease-in. */
    fun sheetOutOffset(): TweenSpec<IntOffset> =
        tween(durationMillis = 280, easing = exit)

    fun color(): TweenSpec<Color> =
        tween(durationMillis = 180, easing = easeInOut)

    fun fadeInFloat(): TweenSpec<Float> =
        tween(durationMillis = fadeInMillis, easing = standard)

    fun fadeOutFloat(): TweenSpec<Float> =
        tween(durationMillis = fadeOutMillis, easing = exit)

    fun contentFloat(): TweenSpec<Float> =
        tween(durationMillis = contentMillis, easing = standard)
}
