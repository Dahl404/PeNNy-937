package com.dahl404.penny937.editor

import android.graphics.Color
import com.dahl404.penny937.data.Prefs
import com.dahl404.penny937.data.ProjectStore
import com.dahl404.penny937.model.ProjectFactory
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], manifest = Config.NONE)
class EyedropperSourceTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun model(): EditorModel {
        val context = RuntimeEnvironment.getApplication()
        val project = ProjectFactory.create("source test", 200, 200, 12, Color.WHITE)
        return EditorModel(context, ProjectStore(context), Prefs(context), project, scope)
    }

    @Test
    fun eyedropperSamplesAChosenLayerWithoutSelectingIt() {
        val editor = model()
        editor.addLayer()
        val lower = editor.frame.layers[0]
        val upper = editor.frame.layers[1]
        assertEquals("the new layer should be active", upper.id, editor.layer.id)
        editor.addStroke(
            Stroke(
                Tool.PEN,
                Color.RED,
                40f,
                1f,
                floatArrayOf(40f, 100f, 1f, 100f, 100f, 1f, 160f, 100f, 1f)
            ),
            0,
            0
        )

        assertTrue(editor.beginEyedropper(lower.id))
        assertEquals(lower.id, editor.eyedropper?.layerId)
        assertEquals("sampling must not change the active layer", upper.id, editor.layer.id)
        assertTrue(editor.sampleEyedropper(100f, 100f, 3f))
        assertEquals("the picked colour must come from the lower layer", Color.RED, editor.color)
        editor.cancelEyedropper()
        assertEquals("the active layer is still the upper one", upper.id, editor.layer.id)
    }
}
