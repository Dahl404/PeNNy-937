package com.dahl404.penny937.ui.canvas

/**
 * Compact persistence for the canvas camera. Floats are stored as raw bits so a
 * saved pan/zoom round-trips exactly instead of depending on decimal formatting.
 */
object CanvasViewportCodec {
    private const val VERSION = 1
    private const val PREFIX = "v$VERSION"

    fun encode(state: CanvasViewport.State): String = buildString {
        append(PREFIX)
        append(':')
        append(java.lang.Float.floatToIntBits(state.scale))
        append(':')
        append(java.lang.Float.floatToIntBits(state.offsetX))
        append(':')
        append(java.lang.Float.floatToIntBits(state.offsetY))
        append(':')
        append(if (state.userZoomed) 1 else 0)
    }

    fun decode(text: String?): CanvasViewport.State? {
        if (text.isNullOrBlank()) return null
        val parts = text.split(':')
        if (parts.size != 5 || parts[0] != PREFIX) return null
        return runCatching {
            val scale = java.lang.Float.intBitsToFloat(parts[1].toInt())
            val offsetX = java.lang.Float.intBitsToFloat(parts[2].toInt())
            val offsetY = java.lang.Float.intBitsToFloat(parts[3].toInt())
            val zoomed = when (parts[4]) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            if (!scale.isFinite() || !offsetX.isFinite() || !offsetY.isFinite()) {
                return null
            }
            CanvasViewport.State(scale, offsetX, offsetY, zoomed)
        }.getOrNull()
    }
}
