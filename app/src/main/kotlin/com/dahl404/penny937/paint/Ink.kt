package com.dahl404.penny937.paint

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
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

/** Growable primitive float list — avoids boxing on the per-stroke hot path. */
internal class Floats(capacity: Int = 64) {
    var data = FloatArray(if (capacity < 8) 8 else capacity)
        private set
    var size = 0
        private set

    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }

    fun clear() {
        size = 0
    }
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

    /** Opaque mask paint used to buffer an eraser stroke in the scratch bitmap. */
    fun maskPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        alpha = 255
    }

    /** Paint that layers an opaque scratch stroke at its ink opacity. Used while drawing. */
    fun compositePaintFor(tool: Tool, opacity: Float): Paint =
        Paint(Paint.FILTER_BITMAP_FLAG).apply {
            if (tool == Tool.ERASER) {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                alpha = 255
            } else {
                alpha = (255 * opacity).toInt().coerceIn(0, 255)
            }
        }

    /** Renders a committed stroke with exactly the same geometry as the live preview. */
    fun renderStroke(canvas: Canvas, stroke: Stroke) {
        val n = stroke.pointCount
        if (n == 0) return
        val paint = paintFor(stroke.tool, stroke.color, stroke.width, stroke.opacity)

        if (n == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(stroke.pointX(0), stroke.pointY(0), radiusAt(stroke, 0), paint)
            return
        }
        val outline = buildOutline(stroke) ?: return
        paint.style = Paint.Style.FILL
        repeat(passesFor(stroke.tool)) { canvas.drawPath(outline, paint) }
    }

    /**
     * Pencil grain is a per-pixel alpha texture. Stamping a stroke as overlapping
     * discs used to accumulate that texture several times over, which is where the
     * graphite density came from; a single fill would look washed out. Re-blending
     * the same tiled shader a fixed number of times restores the density without
     * bringing back the per-segment double blending.
     */
    fun passesFor(tool: Tool): Int = if (tool == Tool.PENCIL) 3 else 1

    private fun radiusAt(stroke: Stroke, i: Int): Float =
        widthFor(stroke.tool, stroke.width, stroke.pointPressure(i), true) * 0.5f

    /**
     * Builds the whole stroke as ONE closed, filled ribbon outline.
     *
     * The old renderer stamped a stroke as hundreds of `drawLine` calls with a
     * slightly different width each: correct, but it spent more time in the Skia
     * dispatch than in actual rasterisation (a 104 px thumbnail of 90 strokes cost
     * 46 ms). Offsetting the same centreline into a left/right boundary pair and
     * filling it once collapses that to a single path per stroke, and it also stops
     * translucent ink from double-darkening where consecutive segments overlap.
     */
    fun buildOutline(stroke: Stroke, into: Path = Path()): Path? =
        buildOutlineRange(
            points = stroke.points,
            count = stroke.pointCount,
            tool = stroke.tool,
            width = stroke.width,
            from = 0,
            into = into
        )

    /**
     * Builds the ribbon for samples [from] .. [count]-1 of an in-progress stroke.
     *
     * Each piece of the centreline depends only on its own neighbours, so a range
     * sampled here is bit-identical to the same range of the finished stroke. That is
     * what lets the live preview grow a few samples at a time and still land on
     * exactly the geometry that will be committed.
     */
    fun buildOutlineRange(
        points: FloatArray,
        count: Int,
        tool: Tool,
        width: Float,
        from: Int,
        into: Path = Path()
    ): Path? {
        if (count == 0) return null
        into.reset()
        if (count == 1) {
            into.addCircle(
                points[0], points[1],
                widthFor(tool, width, points[2], true) * 0.5f,
                Path.Direction.CW
            )
            return into
        }
        val xs = Floats((count - from) * 4)
        val ys = Floats((count - from) * 4)
        val rs = Floats((count - from) * 4)
        sampleCenterline(points, count, tool, width, from, xs, ys, rs)
        emitPolygon(xs, ys, rs, into)
        return into
    }

    /**
     * Samples the midpoint-quadratic centreline (the geometry the stamped renderer
     * drew) into flat x/y/radius lists, starting at sample [from].
     */
    private fun sampleCenterline(
        pts: FloatArray,
        n: Int,
        tool: Tool,
        width: Float,
        from: Int,
        xs: Floats,
        ys: Floats,
        rs: Floats
    ) {
        // Marker ink has a constant width; nothing to interpolate.
        val constant = tool == Tool.MARKER
        fun radius(i: Int): Float =
            if (constant) width * 0.5f
            else widthFor(tool, width, pts[i * 3 + 2], true) * 0.5f

        fun emit(x: Float, y: Float, r: Float) {
            xs.add(x); ys.add(y); rs.add(r)
        }

        // A range starts on the piece boundary the previous range ended on, so the
        // overlapping tips coincide instead of forming a backward spur.
        if (from == 0) emit(pts[0], pts[1], radius(0))
        if (n - from == 1) {
            if (from == n - 1) emit(pts[(n - 1) * 3], pts[(n - 1) * 3 + 1], radius(n - 1))
            limitRadiusRate(xs, ys, rs)
            return
        }
        var j = if (from < 1) 1 else from
        var first = true
        while (j <= n - 2) {
            val ax: Float
            val ay: Float
            val ar: Float
            if (j == 1) {
                ax = pts[0]; ay = pts[1]; ar = radius(0)
            } else {
                ax = (pts[(j - 1) * 3] + pts[j * 3]) * 0.5f
                ay = (pts[(j - 1) * 3 + 1] + pts[j * 3 + 1]) * 0.5f
                ar = (radius(j - 1) + radius(j)) * 0.5f
            }
            if (first) {
                if (from > 0) emit(ax, ay, ar)
                first = false
            }
            val cx = pts[j * 3]
            val cy = pts[j * 3 + 1]
            val cr = radius(j)
            val ex = (pts[j * 3] + pts[(j + 1) * 3]) * 0.5f
            val ey = (pts[j * 3 + 1] + pts[(j + 1) * 3 + 1]) * 0.5f
            val er = (radius(j) + radius(j + 1)) * 0.5f
            sampleQuad(xs, ys, rs, ax, ay, ar, cx, cy, cr, ex, ey, er)
            j++
        }
        // Tail: last midpoint to the final sample.
        emit(pts[(n - 1) * 3], pts[(n - 1) * 3 + 1], radius(n - 1))
        limitRadiusRate(xs, ys, rs)
    }

    /**
     * Caps how fast the width can change per unit of travel.
     *
     * A ribbon tolerates a moving width, not a strobing one: raw pressure can jump
     * tens of percent between two samples a couple of pixels apart, which turns the
     * offset boundary into a sawtooth. Limiting the rate keeps fast pressure changes
     * smooth (the old stamping renderer hid them behind overlapping discs).
     */
    private fun limitRadiusRate(xs: Floats, ys: Floats, rs: Floats) {
        val k = rs.size
        if (k < 3) return
        val x = xs.data
        val y = ys.data
        val r = rs.data
        // Forward-only on purpose: the live stroke is drawn in ranges, and a pass
        // that only looks backwards gives every range the same profile as the whole.
        for (i in 1 until k) {
            val d = hypot(x[i] - x[i - 1], y[i] - y[i - 1])
            val maxDelta = 0.75f * d + 0.25f
            val lo = r[i - 1] - maxDelta
            val hi = r[i - 1] + maxDelta
            if (r[i] < lo) r[i] = lo else if (r[i] > hi) r[i] = hi
        }
    }

    private fun sampleQuad(
        xs: Floats,
        ys: Floats,
        rs: Floats,
        x0: Float, y0: Float, r0: Float,
        cx: Float, cy: Float, cr: Float,
        x1: Float, y1: Float, r1: Float
    ) {
        val len = hypot(cx - x0, cy - y0) + hypot(x1 - cx, y1 - cy)
        // Matches the sampling density the old stamped renderer used, so both
        // renderers walk the same centreline polyline.
        val step = max(0.8f, min(r0, min(cr, r1)) * 0.24f)
        val steps = (len / step).toInt().coerceIn(2, 40)
        for (s in 1 until steps) {
            val t = s.toFloat() / steps
            val mt = 1f - t
            val w = 2f * mt * t
            xs.add(mt * mt * x0 + w * cx + t * t * x1)
            ys.add(mt * mt * y0 + w * cy + t * t * y1)
            rs.add(r0 + (r1 - r0) * t)
        }
        xs.add(x1)
        ys.add(y1)
        rs.add(r1)
    }

    private const val CAP_STEPS = 5
    private const val ARC_STEP = 0.4f
    private const val MAX_ARC_POINTS = 8

    private fun len(x: Float, y: Float): Float = sqrt(x * x + y * y)

    /**
     * Turns the sampled centreline into a closed round-capped ribbon.
     *
     * Each segment contributes its own offset pair (so a width change between two
     * samples is a short bevel, never a spike), and vertices that turn by more than
     * [ARC_STEP] get an arc on their outer side, which is the round join the stamped
     * renderer produced. Directions and angles are resolved once per segment and the
     * centreline is walked twice, so no boundary arrays are allocated.
     */
    private fun emitPolygon(xs: Floats, ys: Floats, rs: Floats, into: Path) {
        val k = xs.size
        if (k < 2) return
        val px = xs.data
        val py = ys.data
        val pr = rs.data
        val segs = k - 1

        val dirX = FloatArray(segs)
        val dirY = FloatArray(segs)
        val ang = FloatArray(segs)
        for (j in 0 until segs) {
            val dx = px[j + 1] - px[j]
            val dy = py[j + 1] - py[j]
            val l = len(dx, dy)
            if (l > 1e-5f) {
                dirX[j] = dx / l
                dirY[j] = dy / l
                ang[j] = atan2(dx, -dy)
            } else {
                dirX[j] = if (j > 0) dirX[j - 1] else 1f
                dirY[j] = if (j > 0) dirY[j - 1] else 0f
                ang[j] = atan2(dirX[j], -dirY[j])
            }
        }

        // Forward: left boundary.
        into.moveTo(px[0] - dirY[0] * pr[0], py[0] + dirX[0] * pr[0])
        for (i in 1 until k) {
            val j = i - 1
            val inX = dirX[j]
            val inY = dirY[j]
            val r = pr[i]
            into.lineTo(px[i] - inY * r, py[i] + inX * r)
            if (i < k - 1) {
                val outX = dirX[j + 1]
                val outY = dirY[j + 1]
                if (inX * outY - inY * outX < 0f) {
                    val turn = abs(wrap(ang[j + 1] - ang[j]))
                    if (turn > ARC_STEP) {
                        // Turning clockwise: the left side is the outer side.
                        val steps = ((turn / ARC_STEP).toInt() + 1).coerceAtMost(MAX_ARC_POINTS)
                        val a0 = ang[j]
                        val sweep = wrap(ang[j + 1] - a0)
                        for (s in 1 until steps) {
                            val a = a0 + sweep * s / steps
                            into.lineTo(px[i] + cos(a) * r, py[i] + sin(a) * r)
                        }
                    }
                }
                into.lineTo(px[i] - outY * r, py[i] + outX * r)
            }
        }

        // End cap: semicircle from left to right around the last sample.
        val endR = pr[k - 1]
        val endAngle = ang[segs - 1]
        for (s in 1..CAP_STEPS) {
            val a = endAngle - Math.PI.toFloat() * s / CAP_STEPS
            into.lineTo(px[k - 1] + cos(a) * endR, py[k - 1] + sin(a) * endR)
        }

        // Backward: right boundary.
        for (i in k - 2 downTo 0) {
            val j = i
            val inX = dirX[j]
            val inY = dirY[j]
            val r = pr[i]
            into.lineTo(px[i] + inY * r, py[i] - inX * r)
            if (j > 0) {
                val outX = dirX[j - 1]
                val outY = dirY[j - 1]
                if (outX * inY - outY * inX > 0f) {
                    val turn = abs(wrap(ang[j] - ang[j - 1]))
                    if (turn > ARC_STEP) {
                        // Turning anticlockwise: the right side is the outer side.
                        val steps = ((turn / ARC_STEP).toInt() + 1).coerceAtMost(MAX_ARC_POINTS)
                        val a0 = ang[j] + Math.PI.toFloat()
                        val sweep = wrap(ang[j - 1] - ang[j])
                        for (s in 1 until steps) {
                            val a = a0 + sweep * s / steps
                            into.lineTo(px[i] + cos(a) * r, py[i] + sin(a) * r)
                        }
                    }
                }
                into.lineTo(px[i] + outY * r, py[i] - outX * r)
            }
        }

        // Start cap: semicircle from right back around to the left point.
        val s0 = ang[0] + Math.PI.toFloat()
        for (s in 1..CAP_STEPS) {
            val a = s0 - Math.PI.toFloat() * s / CAP_STEPS
            into.lineTo(px[0] + cos(a) * pr[0], py[0] + sin(a) * pr[0])
        }
        into.close()
    }

    private fun wrap(a: Float): Float {
        var v = a
        while (v > Math.PI) v -= (Math.PI * 2).toFloat()
        while (v < -Math.PI) v += (Math.PI * 2).toFloat()
        return v
    }

    /** Radius bound used to pad dirty rectangles around a stroke. */
    fun maxRadius(tool: Tool, width: Float): Float = width * 0.5f + 2f
}

