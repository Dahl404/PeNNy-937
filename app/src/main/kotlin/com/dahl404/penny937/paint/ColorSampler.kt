package com.dahl404.penny937.paint

/**
 * Picks the colour nearest to a point from a small patch of an ARGB layer bitmap.
 * Fully/nearly transparent pixels are skipped, so tapping just beside a thin
 * stroke still finds its ink instead of returning nothing.
 */
object ColorSampler {

    private const val MIN_ALPHA = 8

    /**
     * @param pixels row-major ARGB pixels of the sampled patch
     * @param width patch width
     * @param height patch height
     * @param sampleX sample point in patch coordinates
     * @param sampleY sample point in patch coordinates
     * @return the nearest visible colour with a forced opaque alpha, or null
     */
    fun nearestOpaqueColor(
        pixels: IntArray,
        width: Int,
        height: Int,
        sampleX: Int,
        sampleY: Int
    ): Int? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        var best: Int? = null
        var bestDistance = Int.MAX_VALUE
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val pixel = pixels[row + x]
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < MIN_ALPHA) continue
                val dx = x - sampleX
                val dy = y - sampleY
                val distance = dx * dx + dy * dy
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = pixel
                }
            }
        }
        return best?.let { (it and 0x00FFFFFF) or 0xFF000000.toInt() }
    }
}
