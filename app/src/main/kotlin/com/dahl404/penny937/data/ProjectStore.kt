package com.dahl404.penny937.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.dahl404.penny937.model.Frame
import com.dahl404.penny937.model.Project
import com.dahl404.penny937.paint.FrameRasterizer
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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
class ProjectStore(
    context: Context,
    private val format: ProjectFormat = ProjectCodec
) {

    private val dir = File(context.filesDir, "projects").apply { mkdirs() }
    private val archiveDir = File(context.cacheDir, "exports").apply { mkdirs() }

    fun list(): List<ProjectMeta> {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) } ?: return emptyList()
        return files.mapNotNull { file ->
            val id = file.name.removeSuffix(SUFFIX).toLongOrNull()
            // If the primary file was interrupted, show the project from its one
            // backup rather than dropping it off the home screen.
            val meta = readMeta(file) ?: id?.let { readMeta(backupFile(it)) }
            meta?.copy(hasThumbnail = thumbnailFile(meta.id).exists())
        }.sortedByDescending { it.updatedAt }
    }

    fun load(id: Long): Project? {
        val primary = projectFile(id)
        val backup = backupFile(id)
        var project = readProject(primary)
        if (project == null && backup.isFile) {
            project = readProject(backup)
            // Put the recovered bytes back at the primary path so the next save
            // rotates a valid document instead of backing up a corrupt one.
            if (project != null) {
                runCatching { backup.copyTo(primary, overwrite = true) }
            }
        }
        if (project == null) return null
        // Old builds used one shared reference folder. Copy those files in, then
        // remove leftovers from a crash or a cancelled import.
        val referenced = project.referencedFiles()
        ReferenceFiles.migrateLegacy(project.id, referenced)
        ReferenceFiles.gc(project.id, referenced)
        return project
    }

    private fun readProject(file: File): Project? {
        if (!file.isFile) return null
        return runCatching {
            file.inputStream().use { format.read(it) }
        }.getOrNull()
    }

    private fun readMeta(file: File): ProjectMeta? {
        if (!file.isFile) return null
        return runCatching {
            file.inputStream().use { format.readMeta(it) }
        }.getOrNull()
    }

    /**
     * Writes the document atomically. Returns false when the bytes could not be
     * committed, so the editor can keep the previous save state and offer a retry
     * instead of pretending a failed write succeeded.
     */
    fun save(project: Project): Boolean {
        val target = projectFile(project.id)
        val backup = backupFile(project.id)
        val tmp = File(dir, "${project.id}.tmp")
        return try {
            FileOutputStream(tmp).use { format.write(project, it) }

            // Keep exactly one previous document next to the new one. Moving is
            // cheap; the copy fallback covers file systems where rename is refused.
            if (target.isFile) {
                backup.delete()
                val backedUp = target.renameTo(backup) || runCatching {
                    target.copyTo(backup, overwrite = true)
                    true
                }.getOrDefault(false)
                if (backedUp && target.isFile) target.delete()
            }

            val moved = try {
                Files.move(
                    tmp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
                )
                true
            } catch (_: Exception) {
                if (target.isFile) target.delete()
                tmp.renameTo(target)
            }
            if (!moved) {
                tmp.delete()
                // The old document was rotated to .bak before the failed move.
                // Put it back so a failed save never leaves the project missing.
                if (!target.exists() && backup.isFile) backup.renameTo(target)
            }
            moved
        } catch (_: Exception) {
            tmp.delete()
            if (!target.exists() && backup.isFile) backup.renameTo(target)
            false
        }
    }

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
        backupFile(id).delete()
        thumbnailFile(id).delete()
        ReferenceFiles.deleteProject(id)
    }

    /** Writes a portable zip containing the project and its references. */
    fun exportArchive(project: Project): File? = runCatching {
        archiveDir.listFiles()?.forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > 24L * 60 * 60 * 1000) {
                file.delete()
            }
        }
        val target = File(
            archiveDir,
            "${sanitize(project.name)}-${System.currentTimeMillis()}.p937.zip"
        )
        FileOutputStream(target).use { out -> ProjectArchive.write(project, out) }
        target
    }.getOrNull()

    private fun sanitize(name: String): String =
        name.trim().replace(Regex("[^A-Za-z0-9._ -]"), "_").take(48)
            .ifBlank { "project" }

    /** Imports a project zip and saves it as a new project. */
    fun importArchive(input: InputStream): Project? {
        val temp = File(dir, "import-${System.currentTimeMillis()}.zip")
        return try {
            temp.outputStream().use { out -> input.copyTo(out) }
            val imported = ProjectArchive.read(temp) ?: return null
            save(imported)
            imported
        } catch (_: Exception) {
            null
        } finally {
            temp.delete()
        }
    }

    fun thumbnailFile(id: Long): File = File(dir, "$id.png")

    fun projectFile(id: Long): File = File(dir, "$id$SUFFIX")

    /** The single previous document, kept for recovery after a failed write. */
    fun backupFile(id: Long): File = File(dir, "$id$SUFFIX$BACKUP_SUFFIX")

    companion object {
        private const val SUFFIX = ".p937"
        private const val BACKUP_SUFFIX = ".bak"
    }
}
