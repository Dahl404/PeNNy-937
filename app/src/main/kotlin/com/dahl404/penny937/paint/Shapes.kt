package com.dahl404.penny937.paint

import com.dahl404.penny937.model.ShapeKind
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Turns a drag rectangle into vector outline strokes.
 *
 * Rectangles are sampled with tiny points just before/after each corner so the
 * pen's midpoint smoothing keeps the corners crisp instead of ballooning them.
 * Shapes are ordinary strokes, so undo, erase, layers and export all just work.
 */
object Shapes {
    private const val CORNER_EPS = 0.75f
    private const val ELLIPSE_SEGMENTS = 220

    fun build(
        kind: ShapeKind,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        color: Int,
        width: Float
    ): Stroke? {
        return when (kind) {
            ShapeKind.LINE -> {
                if (hypot(x1 - x0, y1 - y0) < 2f) null
                else stroke(
                    floatArrayOf(x0, y0, 1f, x1, y1, 1f),
                    color, width
                )
            }
            ShapeKind.RECTANGLE -> rectangle(x0, y0, x1, y1, color, width)
            ShapeKind.SQUARE -> {
                val dx = x1 - x0
                val dy = y1 - y0
                val side = max(abs(dx), abs(dy))
                val ex = x0 + if (dx < 0f) -side else side
                val ey = y0 + if (dy < 0f) -side else side
                rectangle(x0, y0, ex, ey, color, width)
            }
            ShapeKind.ELLIPSE -> ellipse(x0, y0, x1, y1, color, width)
            ShapeKind.CIRCLE -> {
                val dx = x1 - x0
                val dy = y1 - y0
                val side = max(abs(dx), abs(dy))
                val ex = x0 + if (dx < 0f) -side else side
                val ey = y0 + if (dy < 0f) -side else side
                ellipse(x0, y0, ex, ey, color, width)
            }
        }
    }

    private fun rectangle(
        x0: Float, y0: Float, x1: Float, y1: Float,
        color: Int, width: Float
    ): Stroke? {
        val left = min(x0, x1)
        val right = max(x0, x1)
        val top = min(y0, y1)
        val bottom = max(y0, y1)
        if (right - left < 2f || bottom - top < 2f) return null

        val corners = arrayOf(
            floatArrayOf(left, top),
            floatArrayOf(right, top),
            floatArrayOf(right, bottom),
            floatArrayOf(left, bottom)
        )
        val points = ArrayList<Float>(64)
        fun add(x: Float, y: Float) {
            points.add(x); points.add(y); points.add(1f)
        }
        for (i in 0 until 4) {
            val c = corners[i]
            val prev = corners[(i + 3) % 4]
            val next = corners[(i + 1) % 4]
            val lenPrev = hypot(prev[0] - c[0], prev[1] - c[1])
            val lenNext = hypot(next[0] - c[0], next[1] - c[1])
            val eps = min(CORNER_EPS, min(lenPrev, lenNext) / 4f)
            add(
                c[0] + (prev[0] - c[0]) / lenPrev * eps,
                c[1] + (prev[1] - c[1]) / lenPrev * eps
            )
            add(c[0], c[1])
            add(
                c[0] + (next[0] - c[0]) / lenNext * eps,
                c[1] + (next[1] - c[1]) / lenNext * eps
            )
        }
        add(corners[0][0], corners[0][1])
        return stroke(points.toFloatArray(), color, width)
    }

    private fun ellipse(
        x0: Float, y0: Float, x1: Float, y1: Float,
        color: Int, width: Float
    ): Stroke? {
        val cx = (x0 + x1) * 0.5f
        val cy = (y0 + y1) * 0.5f
        val rx = abs(x1 - x0) * 0.5f
        val ry = abs(y1 - y0) * 0.5f
        if (rx < 1f || ry < 1f) return null
        val points = FloatArray((ELLIPSE_SEGMENTS + 1) * 3)
        for (i in 0..ELLIPSE_SEGMENTS) {
            val a = (i.toFloat() / ELLIPSE_SEGMENTS) * (Math.PI * 2.0).toFloat()
            points[i * 3] = cx + cos(a) * rx
            points[i * 3 + 1] = cy + sin(a) * ry
            points[i * 3 + 2] = 1f
        }
        return stroke(points, color, width)
    }

    private fun stroke(points: FloatArray, color: Int, width: Float): Stroke = Stroke(
        tool = Tool.PEN,
        color = color,
        width = width,
        opacity = 1f,
        points = points
    )
}
