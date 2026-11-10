package com.dahl404.penny937.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaCodecList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dahl404.penny937.data.ReferenceFiles
import com.dahl404.penny937.model.LayerImage
import com.dahl404.penny937.model.Project
import com.dahl404.penny937.model.ProjectFactory
import com.dahl404.penny937.model.ProjectOps
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.ZipFile

/**
 * On-device smoke test for the real export pipeline: vector rasterisation,
 * reference decoding, GIF/LZW, PNG zip and (when available) MediaCodec MP4.
 */
@RunWith(AndroidJUnit4::class)
class ExportSmokeTest {

    private lateinit var context: Context
    private var projectId: Long = 0L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ReferenceFiles.init(context)
    }

    @After
    fun tearDown() {
        if (projectId != 0L) ReferenceFiles.deleteProject(projectId)
    }

    private fun smokeProject(): Project {
        var project = ProjectFactory.create(
            "export smoke", 160, 120, 12, Color.WHITE
        )
        projectId = project.id

        val reference = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        assertTrue(ReferenceFiles.savePng(project.id, "smoke-ref.png", reference))
        reference.recycle()
        project = ProjectOps.addReferenceLayer(
            project, 0,
            ProjectFactory.referenceLayer(
                "Reference",
                LayerImage(
                    file = "smoke-ref.png",
                    sourceWidth = 2,
                    sourceHeight = 2,
                    centerX = 80f,
                    centerY = 60f,
                    width = 160f,
                    height = 120f,
                    inOutput = true
                )
            )
        )

        for (i in 0 until 3) {
            if (i > 0) project = ProjectOps.addFrameAfter(project, i - 1)
            val frame = project.frames[i]
            val inkLayer = frame.layers.indexOfFirst { !it.isReference }
            project = ProjectOps.addStroke(
                project, i, inkLayer,
                Stroke(
                    Tool.PEN,
                    Color.BLACK,
                    6f,
                    1f,
                    floatArrayOf(
                        10f, 20f + i * 8f, 1f,
                        80f, 50f + i * 8f, 0.7f,
                        150f, 40f + i * 8f, 1f
                    )
                )
            )
        }
        return project
    }

    @Test
    fun gifPngSequenceAndStillExportsProduceReadableFiles() = runBlocking {
        val project = smokeProject()
        var progress = 0f

        val gif = Exporter.export(
            context, project, 0, ExportFormat.GIF, 160
        ) { progress = it }
        assertTrue("gif exists", gif.isFile && gif.length() > 200)
        assertEquals("GIF89a", gif.readBytes().copyOfRange(0, 6).toString(Charsets.US_ASCII))
        assertEquals(1f, progress, 0.001f)
        gif.delete()

        progress = 0f
        val zipFile = Exporter.export(
            context, project, 0, ExportFormat.PNG_SEQUENCE, project.width
        ) { progress = it }
        assertTrue("png zip exists", zipFile.isFile && zipFile.length() > 200)
        ZipFile(zipFile).use { zip ->
            assertNotNull(zip.getEntry("about.txt"))
            assertEquals(
                project.frames.size,
                zip.entries().asSequence().count { it.name.endsWith(".png") }
            )
        }
        assertEquals(1f, progress, 0.001f)
        zipFile.delete()

        val still = Exporter.export(
            context, project, 1, ExportFormat.CURRENT_FRAME, project.width
        ) { progress = it }
        assertTrue("still exists", still.isFile && still.length() > 100)
        val signature = still.readBytes().copyOfRange(0, 8)
        assertEquals(0x89.toByte(), signature[0])
        assertEquals(0x50.toByte(), signature[1])
        assertEquals(0x4E.toByte(), signature[2])
        assertEquals(0x47.toByte(), signature[3])
        assertEquals(1f, progress, 0.001f)
        still.delete()
    }

    @Test
    fun mp4ExportProducesAFileWhenAnEncoderExists() = runBlocking {
        val hasEncoder = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { it.equals("video/avc", ignoreCase = true) }
        }
        assumeTrue("No H.264 encoder on this device", hasEncoder)

        val project = smokeProject()
        var progress = 0f
        val mp4 = Exporter.export(
            context, project, 0, ExportFormat.MP4, 160
        ) { progress = it }
        assertTrue("mp4 exists", mp4.isFile && mp4.length() > 500)
        assertEquals(1f, progress, 0.001f)
        mp4.delete()
    }
}
