package com.dahl404.penny937.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.dahl404.penny937.data.Prefs
import com.dahl404.penny937.data.ProjectStore
import com.dahl404.penny937.data.ReferenceFiles
import com.dahl404.penny937.model.Ids
import com.dahl404.penny937.model.Layer
import com.dahl404.penny937.model.LayerImage
import com.dahl404.penny937.model.ProjectFactory
import com.dahl404.penny937.model.ProjectOps
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import com.dahl404.penny937.paint.FrameRasterizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Merging is allowed for every layer pair. Hard cases (an upper eraser, reference
 * images) flatten the pair into raster ink, and that flattening must not change a
 * single visible pixel.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], manifest = Config.NONE)
class LayerMergeTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val width = 200
    private val height = 200

    private fun model(): EditorModel {
        val context = RuntimeEnvironment.getApplication()
        ReferenceFiles.init(context)
        val project = ProjectFactory.create(
            "merge test", width, height, 12, Color.WHITE
        )
        return EditorModel(context, ProjectStore(context), Prefs(context), project, scope)
    }

    private fun line(
        tool: Tool,
        color: Int,
        width: Float,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float
    ): Stroke {
        val points = FloatArray(21)
        for (i in 0 until 7) {
            val t = i / 6f
            points[i * 3] = x0 + (x1 - x0) * t
            points[i * 3 + 1] = y0 + (y1 - y0) * t
            points[i * 3 + 2] = 1f
        }
        return Stroke(tool, color, width, 1f, points)
    }

    private fun render(editor: EditorModel, includeReferences: Boolean = false): Bitmap =
        FrameRasterizer.rasterize(
            editor.project,
            editor.frame,
            width,
            height,
            editor.project.background,
            includeReferences = includeReferences
        )

    private fun assertSameImage(expected: Bitmap, actual: Bitmap) {
        val a = IntArray(width * height)
        val b = IntArray(width * height)
        expected.getPixels(a, 0, width, 0, 0, width, height)
        actual.getPixels(b, 0, width, 0, 0, width, height)
        var differing = 0
        var maxDelta = 0
        for (i in a.indices) {
            val aa = a[i]
            val bb = b[i]
            val delta = maxOf(
                abs(((aa ushr 24) and 0xFF) - ((bb ushr 24) and 0xFF)),
                abs(((aa shr 16) and 0xFF) - ((bb shr 16) and 0xFF)),
                abs(((aa shr 8) and 0xFF) - ((bb shr 8) and 0xFF)),
                abs((aa and 0xFF) - (bb and 0xFF))
            )
            if (delta > 2) differing++
            if (delta > maxDelta) maxDelta = delta
        }
        assertEquals("merged pixels must match the unmerged frame", 0, differing)
        assertTrue("max channel delta was $maxDelta", maxDelta <= 2)
    }

    @Test
    fun upperEraserMergePreservesTheImage() {
        val editor = model()
        editor.addLayer()
        // Lower layer: wide red band.
        editor.addStroke(
            line(Tool.PEN, Color.RED, 80f, 20f, 100f, 180f, 100f), 0, 0
        )
        // Upper layer: blue band plus an eraser that cuts a hole out of the blue.
        editor.addStroke(
            line(Tool.PEN, Color.BLUE, 80f, 100f, 20f, 100f, 180f), 0, 1
        )
        editor.addStroke(
            line(Tool.ERASER, Color.BLACK, 40f, 100f, 70f, 100f, 130f), 0, 1
        )

        val before = render(editor)
        assertTrue(
            "an upper eraser needs the raster merge path",
            com.dahl404.penny937.model.ProjectOps.mergeNeedsFlattening(
                editor.frame.layers[0], editor.frame.layers[1]
            )
        )

        editor.mergeLayerDown()

        val layers = editor.frame.layers
        assertEquals(1, layers.size)
        assertTrue("the merged layer must be raster ink", layers[0].flattened)
        assertEquals(false, layers[0].isReference)
        assertNotNull(layers[0].image)
        assertNotNull(
            "the merged image must be stored in the project folder",
            ReferenceFiles.readBytes(editor.project.id, layers[0].image!!.file)
        )
        assertEquals(0, layers[0].strokes.size)

        val after = render(editor)
        assertSameImage(before, after)
        before.recycle()
        after.recycle()
        editor.discard()
    }

    @Test
    fun simpleMergeStaysVector() {
        val editor = model()
        editor.addLayer()
        editor.addStroke(
            line(Tool.PEN, Color.RED, 20f, 20f, 100f, 180f, 100f), 0, 0
        )
        editor.addStroke(
            line(Tool.MARKER, Color.BLUE, 30f, 20f, 120f, 180f, 120f), 0, 1
        )

        editor.mergeLayerDown()

        val merged = editor.frame.layers.single()
        assertEquals(false, merged.flattened)
        assertNull(merged.image)
        assertEquals(2, merged.strokes.size)
        editor.discard()
    }

    @Test
    fun hiddenLowerLayerMergesToTheVisibleResult() {
        val editor = model()
        editor.addLayer()
        editor.addStroke(
            line(Tool.PEN, Color.RED, 40f, 20f, 100f, 180f, 100f), 0, 0
        )
        editor.addStroke(
            line(Tool.PEN, Color.BLUE, 40f, 20f, 100f, 180f, 100f), 0, 1
        )
        editor.setLayerVisible(0, false)

        val before = render(editor)
        editor.mergeLayerDown()
        val after = render(editor)

        val merged = editor.frame.layers.single()
        assertTrue(merged.visible)
        assertEquals(Tool.PEN, merged.strokes.single().tool)
        assertEquals(Color.BLUE, merged.strokes.single().color)
        assertSameImage(before, after)
        before.recycle()
        after.recycle()
        editor.discard()
    }

    @Test
    fun referenceLayerAboveInkMergesWithoutChangingTheCanvas() {
        val context = RuntimeEnvironment.getApplication()
        ReferenceFiles.init(context)
        var project = ProjectFactory.create("merge reference", width, height, 12, Color.WHITE)
        val referenceBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(referenceBitmap).drawRect(
            0f, 0f, width.toFloat(), height / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GREEN }
        )
        assertTrue(ReferenceFiles.savePng(project.id, "ref.png", referenceBitmap))
        referenceBitmap.recycle()

        val ink = Layer(id = Ids.next(), name = "Ink")
        val reference = ProjectFactory.referenceLayer(
            "Reference",
            LayerImage(
                file = "ref.png",
                sourceWidth = width,
                sourceHeight = height,
                centerX = width / 2f,
                centerY = height / 2f,
                width = width.toFloat(),
                height = height.toFloat(),
                rotation = 0f,
                inOutput = false
            )
        )
        project = project.copy(
            frames = listOf(project.frames[0].copy(layers = listOf(ink, reference)))
        )
        project = ProjectOps.addStroke(
            project, 0, 0, line(Tool.PEN, Color.RED, 40f, 20f, 80f, 180f, 80f)
        )
        // The reference is the top layer, so it is the selected layer.
        val editor = EditorModel(context, ProjectStore(context), Prefs(context), project, scope)

        val before = render(editor, includeReferences = true)
        editor.mergeLayerDown()

        val merged = editor.frame.layers.single()
        assertTrue("merging a reference needs raster ink", merged.flattened)
        assertEquals(false, merged.isReference)
        val after = render(editor, includeReferences = true)
        assertSameImage(before, after)
        before.recycle()
        after.recycle()
        editor.discard()
    }
}
