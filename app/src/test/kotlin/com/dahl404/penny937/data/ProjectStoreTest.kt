package com.dahl404.penny937.data

import com.dahl404.penny937.model.ProjectFactory
import com.dahl404.penny937.model.ProjectOps
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ProjectStoreTest {

    private fun store() = ProjectStore(RuntimeEnvironment.getApplication())

    @Before
    fun setUp() {
        store().delete(PROJECT_ID)
    }

    @After
    fun tearDown() {
        store().delete(PROJECT_ID)
    }

    @Test
    fun saveReportsSuccessAndReloadsTheDocument() {
        var project = ProjectFactory.create("Disk", 320, 240, 12, 0xFFFFFFFF.toInt())
            .copy(id = PROJECT_ID)
        project = ProjectOps.addStroke(
            project, 0, 0,
            Stroke(Tool.PEN, 0xFF000000.toInt(), 4f, 1f, floatArrayOf(1f, 2f, 1f, 3f, 4f, 1f))
        )
        val store = store()
        assertTrue(store.save(project))
        val loaded = store.load(PROJECT_ID)
        assertNotNull(loaded)
        assertEquals(1, loaded!!.frames[0].layers[0].strokes.size)
    }

    private fun named(project: com.dahl404.penny937.model.Project, name: String) =
        project.copy(name = name, updatedAt = project.updatedAt + 1)

    @Test
    fun theStoreCanBeDrivenByAnAlternateCodec() {
        var writes = 0
        var reads = 0
        val spy = object : ProjectFormat {
            override fun write(project: com.dahl404.penny937.model.Project, output: java.io.OutputStream) {
                writes++
                ProjectCodec.write(project, output)
            }

            override fun read(input: java.io.InputStream): com.dahl404.penny937.model.Project? {
                reads++
                return ProjectCodec.read(input)
            }

            override fun readMeta(input: java.io.InputStream): ProjectMeta? =
                ProjectCodec.readMeta(input)
        }
        val store = ProjectStore(RuntimeEnvironment.getApplication(), spy)
        val project = ProjectFactory.create("Codec", 320, 240, 12, 0xFFFFFFFF.toInt())
            .copy(id = PROJECT_ID)
        assertTrue(store.save(project))
        assertTrue(writes > 0)
        assertNotNull(store.load(PROJECT_ID))
        assertTrue(reads > 0)
    }

    @Test
    fun saveKeepsExactlyOneBackupOfThePreviousDocument() {
        val store = store()
        val first = named(
            ProjectFactory.create("First", 320, 240, 12, 0xFFFFFFFF.toInt())
                .copy(id = PROJECT_ID),
            "First"
        )
        val second = named(first, "Second")
        val third = named(second, "Third")

        assertTrue(store.save(first))
        assertFalse("a new project has nothing to back up", store.backupFile(PROJECT_ID).exists())

        assertTrue(store.save(second))
        val backup = store.backupFile(PROJECT_ID)
        assertTrue(backup.isFile)
        val previous = ProjectCodec.read(backup.inputStream())!!
        assertEquals("First", previous.name)
        assertEquals("Second", store.load(PROJECT_ID)!!.name)

        assertTrue(store.save(third))
        assertEquals("Second", ProjectCodec.read(store.backupFile(PROJECT_ID).inputStream())!!.name)
        assertEquals(
            "only one .bak is kept",
            1,
            store.projectFile(PROJECT_ID).parentFile!!
                .listFiles { f -> f.name.endsWith(".bak") }!!.size
        )
    }

    @Test
    fun loadRestoresTheBackupWhenThePrimaryFileIsCorrupt() {
        val store = store()
        val first = named(
            ProjectFactory.create("Survivor", 320, 240, 12, 0xFFFFFFFF.toInt())
                .copy(id = PROJECT_ID),
            "Survivor"
        )
        val second = named(first, "Newer")
        assertTrue(store.save(first))
        assertTrue(store.save(second))

        // Simulate a torn write: the primary is unreadable, the backup still holds
        // the last complete document.
        store.projectFile(PROJECT_ID).writeBytes(byteArrayOf(1, 2, 3, 4))
        val recovered = store.load(PROJECT_ID)
        assertNotNull(recovered)
        assertEquals("Survivor", recovered!!.name)

        // The recovered bytes are put back at the primary path.
        val primary = ProjectCodec.read(store.projectFile(PROJECT_ID).inputStream())!!
        assertEquals("Survivor", primary.name)

        // The list also falls back to the backup before load repairs the primary.
        store.projectFile(PROJECT_ID).writeBytes(byteArrayOf(1, 2, 3, 4))
        val listed = store.list().firstOrNull { it.id == PROJECT_ID }
        assertNotNull(listed)
        assertEquals("Survivor", listed!!.name)
    }

    @Test
    fun deletingAProjectRemovesItsBackup() {
        val store = store()
        val project = ProjectFactory.create("Gone", 320, 240, 12, 0xFFFFFFFF.toInt())
            .copy(id = PROJECT_ID)
        assertTrue(store.save(project))
        assertTrue(store.save(named(project, "Gone again")))
        assertTrue(store.backupFile(PROJECT_ID).isFile)

        store.delete(PROJECT_ID)
        assertFalse(store.projectFile(PROJECT_ID).exists())
        assertFalse(store.backupFile(PROJECT_ID).exists())
        assertNull(store.load(PROJECT_ID))
    }

    @Test
    fun saveReturnsFalseWhenTheTargetCannotBeReplaced() {
        val store = store()
        val project = ProjectFactory.create("Blocked", 320, 240, 12, 0xFFFFFFFF.toInt())
            .copy(id = PROJECT_ID)

        // A non-empty directory where the project file should go makes the final
        // rename fail. That must be reported, not swallowed as a successful save.
        val target = store.projectFile(PROJECT_ID)
        assertTrue(target.mkdirs())
        File(target, "blocker").writeText("occupied")

        assertFalse(store.save(project))
        assertTrue("the blocking directory is left alone", target.isDirectory)
        assertFalse(
            "the temporary file is cleaned up",
            File(target.parentFile, "$PROJECT_ID.tmp").exists()
        )

        // Once the path is clear, the same save succeeds.
        target.deleteRecursively()
        assertTrue(store.save(project))
        assertNotNull(store.load(PROJECT_ID))
    }

    private companion object {
        const val PROJECT_ID = 6_001L
    }
}
