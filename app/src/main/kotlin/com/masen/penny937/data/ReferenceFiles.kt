package com.masen.penny937.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

data class ImportedReference(
    val fileName: String,
    val width: Int,
    val height: Int
)

/**
 * Imported reference images live in app storage and are decoded lazily.
 * Layers only store the file name, so projects stay small and portable.
 */
object ReferenceFiles {
    private var dir: File? = null
    private var counter = 0

    private val cache = object : LinkedHashMap<String, Bitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean =
            size > 3
    }

    fun init(context: Context) {
        dir = File(context.filesDir, "references").apply { mkdirs() }
    }

    @Synchronized
    fun bitmap(name: String): Bitmap? {
        cache[name]?.let { return it }
        val folder = dir ?: return null
        val file = File(folder, name)
        if (!file.exists()) return null
        val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            ?: return null
        cache[name] = bitmap
        return bitmap
    }

    fun delete(name: String) {
        synchronized(this) { cache.remove(name) }
        dir?.let { File(it, name).delete() }
    }

    /** Copy + downscale + EXIF-orient a picked image into app storage. */
    fun import(context: Context, uri: Uri): ImportedReference? {
        val folder = dir ?: File(context.filesDir, "references").apply { mkdirs() }.also { dir = it }
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

            val name = "ref-${System.currentTimeMillis()}-${counter++}.png"
            val target = File(folder, name)
            FileOutputStream(target).use { out ->
                decoded.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            synchronized(this) { cache[name] = decoded }
            ImportedReference(name, decoded.width, decoded.height)
        }.getOrNull()
    }

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
