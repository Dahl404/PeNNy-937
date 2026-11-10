package com.dahl404.penny937.data

import android.graphics.Bitmap
import android.graphics.Color
import com.dahl404.penny937.model.LayerImage
import com.dahl404.penny937.model.ProjectFactory
import com.dahl404.penny937.model.ProjectOps
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], manifest = Config.NONE)
class ProjectArchiveTest {

    private val sourceId = 8_001L

    @Before
    fun setUp() {
        ReferenceFiles.init(RuntimeEnvironment.getApplication())
        ReferenceFiles.deleteProject(sourceId)
    }

    @After
    fun tearDown() {
        ReferenceFiles.deleteProject(sourceId)
    }

    @Test
    fun archiveBundlesTheDocumentAndItsReferences() {
        var project = ProjectFactory.create("Archived", 640, 480, 12, 0xFFFFFFFF.toInt())
        val image = LayerImage(
            file = "ref.png",
            sourceWidth = 2,
            sourceHeight = 2,
            centerX = 320f,
            centerY = 240f,
            width = 640f,
            height = 480f,
            inOutput = true
        )
        project = ProjectOps.addReferenceLayer(
            project, 0, ProjectFactory.referenceLayer("Reference", image)
        )
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        assertTrue(ReferenceFiles.savePng(project.id, "ref.png", bitmap))

        val file = File("build/test-output").apply { mkdirs() }.resolve("project.p937.zip")
        FileOutputStream(file).use { out -> assertTrue(ProjectArchive.write(project, out)) }
        assertTrue(file.length() > 100)

        val imported = ProjectArchive.read(file)
        assertNotNull(imported)
        imported!!
        assertNotEquals(project.id, imported.id)
        assertEquals(project.name, imported.name)
        assertEquals(true, imported.frames[0].layers[0].image!!.inOutput)
        val restored = ReferenceFiles.bitmap(imported.id, "ref.png")
        assertNotNull(restored)
        assertEquals(Color.RED, restored!!.getPixel(1, 1))
        ReferenceFiles.deleteProject(imported.id)
    }

    @Test
    fun readRejectsAFileThatIsNotAnArchive() {
        val file = File("build/test-output").apply { mkdirs() }.resolve("not-a-project.zip")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        assertEquals(null, ProjectArchive.read(file))
        assertFalse(file.delete() && file.exists())
    }
}
