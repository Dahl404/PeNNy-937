package com.masen.penny937.paint

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import com.masen.penny937.model.Stroke
import com.masen.penny937.model.Tool
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Graphite texture, generated once per ink colour and tiled along pencil strokes.
 * Two octaves of value noise keep it soft instead of looking like digital static.
 */
object Grain {
    private const val SIZE = 128
    private val cache = object : LinkedHashMap<Int, Bitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>): Boolean =
            size > 6
    }

    @Synchronized
    fun forColor(color: Int): Bitmap = cache.getOrPut(color) { generate(color) }

    private fun generate(color: Int): Bitmap {
        val rnd = Random(color.toLong() * 31 + 7)
        val gridA = FloatArray(10 * 10) { rnd.nextFloat() }
        val gridB = FloatArray(34 * 34) { rnd.nextFloat() }
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val pixels = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val a = noise(gridA, 10, x, y, 32f)
                val bb = noise(gridB, 34, x, y, 6f)
                val n = (a * 0.62f + bb * 0.38f).coerceIn(0f, 1f)
                val alpha = (40 + n * 190).toInt().coerceIn(0, 255)
                pixels[y * SIZE + x] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        }
    }

    private fun noise(grid: FloatArray, n: Int, x: Int, y: Int, cell: Float): Float {
        val fx = x / cell
        val fy = y / cell
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val tx = smooth(fx - x0)
        val ty = smooth(fy - y0)
        val x1 = (x0 + 1) % n
        val y1 = (y0 + 1) % n
        val a = grid[(y0 % n) * n + (x0 % n)]
        val b = grid[(y0 % n) * n + x1]
        val c = grid[y1 * n + (x0 % n)]
        val d = grid[y1 * n + x1]
        return lerp(lerp(a, b, tx), lerp(c, d, tx), ty)
    }

    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}

object Ink {

    fun widthFor(tool: Tool, base: Float, pressure: Float, pressureEnabled: Boolean): Float {
        if (!pressureEnabled || tool == Tool.MARKER) return base
        val p = pressure.coerceIn(0.02f, 1f)
        return when (tool) {
            Tool.ERASER -> base * (0.45f + 0.55f * p)
            else -> base * (0.22f + 0.78f * p)
        }
    }

