package com.dahl404.penny937.data

import com.dahl404.penny937.model.Project
import com.dahl404.penny937.model.ProjectFactory
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * A portable project bundle: the `.p937` vector document plus every reference
 * file it uses. The zip is a backup that survives a reinstall without relying on
 * Android's auto-backup (which deliberately excludes bulky references).
 */
object ProjectArchive {
    const val PROJECT_ENTRY = "project.p937"
    const val REFERENCE_PREFIX = "references/"

    fun write(project: Project, output: OutputStream): Boolean = runCatching {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(PROJECT_ENTRY))
            ProjectCodec.write(project, zip)
            zip.closeEntry()
            for (name in project.referencedFiles()) {
                val bytes = ReferenceFiles.readBytes(project.id, name) ?: continue
                zip.putNextEntry(ZipEntry(REFERENCE_PREFIX + File(name).name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        true
    }.getOrDefault(false)

    /**
     * Reads an archive into a fresh project. The imported project gets new ids and
     * its references are written to its own folder, so it can coexist with the
     * project it was exported from.
     */
    fun read(archive: File): Project? = runCatching {
        ZipFile(archive).use { zip ->
            val entry = zip.getEntry(PROJECT_ENTRY) ?: return null
            val source = zip.getInputStream(entry).use { ProjectCodec.read(it) } ?: return null
            val project = ProjectFactory.duplicateProject(source, source.name)
            for (name in project.referencedFiles()) {
                val reference = zip.getEntry(REFERENCE_PREFIX + File(name).name) ?: continue
                val bytes = zip.getInputStream(reference).use { it.readBytes() }
                ReferenceFiles.writeBytes(project.id, File(name).name, bytes)
            }
            project
        }
    }.getOrNull()
}
