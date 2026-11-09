package com.masen.penny937.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.masen.penny937.model.Frame
import com.masen.penny937.model.Project
import com.masen.penny937.paint.FrameRasterizer
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class ProjectMeta(
    val id: Long,
    val name: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val frameCount: Int,
    val updatedAt: Long,
    val background: Int,
    val hasThumbnail: Boolean
)

/**
 * Projects live as compact vector files in app storage, with a PNG poster next to each.
 */
class ProjectStore(context: Context) {

    private val dir = File(context.filesDir, "projects").apply { mkdirs() }

    fun list(): List<ProjectMeta> {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) } ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching {
                file.inputStream().use { ProjectCodec.readMeta(it) }?.let { meta ->
                    meta.copy(hasThumbnail = thumbnailFile(meta.id).exists())
                }
            }.getOrNull()
        }.sortedByDescending { it.updatedAt }
    }

    fun load(id: Long): Project? {
        val file = projectFile(id)
        if (!file.exists()) return null
        return runCatching {
            file.inputStream().use { ProjectCodec.read(it) }
        }.getOrNull()
    }

    fun save(project: Project): Boolean = runCatching {
        val target = projectFile(project.id)
        val tmp = File(dir, "${project.id}.tmp")
        FileOutputStream(tmp).use { ProjectCodec.write(project, it) }
        try {
            Files.move(
                tmp.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }
        true
    }.getOrDefault(false)

    fun saveThumbnail(project: Project, frame: Frame) {
        runCatching {
            val targetWidth = 640
            val targetHeight = (targetWidth.toFloat() * project.height / project.width)
                .toInt().coerceAtLeast(1)
            val bitmap = FrameRasterizer.rasterize(
                project, frame, targetWidth, targetHeight, project.background
            )
            FileOutputStream(thumbnailFile(project.id)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
        }
    }

    fun loadThumbnail(id: Long): Bitmap? {
        val file = thumbnailFile(id)
        if (!file.exists()) return null
        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        }.getOrNull()
    }

    fun delete(id: Long) {
        projectFile(id).delete()
        thumbnailFile(id).delete()
    }

    fun thumbnailFile(id: Long): File = File(dir, "$id.png")

    fun projectFile(id: Long): File = File(dir, "$id$SUFFIX")

    companion object {
        private const val SUFFIX = ".p937"
    }
}
