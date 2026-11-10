package com.dahl404.penny937.ui.theme

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {

    /**
     * ColorCircle animates its selection inset from 3.dp down to 0.dp. A bouncy
     * spring undershot that to roughly -0.005.dp, and `Modifier.padding` throws on
     * any negative value — which crashed the color sheet as soon as a second
     * swatch was picked. The settle spring must never cross zero.
     */
    @Test
    fun selectionSettleNeverUndershootsZero() {
        val spec = PennyMotion.settleDp().vectorize(Dp.VectorConverter)
        val initial = AnimationVector1D(3f)
        val target = AnimationVector1D(0f)
        val velocity = AnimationVector1D(0f)
        val duration = spec.getDurationNanos(initial, target, velocity)
        var time = 0L
        while (time <= duration) {
            val value = spec.getValueFromNanos(time, initial, target, velocity).value
            assertTrue("settleDp overshot below zero: $value at ${time / 1_000_000} ms", value >= 0f)
            time += 1_000_000L
        }
    }
}
