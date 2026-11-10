package com.dahl404.penny937.paint

import kotlin.math.hypot

/**
 * Soft-falloff displacement used by the morph tool.
 *
 * The point under the brush follows the finger; influence fades to zero at the
 * brush edge with a smoothstep curve, so strokes bend instead of tearing. This is
 * a direct edit of the vector points, not an animation tween.
 */
object Warp {

    /** 1 at the brush centre, 0 at its edge, with a smooth shoulder in between. */
    fun falloff(distance: Float, radius: Float): Float {
        if (radius <= 0f) return 0f
        val t = (distance / radius).coerceIn(0f, 1f)
        val inv = 1f - t
        return inv * inv * (3f - 2f * inv)
    }

    /**
     * Moves the points of one stroke (x, y, pressure triples) that fall inside the
     * brush by ([dx], [dy]) scaled by their distance from ([centerX], [centerY]).
     * Mutates [points] in place and returns true when anything moved.
     */
    fun apply(
        points: FloatArray,
        centerX: Float,
        centerY: Float,
        dx: Float,
        dy: Float,
        radius: Float
    ): Boolean {
        if (radius <= 0f || (dx == 0f && dy == 0f)) return false
        val squaredRadius = radius * radius
        var moved = false
        var i = 0
        while (i < points.size) {
            val px = points[i] - centerX
            val py = points[i + 1] - centerY
            val distanceSquared = px * px + py * py
            if (distanceSquared < squaredRadius) {
                val f = falloff(hypot(px, py), radius)
                if (f > 0f) {
                    points[i] += dx * f
                    points[i + 1] += dy * f
                    moved = true
                }
            }
            i += 3
        }
        return moved
    }
}
