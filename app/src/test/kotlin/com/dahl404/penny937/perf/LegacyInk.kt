package com.dahl404.penny937.perf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import com.dahl404.penny937.paint.Grain
import com.dahl404.penny937.paint.Ink
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Frozen copy of the pre-optimisation stroke renderer (stamped-line geometry).
 * Used only as a pixel reference so the new single-path renderer can be shown to
 * produce the same ink. Do not "fix" this file.
 */
object LegacyInk {

    private fun paintFor(
        tool: Tool,
        color: Int,
        width: Float,
        opacity: Float
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = width
        isFilterBitmap = true
        when (tool) {
            Tool.ERASER -> {
                this.color = Color.BLACK
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                alpha = 255
            }
            Tool.PENCIL -> {
                shader = android.graphics.BitmapShader(
                    Grain.forColor(color),
                    android.graphics.Shader.TileMode.REPEAT,
                    android.graphics.Shader.TileMode.REPEAT
                )
                alpha = (255 * opacity).toInt().coerceIn(0, 255)
            }
            else -> {
                this.color = color
                alpha = (255 * opacity).toInt().coerceIn(0, 255)
            }
        }
    }

    fun renderStroke(canvas: Canvas, stroke: Stroke, opaque: Boolean = false) {
        val n = stroke.pointCount
        if (n == 0) return
        val paint = paintFor(stroke.tool, stroke.color, stroke.width, stroke.opacity)
        if (opaque) paint.alpha = 255

        if (stroke.tool == Tool.MARKER) {
            if (n == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(stroke.pointX(0), stroke.pointY(0), stroke.width * 0.5f, paint)
            } else {
                // Same midpoint-quadratic the app builds, kept local to the reference.
                val path = Path()
                val pts = stroke.points
                val count = stroke.pointCount
                path.moveTo(pts[0], pts[1])
                path.lineTo((pts[0] + pts[3]) * 0.5f, (pts[1] + pts[4]) * 0.5f)
                for (i in 1 until count - 1) {
                    path.quadTo(
                        pts[i * 3], pts[i * 3 + 1],
                        (pts[i * 3] + pts[(i + 1) * 3]) * 0.5f,
                        (pts[i * 3 + 1] + pts[(i + 1) * 3 + 1]) * 0.5f
                    )
                }
                path.lineTo(pts[(count - 1) * 3], pts[(count - 1) * 3 + 1])
                paint.strokeWidth = stroke.width
                canvas.drawPath(path, paint)
            }
            return
        }

        val pts = stroke.points
        fun w(i: Int): Float = Ink.widthFor(stroke.tool, stroke.width, pts[i * 3 + 2], true)

        paint.strokeWidth = w(0)
        val priorStyle = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawCircle(pts[0], pts[1], w(0) * 0.5f, paint)
        paint.style = priorStyle
        if (n == 1) return

        var prevX = pts[0]
        var prevY = pts[1]
        var prevW = w(0)
        var midX = (pts[0] + pts[3]) * 0.5f
        var midY = (pts[1] + pts[4]) * 0.5f
        var midW = (w(0) + w(1)) * 0.5f
        taperedLine(canvas, paint, prevX, prevY, prevW, midX, midY, midW)

        for (i in 1 until n - 1) {
            val cx = pts[i * 3]
            val cy = pts[i * 3 + 1]
            val cw = w(i)
            val nx = (pts[i * 3] + pts[(i + 1) * 3]) * 0.5f
            val ny = (pts[i * 3 + 1] + pts[(i + 1) * 3 + 1]) * 0.5f
            val nw = (w(i) + w(i + 1)) * 0.5f
            quad(canvas, paint, prevX, prevY, prevW, cx, cy, cw, nx, ny, nw)
            prevX = nx; prevY = ny; prevW = nw
            midX = nx; midY = ny; midW = nw
        }

        taperedLine(
            canvas, paint,
            midX, midY, midW,
            pts[(n - 1) * 3], pts[(n - 1) * 3 + 1], w(n - 1)
        )
    }

    private fun taperedLine(
        canvas: Canvas,
        paint: Paint,
        x0: Float, y0: Float, w0: Float,
        x1: Float, y1: Float, w1: Float
    ) {
        val len = hypot(x1 - x0, y1 - y0)
        val step = max(0.8f, min(w0, w1) * 0.12f)
        val steps = (len / step).toInt().coerceIn(1, 48)
        var px = x0
        var py = y0
        for (s in 1..steps) {
            val t = s.toFloat() / steps
            val x = x0 + (x1 - x0) * t
            val y = y0 + (y1 - y0) * t
            paint.strokeWidth = w0 + (w1 - w0) * t
            canvas.drawLine(px, py, x, y, paint)
            px = x
            py = y
        }
    }

    private fun quad(
        canvas: Canvas,
        paint: Paint,
        x0: Float, y0: Float, w0: Float,
        cx: Float, cy: Float, cw: Float,
        x1: Float, y1: Float, w1: Float
    ) {
        val len = hypot(cx - x0, cy - y0) + hypot(x1 - cx, y1 - cy)
        val step = max(0.8f, min(w0, min(cw, w1)) * 0.12f)
        val steps = (len / step).toInt().coerceIn(2, 40)
        var px = x0
        var py = y0
        for (s in 1..steps) {
            val t = s.toFloat() / steps
            val mt = 1f - t
            val x = mt * mt * x0 + 2f * mt * t * cx + t * t * x1
            val y = mt * mt * y0 + 2f * mt * t * cy + t * t * y1
            paint.strokeWidth = w0 + (w1 - w0) * t
            canvas.drawLine(px, py, x, y, paint)
            px = x
            py = y
        }
    }

}
