package com.dahl404.penny937

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.dahl404.penny937.ui.ColorSheetPreview
import com.dahl404.penny937.ui.theme.PennyTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the Color sheet off-device so layout regressions (a wheel that does not
 * fill its space, clipped controls, uneven swatches) can be inspected as an image.
 * Run with: ./gradlew testDebugUnitTest --tests '*ColorSheetScreenshotTest*'
 * Output: app/build/screenshots/color-sheet.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], manifest = Config.NONE, qualifiers = "w480dp-h1000dp-xhdpi")
class ColorSheetScreenshotTest {

    @Test
    fun renderColorSheet() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            PennyTheme {
                Box(Modifier.fillMaxSize()) {
                    ColorSheetPreview()
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()

        val root: View = activity.window.decorView
        val width = 1440
        val height = 2800
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, width, height)
        shadowOf(Looper.getMainLooper()).idle()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val out = File("build/screenshots/color-sheet.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        assertTrue("screenshot should be written", out.length() > 0)
    }
}
