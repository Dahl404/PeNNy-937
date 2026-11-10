package com.dahl404.penny937.paint

import android.graphics.Bitmap
import android.graphics.Color
import com.dahl404.penny937.data.ReferenceFiles
import com.dahl404.penny937.model.Frame
import com.dahl404.penny937.model.Layer
import com.dahl404.penny937.model.LayerImage
import com.dahl404.penny937.model.Project
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], manifest = Config.NONE)
class ReferenceExportTest {

    private val projectId = 7_777L

    @Before
    fun setUp() {
        ReferenceFiles.init(RuntimeEnvironment.getApplication())
        ReferenceFiles.deleteProject(projectId)
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        assertTrue(ReferenceFiles.savePng(projectId, "ref.png", bitmap))
    }

    @After
    fun tearDown() {
        ReferenceFiles.deleteProject(projectId)
    }

    private fun project(inOutput: Boolean): Project {
        val image = LayerImage(
            file = "ref.png",
            sourceWidth = 2,
            sourceHeight = 2,
            centerX = 16f,
            centerY = 16f,
            width = 32f,
            height = 32f,
            inOutput = inOutput
        )
        val frame = Frame(
            id = 2L,
            layers = listOf(
                Layer(id = 3L, name = "Ink"),
                Layer(id = 4L, name = "Reference", locked = true, image = image)
            )
        )
        return Project(
            id = projectId,
            name = "ref export",
            width = 32,
            height = 32,
            fps = 12,
            background = Color.WHITE,
            frames = listOf(frame),
            createdAt = 1,
            updatedAt = 1
        )
    }

    private fun center(project: Project): Int {
        val bitmap = FrameRasterizer.rasterize(
            project, project.frames[0], 32, 32, project.background, includeReferences = false
        )
        val pixel = bitmap.getPixel(16, 16)
        bitmap.recycle()
        return pixel
    }

    @Test
    fun guideOnlyReferencesStayOutOfTheExport() {
        assertEquals(Color.WHITE, center(project(inOutput = false)))
    }

    @Test
    fun referencesMarkedInOutputAreRendered() {
        val pixel = center(project(inOutput = true))
        assertEquals(255, Color.red(pixel))
        assertEquals(0, Color.green(pixel))
        assertEquals(0, Color.blue(pixel))
    }
}
