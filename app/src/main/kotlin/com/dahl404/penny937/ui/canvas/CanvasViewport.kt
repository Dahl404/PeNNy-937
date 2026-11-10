package com.dahl404.penny937.ui.canvas

import android.animation.ValueAnimator
import android.view.animation.PathInterpolator
import kotlin.math.max
import kotlin.math.min

/**
 * Owns the canvas camera: fit, pan, pinch zoom, double-tap zoom and the offset
 * clamping rules. Keeping this out of [DrawingCanvasView] means the view is left
 * with input routing and painting rather than camera arithmetic.
 */
class CanvasViewport(
    private val viewWidth: () -> Int,
    private val viewHeight: () -> Int,
    private val fitPaddingPx: () -> Float,
    private val edgeMarginPx: () -> Float,
    private val onChange: () -> Unit
) {
    data class State(
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
        val userZoomed: Boolean
    )

    var projectWidth: Int = 1
        private set
    var projectHeight: Int = 1
        private set

    var scale: Float = 1f
    var offsetX: Float = 0f
    var offsetY: Float = 0f
    var fitScale: Float = 1f
        private set
    var userZoomed: Boolean = false

    var animator: ValueAnimator? = null
        private set

    private var seenWidth = -1
    private var seenHeight = -1

    fun updateProject(width: Int, height: Int) {
        projectWidth = width.coerceAtLeast(1)
        projectHeight = height.coerceAtLeast(1)
    }

    fun fitToPaper(animate: Boolean = false) {
        val newScale = computeFitScale() ?: return
        fitScale = newScale
        val w = viewWidth()
        val h = viewHeight()
        val tx = (w - projectWidth * newScale) / 2f
        val ty = (h - projectHeight * newScale) / 2f
        userZoomed = false
        if (animate) {
            animateTo(newScale, tx, ty)
        } else {
            cancelAnimation()
            scale = newScale
            offsetX = tx
            offsetY = ty
            clampOffsets()
            onChange()
        }
    }

    fun computeFitScale(): Float? {
        val w = viewWidth()
        val h = viewHeight()
        if (w == 0 || h == 0) return null
        val padding = fitPaddingPx()
        val availableW = (w - padding * 2f).coerceAtLeast(1f)
        val availableH = (h - padding * 2f).coerceAtLeast(1f)
        return min(availableW / projectWidth, availableH / projectHeight).coerceAtLeast(0.001f)
    }

    fun currentScale(): Float = scale

    fun snapshot(): State = State(scale, offsetX, offsetY, userZoomed)

    /** Restores a saved camera after the view and project dimensions are known. */
    fun restore(state: State) {
        fitScale = computeFitScale() ?: fitScale
        val maxScale = max(2f, fitScale * 12f)
        scale = state.scale.coerceIn(0.02f, maxScale)
        offsetX = state.offsetX
        offsetY = state.offsetY
        userZoomed = state.userZoomed
        clampOffsets()
        onChange()
    }

    fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w != seenWidth || h != seenHeight) {
            seenWidth = w
            seenHeight = h
            if (!userZoomed) fitToPaper() else clampOffsets()
        }
    }

    fun cancelAnimation() {
        animator?.cancel()
        animator = null
    }

    fun toggleZoom(x: Float, y: Float) {
        if (scale > fitScale * 1.15f) {
            fitToPaper(animate = true)
        } else {
            val targetScale = (fitScale * 2.4f).coerceAtMost(max(2f, fitScale * 12f))
            val wx = (x - offsetX) / scale
            val wy = (y - offsetY) / scale
            animateTo(targetScale, x - wx * targetScale, y - wy * targetScale)
            userZoomed = true
        }
    }

    fun panBy(dx: Float, dy: Float) {
        offsetX += dx
        offsetY += dy
        clampOffsets()
        onChange()
    }

    fun zoomTo(newScale: Float, focusX: Float, focusY: Float, worldX: Float, worldY: Float) {
        scale = newScale
        offsetX = focusX - worldX * newScale
        offsetY = focusY - worldY * newScale
        userZoomed = true
        clampOffsets()
        onChange()
    }

    fun animateTo(targetScale: Float, targetX: Float, targetY: Float) {
        cancelAnimation()
        val startScale = scale
        val startX = offsetX
        val startY = offsetY
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            // Same soft deceleration as the Compose motion system.
            interpolator = PathInterpolator(0.22f, 0.61f, 0.36f, 1f)
            addUpdateListener { animation ->
                val t = animation.animatedValue as Float
                scale = startScale + (targetScale - startScale) * t
                offsetX = startX + (targetX - startX) * t
                offsetY = startY + (targetY - startY) * t
                clampOffsets()
                onChange()
            }
            start()
        }
    }

    fun clampOffsets() {
        val paperW = projectWidth * scale
        val paperH = projectHeight * scale
        val margin = edgeMarginPx()
        val marginX = min(margin, paperW * 0.8f)
        val marginY = min(margin, paperH * 0.8f)
        offsetX = offsetX.coerceIn(-paperW + marginX, viewWidth() - marginX)
        offsetY = offsetY.coerceIn(-paperH + marginY, viewHeight() - marginY)
        if (paperW < viewWidth()) {
            offsetX = (viewWidth() - paperW) / 2f
        }
        if (paperH < viewHeight()) {
            offsetY = (viewHeight() - paperH) / 2f
        }
    }
}
