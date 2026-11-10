package com.dahl404.penny937.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class ImportedReference(
    val fileName: String,
    val width: Int,
    val height: Int
)

/**
 * Imported references live in app storage and are decoded lazily. Each project gets
 * its own folder, so deleting a project cannot remove another project's references
 * and a duplicate can own independent copies.
 *
 * Older builds stored references directly in `references/`. Those files are still
 * readable and are copied into a project folder the first time it is opened.
 */
object ReferenceFiles {
    private var root: File? = null
    private var counter = 0

    private data class Key(val projectId: Long, val name: String)

    private val cache = object : LinkedHashMap<Key, Bitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Bitmap>): Boolean =
            size > 4
    }

    fun init(context: Context) {
        root = File(context.filesDir, "references").apply { mkdirs() }
    }

    private fun projectDir(projectId: Long): File? = root?.let { File(it, projectId.toString()) }

    private fun projectDirForWrite(projectId: Long): File? =
        projectDir(projectId)?.apply { mkdirs() }

    /** Rejects separators and `..` so a crafted project cannot escape its folder. */
    private fun safeName(name: String): String? {
        if (name.isBlank()) return null
        return if (File(name).name == name) name else null
    }

    private fun fileFor(projectId: Long, name: String): File? {
        val safe = safeName(name) ?: return null
        return projectDir(projectId)?.let { File(it, safe) }
    }

    private fun legacyFile(name: String): File? {
        val safe = safeName(name) ?: return null
        return root?.let { File(it, safe) }
    }

    @Synchronized
    fun bitmap(projectId: Long, name: String): Bitmap? {
        val key = Key(projectId, name)
        cache[key]?.let { return it }
        val file = fileFor(projectId, name)?.takeIf { it.exists() }
            ?: legacyFile(name)?.takeIf { it.exists() }
            ?: return null
        val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            ?: return null
        cache[key] = bitmap
        return bitmap
    }

    fun saveJpeg(projectId: Long, name: String, bitmap: Bitmap, quality: Int = 86): Boolean {
        val folder = projectDirForWrite(projectId) ?: return false
        val safe = safeName(name) ?: return false
        return runCatching {
            FileOutputStream(File(folder, safe)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
        }.getOrDefault(false)
    }

    fun savePng(projectId: Long, name: String, bitmap: Bitmap): Boolean {
        val folder = projectDirForWrite(projectId) ?: return false
        val safe = safeName(name) ?: return false
        return runCatching {
            FileOutputStream(File(folder, safe)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }.getOrDefault(false)
    }

    /** Raw bytes of one reference, used by project archives. */
    fun readBytes(projectId: Long, name: String): ByteArray? {
        val file = fileFor(projectId, name)?.takeIf { it.exists() }
            ?: legacyFile(name)?.takeIf { it.exists() }
            ?: return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    fun writeBytes(projectId: Long, name: String, bytes: ByteArray): Boolean {
        val folder = projectDirForWrite(projectId) ?: return false
        val safe = safeName(name) ?: return false
        return runCatching {
            File(folder, safe).writeBytes(bytes)
        }.isSuccess
    }

    fun delete(projectId: Long, name: String) {
        synchronized(this) { cache.remove(Key(projectId, name)) }
        fileFor(projectId, name)?.delete()
    }

    /** Removes every reference owned by a deleted project. */
    fun deleteProject(projectId: Long) {
        synchronized(this) {
            cache.keys.removeAll { it.projectId == projectId }
        }
        projectDir(projectId)?.deleteRecursively()
    }

    /**
     * Deletes files in a project folder that the project no longer references.
     * Called when a project is opened (after a crash or a cancelled import) and
     * whenever an import is cancelled.
     */
    fun gc(projectId: Long, referenced: Set<String>) {
        val folder = projectDir(projectId) ?: return
        val files = folder.listFiles() ?: return
        val removed = HashSet<String>()
        for (file in files) {
            if (file.isFile && file.name !in referenced) {
                if (file.delete()) removed.add(file.name)
            }
        }
        if (removed.isNotEmpty()) {
            synchronized(this) {
                cache.keys.removeAll { it.projectId == projectId && it.name in removed }
            }
        }
    }

    /**
     * Copies the references a duplicated project needs into its own folder.
     * Returns false when a source file is missing, so callers can keep the
     * duplicate usable by letting the missing image render as a placeholder.
     */
    fun copyProject(fromProjectId: Long, toProjectId: Long, names: Set<String>): Boolean {
        if (names.isEmpty()) return true
        val target = projectDirForWrite(toProjectId) ?: return false
        var complete = true
        for (name in names) {
            val safe = safeName(name)
            if (safe == null) {
                complete = false
                continue
            }
            val destination = File(target, safe)
            if (destination.exists()) continue
            val source = fileFor(fromProjectId, safe)?.takeIf { it.exists() }
                ?: legacyFile(name)?.takeIf { it.exists() }
            if (source == null) {
                complete = false
                continue
            }
            val copied = runCatching {
                try {
                    Files.copy(
                        source.toPath(), destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (_: Exception) {
                    source.copyTo(destination, overwrite = true)
                }
                true
            }.getOrDefault(false)
            if (!copied) complete = false
        }
        return complete
    }

    /**
     * Copies references from the legacy shared folder into this project's folder.
     * Safe to call repeatedly; existing files win.
     */
    fun migrateLegacy(projectId: Long, referenced: Set<String>) {
        if (referenced.isEmpty()) return
        val target = projectDirForWrite(projectId) ?: return
        for (name in referenced) {
            val safe = safeName(name) ?: continue
            val destination = File(target, safe)
            if (destination.exists()) continue
            val source = legacyFile(safe)?.takeIf { it.exists() } ?: continue
            runCatching {
                Files.copy(source.toPath(), destination.toPath())
            }.recoverCatching {
                source.copyTo(destination, overwrite = false)
            }
        }
    }

    /** Copy + downscale + EXIF-orient a picked image into this project's folder. */
    fun import(context: Context, projectId: Long, uri: Uri): ImportedReference? {
        val folder = projectDirForWrite(projectId) ?: return null
        return runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // decodeStream returns null when only bounds were requested, so do not
            // treat that null as a failed read.
            val boundsStream = resolver.openInputStream(uri) ?: return null
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val sample = sampleSize(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decodeStream = resolver.openInputStream(uri) ?: return null
            var decoded = decodeStream.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            val orientation = runCatching {
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
            val matrix = orientationMatrix(orientation)
            if (matrix != null) {
                val rotated = runCatching {
                    Bitmap.createBitmap(
                        decoded, 0, 0, decoded.width, decoded.height, matrix, true
                    )
                }.getOrNull()
                if (rotated != null && rotated !== decoded) {
                    decoded.recycle()
                    decoded = rotated
                }
            }

            val name = nextName()
            val target = File(folder, name)
            FileOutputStream(target).use { out ->
                decoded.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            synchronized(this) { cache[Key(projectId, name)] = decoded }
            ImportedReference(name, decoded.width, decoded.height)
        }.getOrNull()
    }

    @Synchronized
    private fun nextName(): String = "ref-${System.currentTimeMillis()}-${counter++}.png"

    private fun sampleSize(width: Int, height: Int, max: Int = 2048): Int {
        var sample = 1
        while (width / sample > max || height / sample > max) sample *= 2
        return sample
    }

    private fun orientationMatrix(orientation: Int): Matrix? = when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { setScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { setRotate(180f) }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { setScale(1f, -1f) }
        ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply {
            setRotate(90f)
            postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { setRotate(90f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply {
            setRotate(-90f)
            postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { setRotate(-90f) }
        else -> null
    }
}
