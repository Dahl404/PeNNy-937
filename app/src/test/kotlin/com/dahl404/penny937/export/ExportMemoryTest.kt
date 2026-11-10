package com.dahl404.penny937.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportMemoryTest {

    private val mib = 1024L * 1024L

    @Test
    fun budgetUsesAFractionOfTheHeapAndStaysWithinFreeMemory() {
        val max = 512L * mib
        assertEquals(128L * mib, ExportMemory.budgetBytes(max, usedMemory = 100L * mib))
        assertEquals(128L * mib, ExportMemory.budgetBytes(max, usedMemory = 0L))
        assertEquals(106L * mib, ExportMemory.budgetBytes(max, usedMemory = 300L * mib))
        // When almost nothing is free, the budget shrinks with it.
        assertEquals(12L * mib, ExportMemory.budgetBytes(max, usedMemory = 500L * mib))
        assertTrue(ExportMemory.budgetBytes(max, usedMemory = max) >= 1L)
    }

    @Test
    fun budgetStaysSaneOnTinyHeaps() {
        val max = 32L * mib
        val budget = ExportMemory.budgetBytes(max, usedMemory = 4L * mib)
        assertTrue(budget in 1L..max / 2L)
    }

    @Test
    fun framesPerBatchFillsTheBudgetButNeverExceedsTheRequestedFanOut() {
        val desired = 12
        assertEquals(12, ExportMemory.framesPerBatch(10 * mib, desired, 128 * mib))
        assertEquals(2, ExportMemory.framesPerBatch(50 * mib, desired, 128 * mib))
        assertEquals(1, ExportMemory.framesPerBatch(200 * mib, desired, 128 * mib))
        assertEquals(1, ExportMemory.framesPerBatch(1, 1, 128 * mib))
        assertEquals(12, ExportMemory.framesPerBatch(0, desired, 128 * mib))
    }

    @Test
    fun frameEstimatesScaleWithPixels() {
        assertTrue(ExportMemory.pngFrameBytes(4096, 4096) > ExportMemory.pngFrameBytes(1080, 1920))
        assertTrue(ExportMemory.gifFrameBytes(720, 1280) > ExportMemory.gifFrameBytes(480, 854))
        assertTrue(ExportMemory.videoFrameBytes(1920, 1080) >= 8L * 1920 * 1080)
    }

    @Test
    fun runtimeBudgetIsPositive() {
        assertTrue(ExportMemory.runtimeBudget() > 0)
    }
}