    fun paintFor(
        tool: Tool,
        color: Int,
        width: Float,
        opacity: Float,
        opaque: Boolean = false
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
                shader = BitmapShader(Grain.forColor(color), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                alpha = (if (opaque) 255 else (255 * opacity).toInt()).coerceIn(0, 255)
            }
            else -> {
                this.color = color
                alpha = (if (opaque) 255 else (255 * opacity).toInt()).coerceIn(0, 255)
            }
        }
    }

    /** Appends a smooth midpoint-quadratic to [path]. Used for constant-width marker ink. */
    fun buildMarkerPath(points: FloatArray, into: Path = Path()): Path {
        into.reset()
        val n = points.size / 3
        if (n == 0) return into
        into.moveTo(points[0], points[1])
        if (n == 1) return into
        if (n == 2) {
            into.lineTo(points[3], points[4])
            return into
        }
        into.lineTo((points[0] + points[3]) * 0.5f, (points[1] + points[4]) * 0.5f)
        for (i in 1 until n - 1) {
            val cx = points[i * 3]
            val cy = points[i * 3 + 1]
            val mx = (points[i * 3] + points[(i + 1) * 3]) * 0.5f
            val my = (points[i * 3 + 1] + points[(i + 1) * 3 + 1]) * 0.5f
            into.quadTo(cx, cy, mx, my)
        }
        into.lineTo(points[(n - 1) * 3], points[(n - 1) * 3 + 1])
        return into
    }

    /** Renders a committed stroke with exactly the same geometry as the live preview. */
    fun renderStroke(canvas: Canvas, stroke: Stroke, opaque: Boolean = false) {
        val n = stroke.pointCount
        if (n == 0) return
        val paint = paintFor(stroke.tool, stroke.color, stroke.width, stroke.opacity, opaque)

        if (stroke.tool == Tool.MARKER) {
            if (n == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(stroke.pointX(0), stroke.pointY(0), stroke.width * 0.5f, paint)
            } else {
                val path = buildMarkerPath(stroke.points)
                paint.strokeWidth = stroke.width
                canvas.drawPath(path, paint)
            }
            return
        }

        val pts = stroke.points
        val pressureEnabled = true
        fun w(i: Int): Float =
            widthFor(stroke.tool, stroke.width, pts[i * 3 + 2], pressureEnabled)

        // Start cap.
        paint.strokeWidth = w(0)
        val priorStyle = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawCircle(pts[0], pts[1], w(0) * 0.5f, paint)
        paint.style = priorStyle
        if (n == 1) return

        // First half segment into the first midpoint.
        var prevX = pts[0]
        var prevY = pts[1]
        var prevW = w(0)
        var midX = (pts[0] + pts[3]) * 0.5f
        var midY = (pts[1] + pts[4]) * 0.5f
        var midW = (w(0) + w(1)) * 0.5f
        drawTaperedLine(canvas, paint, prevX, prevY, prevW, midX, midY, midW)

        // Smooth quadratic quads through each interior point.
        for (i in 1 until n - 1) {
            val cx = pts[i * 3]
            val cy = pts[i * 3 + 1]
            val cw = w(i)
            val nx = (pts[i * 3] + pts[(i + 1) * 3]) * 0.5f
            val ny = (pts[i * 3 + 1] + pts[(i + 1) * 3 + 1]) * 0.5f
            val nw = (w(i) + w(i + 1)) * 0.5f
            drawQuad(canvas, paint, prevX, prevY, prevW, cx, cy, cw, nx, ny, nw)
            prevX = nx; prevY = ny; prevW = nw
            midX = nx; midY = ny; midW = nw
        }

        // Tail.
        drawTaperedLine(
            canvas, paint,
            midX, midY, midW,
            pts[(n - 1) * 3], pts[(n - 1) * 3 + 1], w(n - 1)
        )
    }

    private fun drawTaperedLine(
        canvas: Canvas,
        paint: Paint,
        x0: Float, y0: Float, w0: Float,
        x1: Float, y1: Float, w1: Float
    ) {
        val len = kotlin.math.hypot(x1 - x0, y1 - y0)
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

    private fun drawQuad(
        canvas: Canvas,
        paint: Paint,
        x0: Float, y0: Float, w0: Float,
        cx: Float, cy: Float, cw: Float,
        x1: Float, y1: Float, w1: Float
    ) {
        val len = kotlin.math.hypot(cx - x0, cy - y0) + kotlin.math.hypot(x1 - cx, y1 - cy)
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

/**
 * Smooths raw stylus samples and draws them incrementally into a bitmap.
 * The geometry here is shared with [Ink.renderStroke], so a committed stroke is
 * pixel-identical to the stroke that was under the pen.
 */
class StrokeInker(
    val tool: Tool,
    val color: Int,
    val baseWidth: Float,
    val opacity: Float,
    private val pressureEnabled: Boolean
) {
    private val points = ArrayList<Float>(2048)
    private val paint = Ink.paintFor(tool, color, baseWidth, opacity, opaque = tool.usesOverlay)
    private var smoothX = 0f
    private var smoothY = 0f
    private var smoothP = 1f
    private var hasPoint = false
    private var quadsDrawn = 0
    private var startDrawn = false
    private var tailDrawn = false
    private var markerPath = Path()

    val isEmpty: Boolean get() = points.isEmpty()
    val pointCount: Int get() = points.size / 3

    fun addRaw(x: Float, y: Float, rawPressure: Float): Boolean {
        val pressure = (if (rawPressure <= 0f) 1f else rawPressure).coerceIn(0.01f, 1f)
        if (!hasPoint) {
            smoothX = x
            smoothY = y
            smoothP = pressure
            hasPoint = true
            points.add(x); points.add(y); points.add(if (pressureEnabled) pressure else 1f)
            return true
        }
        val dx = x - smoothX
        val dy = y - smoothY
        val dist2 = dx * dx + dy * dy
        if (dist2 < 0.16f && abs(pressure - smoothP) < 0.02f) return false
        val k = 0.58f
        smoothX += dx * k
        smoothY += dy * k
        smoothP += (pressure - smoothP) * 0.42f
        points.add(smoothX); points.add(smoothY)
        points.add(if (pressureEnabled) smoothP else 1f)
        return true
    }

    /** Draws only the geometry that became available since the last call. */
    fun drawNew(canvas: Canvas) {
        val n = pointCount
        if (n == 0 || tailDrawn) return

        if (tool == Tool.MARKER) {
            markerPath = Ink.buildMarkerPath(points.toFloatArray())
            if (n == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(points[0], points[1], baseWidth * 0.5f, paint)
                paint.style = Paint.Style.STROKE
            } else {
                paint.strokeWidth = baseWidth
                canvas.drawPath(markerPath, paint)
            }
            return
        }

        paint.strokeWidth = widthAt(0)
        if (!startDrawn) {
            val prior = paint.style
            paint.style = Paint.Style.FILL
            canvas.drawCircle(points[0], points[1], widthAt(0) * 0.5f, paint)
            paint.style = prior
            if (n >= 2) {
                val mx = (points[0] + points[3]) * 0.5f
                val my = (points[1] + points[4]) * 0.5f
                drawTapered(
                    canvas, points[0], points[1], widthAt(0),
                    mx, my, (widthAt(0) + widthAt(1)) * 0.5f
                )
                startDrawn = true
            } else {
                return
            }
        }

        while (quadsDrawn < n - 2) {
            val i = quadsDrawn + 1
            val cx = points[i * 3]
            val cy = points[i * 3 + 1]
            val cw = widthAt(i)
            val nx = (points[i * 3] + points[(i + 1) * 3]) * 0.5f
            val ny = (points[i * 3 + 1] + points[(i + 1) * 3 + 1]) * 0.5f
            val nw = (widthAt(i) + widthAt(i + 1)) * 0.5f
            drawQuad(
                canvas,
                midX(i - 1), midY(i - 1), midW(i - 1),
                cx, cy, cw,
                nx, ny, nw
            )
            quadsDrawn++
        }
    }

    fun finish(canvas: Canvas) {
        val n = pointCount
        if (n == 0) return
        if (tool == Tool.MARKER) {
            drawNew(canvas)
            tailDrawn = true
            return
        }
        if (!startDrawn) drawNew(canvas)
        if (n >= 2 && !tailDrawn) {
            val i = n - 1
            paint.strokeWidth = widthAt(i)
            drawTapered(
                canvas,
                midX(i - 1), midY(i - 1), midW(i - 1),
                points[i * 3], points[i * 3 + 1], widthAt(i)
            )
            val priorStyle = paint.style
            paint.style = Paint.Style.FILL
            canvas.drawCircle(points[i * 3], points[i * 3 + 1], widthAt(i) * 0.5f, paint)
            paint.style = priorStyle
        }
        tailDrawn = true
    }

    fun snapshot(): Stroke = Stroke(
        tool = tool,
        color = color,
        width = baseWidth,
        opacity = opacity,
        points = points.toFloatArray()
    )

    fun markerPath(): Path? = if (tool == Tool.MARKER && pointCount > 0) markerPath else null

    private fun widthAt(i: Int): Float =
        Ink.widthFor(tool, baseWidth, points[i * 3 + 2], pressureEnabled)

    private fun midX(i: Int): Float = (points[i * 3] + points[(i + 1) * 3]) * 0.5f
    private fun midY(i: Int): Float = (points[i * 3 + 1] + points[(i + 1) * 3 + 1]) * 0.5f
    private fun midW(i: Int): Float = (widthAt(i) + widthAt(i + 1)) * 0.5f

    private fun drawTapered(
        canvas: Canvas, x0: Float, y0: Float, w0: Float, x1: Float, y1: Float, w1: Float
    ) {
        val len = kotlin.math.hypot(x1 - x0, y1 - y0)
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
            px = x; py = y
        }
    }

    private fun drawQuad(
        canvas: Canvas,
        x0: Float, y0: Float, w0: Float,
        cx: Float, cy: Float, cw: Float,
        x1: Float, y1: Float, w1: Float
    ) {
        val len = kotlin.math.hypot(cx - x0, cy - y0) + kotlin.math.hypot(x1 - cx, y1 - cy)
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
            px = x; py = y
        }
    }
}