/**
 * Smooths raw stylus samples and draws them incrementally.
 *
 * The live stroke is drawn into a transparent scratch bitmap (see
 * [com.dahl404.penny937.paint.FrameRenderer.inkOverlay]) as an opaque ribbon and is
 * blended into the layer exactly once, on lift. The preview is therefore identical
 * to the committed stroke instead of accumulating darker ink at every joint.
 */
class StrokeInker(
    val tool: Tool,
    val color: Int,
    val baseWidth: Float,
    val opacity: Float,
    private val pressureEnabled: Boolean
) {
    private val points = Floats(2048)
    private val outline = Path()

    private var smoothX = 0f
    private var smoothY = 0f
    private var smoothP = 1f
    private var hasPoint = false
    private val dirty = android.graphics.RectF()

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

    /**
     * Redraws the stroke into the scratch bitmap.
     *
     * The scratch bitmap only ever holds this one stroke, so the cheapest correct
     * thing is to clear the stroke's box and lay the whole ribbon down again: one
     * path, one fill, no seams, and self-crossings are exact. Growing the preview by
     * appending instead would either blend the joins twice or need the bitmap read
     * back, both of which show up as beads of darker ink along a translucent stroke.
     */
    fun drawNew(canvas: Canvas) {
        val n = pointCount
        if (n == 0) return
        bounds(dirty)
        canvas.save()
        canvas.clipRect(dirty)
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        // The eraser buffers a mask, not ink: the cut-out happens when the overlay is
        // composited into the layer.
        val paint = if (tool == Tool.ERASER) Ink.maskPaint().also { it.isAntiAlias = true }
        else Ink.paintFor(tool, color, baseWidth, 1f, opaque = true).also {
            it.style = Paint.Style.FILL
        }
        val path = Ink.buildOutlineRange(points.data, n, tool, baseWidth, 0, outline) ?: run {
            canvas.restore()
            return
        }
        repeat(Ink.passesFor(tool)) { canvas.drawPath(path, paint) }
        canvas.restore()
    }

    /** Bounding box of the stroke so far, padded by its widest radius. */
    fun bounds(out: android.graphics.RectF) {
        val n = pointCount
        if (n == 0) {
            out.setEmpty()
            return
        }
        val data = points.data
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        var i = 0
        while (i < n) {
            val x = data[i * 3]
            val y = data[i * 3 + 1]
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
            i++
        }
        val pad = Ink.maxRadius(tool, baseWidth)
        out.set(left - pad, top - pad, right + pad, bottom + pad)
    }

    fun finish(canvas: Canvas) {
        drawNew(canvas)
    }

    fun snapshot(): Stroke = Stroke(
        tool = tool,
        color = color,
        width = baseWidth,
        opacity = opacity,
        points = points.data.copyOf(points.size)
    )
}
