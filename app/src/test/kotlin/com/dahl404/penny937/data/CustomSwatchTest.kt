package com.dahl404.penny937.data

import android.graphics.Color
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class CustomSwatchTest {

    private fun prefs() = Prefs(RuntimeEnvironment.getApplication())

    @After
    fun tearDown() {
        for (index in 0 until Prefs.CUSTOM_SWATCH_COUNT) {
            prefs().setCustomSwatch(index, null)
        }
    }

    @Test
    fun customSwatchesRoundTripAndClear() {
        val prefs = prefs()
        for (index in 0 until Prefs.CUSTOM_SWATCH_COUNT) {
            prefs.setCustomSwatch(index, null)
        }
        prefs.setCustomSwatch(0, Color.RED)
        prefs.setCustomSwatch(3, Color.BLUE)

        val reloaded = prefs().customSwatches()
        assertEquals(Prefs.CUSTOM_SWATCH_COUNT, reloaded.size)
        assertEquals(Color.RED, reloaded[0])
        assertNull(reloaded[1])
        assertEquals(Color.BLUE, reloaded[3])

        prefs.setCustomSwatch(0, null)
        assertNull(prefs().customSwatch(0))
    }
}
