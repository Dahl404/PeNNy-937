package com.dahl404.penny937.export

/**
 * Keeps export fan-out inside a bounded share of the heap.
 *
 * Rendering a frame holds several full-size buffers (the bitmap, a pixel array,
 * quantiser scratch and the encoded bytes). The old fixed `parallelism * 2` batch
 * was fine for small canvases and an easy way to run out of memory on a 4096px one.
 * These helpers are pure arithmetic so the policy can be tested on the JVM.
 */
object ExportMemory {
    private const val MIB = 1024L * 1024L
    private const val MIN_BUDGET = 24L * MIB

    /** A stable estimate for the peak bytes one GIF frame can hold in a batch. */
    fun gifFrameBytes(width: Int, height: Int): Long =
        width.toLong() * height.toLong() * 10L + MIB

    /** PNG keeps the bitmap plus a compressed copy of the frame. */
    fun pngFrameBytes(width: Int, height: Int): Long =
        width.toLong() * height.toLong() * 8L + MIB

    /** MP4 keeps ARGB pixels in the pipeline and a bitmap while rendering. */
    fun videoFrameBytes(width: Int, height: Int): Long =
        width.toLong() * height.toLong() * 8L + MIB

    /**
     * Budget for one export batch, capped by a quarter of the heap and half of the
     * currently free heap so other UI caches still have room.
     */
    fun budgetBytes(maxMemory: Long, usedMemory: Long): Long {
        if (maxMemory <= 0L) return MIN_BUDGET
        val free = (maxMemory - usedMemory).coerceAtLeast(0L)
        val cap = (maxMemory / 2L).coerceAtLeast(1L)
        // Never reserve more than the heap that is actually free, and never less
        // than one frame's worth of room for the caller to make progress.
        val minimum = minOf(MIN_BUDGET, maxMemory / 8L, free).coerceAtLeast(1L)
        val target = minOf(maxMemory / 4L, free / 2L)
        return target.coerceIn(minimum, cap)
    }

    fun runtimeBudget(): Long {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        return budgetBytes(runtime.maxMemory(), used)
    }

    /** At least one frame, at most [desired], bounded by [budgetBytes]. */
    fun framesPerBatch(bytesPerFrame: Long, desired: Int, budgetBytes: Long): Int {
        val wanted = desired.coerceAtLeast(1)
        if (bytesPerFrame <= 0L) return wanted
        val fits = (budgetBytes / bytesPerFrame).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return fits.coerceIn(1, wanted)
    }
}
