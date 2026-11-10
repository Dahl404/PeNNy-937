package com.dahl404.penny937.data

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class ReferenceFilesTest {

    private val projectA = 9_001L
    private val projectB = 9_002L
    private val projectC = 9_003L

    @Before
    fun setUp() {
        ReferenceFiles.init(RuntimeEnvironment.getApplication())
        listOf(projectA, projectB, projectC).forEach { ReferenceFiles.deleteProject(it) }
    }

    @After
    fun tearDown() {
        listOf(projectA, projectB, projectC).forEach { ReferenceFiles.deleteProject(it) }
    }

    private fun bitmap(color: Int): Bitmap =
        Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Test
    fun projectsGetIndependentFilesWithTheSameName() {
        assertTrue(ReferenceFiles.savePng(projectA, "same.png", bitmap(Color.RED)))
        assertTrue(ReferenceFiles.savePng(projectB, "same.png", bitmap(Color.BLUE)))

        val a = ReferenceFiles.bitmap(projectA, "same.png")
        val b = ReferenceFiles.bitmap(projectB, "same.png")
        assertNotNull(a)
        assertNotNull(b)
        assertEquals(Color.RED, a!!.getPixel(0, 0))
        assertEquals(Color.BLUE, b!!.getPixel(0, 0))

        ReferenceFiles.deleteProject(projectA)
        assertNull(ReferenceFiles.bitmap(projectA, "same.png"))
        assertNotNull(ReferenceFiles.bitmap(projectB, "same.png"))
    }

    @Test
    fun gcRemovesOnlyUnreferencedFiles() {
        assertTrue(ReferenceFiles.savePng(projectA, "keep.png", bitmap(Color.GREEN)))
        assertTrue(ReferenceFiles.savePng(projectA, "orphan.png", bitmap(Color.BLUE)))

        ReferenceFiles.gc(projectA, setOf("keep.png"))
        assertNotNull(ReferenceFiles.bitmap(projectA, "keep.png"))
        assertNull(ReferenceFiles.bitmap(projectA, "orphan.png"))

        ReferenceFiles.gc(projectA, emptySet())
        assertNull(ReferenceFiles.bitmap(projectA, "keep.png"))
    }

    @Test
    fun duplicationCopiesReferenceBytes() {
        assertTrue(ReferenceFiles.savePng(projectA, "ref.png", bitmap(Color.MAGENTA)))
        assertTrue(
            ReferenceFiles.copyProject(projectA, projectB, setOf("ref.png", "missing.png")) == false
        )
        assertNotNull(ReferenceFiles.bitmap(projectB, "ref.png"))
        assertEquals(
            Color.MAGENTA,
            ReferenceFiles.bitmap(projectB, "ref.png")!!.getPixel(1, 1)
        )
        assertNull(ReferenceFiles.bitmap(projectB, "missing.png"))
        // The source is untouched.
        assertNotNull(ReferenceFiles.bitmap(projectA, "ref.png"))
    }

    @Test
    fun archiveBytesRoundTripThroughProjectFolders() {
        assertTrue(ReferenceFiles.savePng(projectA, "ref.png", bitmap(Color.CYAN)))
        val bytes = ReferenceFiles.readBytes(projectA, "ref.png")
        assertNotNull(bytes)
        assertTrue(ReferenceFiles.writeBytes(projectC, "ref.png", bytes!!))
        assertEquals(
            Color.CYAN,
            ReferenceFiles.bitmap(projectC, "ref.png")!!.getPixel(0, 0)
        )
        assertFalse(ReferenceFiles.writeBytes(0L, "../escape.png", byteArrayOf(1)))
    }
}
