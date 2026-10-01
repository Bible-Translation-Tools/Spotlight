package org.bibletranslationtools.glossary

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.source
import kotlinx.io.IOException
import kotlinx.io.Source
import kotlinx.io.asInputStream
import kotlinx.io.asOutputStream
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemPathSeparator
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtils {

    fun extractZip(file: PlatformFile, destDir: Path) {
        SystemFileSystem.createDirectories(destDir)

        val source: Source = file.source().buffered()
        val zipInputStream = ZipInputStream(source.asInputStream())

        zipInputStream.use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val destPath = Path(destDir, entry.name)

                val destDirCanonical = File(destDir.toString()).canonicalPath
                val entryCanonical = File(destPath.toString()).canonicalPath

                if (!entryCanonical.startsWith(destDirCanonical)) {
                    throw IOException(
                        "Zip entry is trying to extract outside of destination directory: ${entry.name}"
                    )
                }

                if (entry.isDirectory) {
                    SystemFileSystem.createDirectories(destPath)
                } else {
                    destPath.parent?.let { parentDir ->
                        if (!SystemFileSystem.exists(parentDir)) {
                            SystemFileSystem.createDirectories(parentDir)
                        }
                    }

                    SystemFileSystem.sink(destPath).buffered().use { fileSink ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (zis.read(buffer).also { bytesRead = it } != -1) {
                            fileSink.write(buffer, 0, bytesRead)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /** Name and text of the first file entry whose name matches [predicate]. */
    fun readEntry(file: PlatformFile, predicate: (String) -> Boolean): Pair<String, String>? {
        val source: Source = file.source().buffered()
        val zipInputStream = ZipInputStream(source.asInputStream())

        zipInputStream.use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && predicate(entry.name)) {
                    return entry.name to zis.readBytes().decodeToString()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return null
    }

    fun zipDirectory(source: Path, target: PlatformFile) {
        if (!SystemFileSystem.exists(source)
            || SystemFileSystem.metadataOrNull(source)?.isDirectory != true) {
            throw IllegalArgumentException("Source directory does not exist or is not a directory: $source")
        }

        val sink = target.sink().buffered()
        val zipOutputStream = ZipOutputStream(sink.asOutputStream())

        zipOutputStream.use { zos ->
            SystemFileSystem.list(source).forEach { path ->
                zipRecursively(source, path, zos)
            }
        }
    }

    private fun zipRecursively(rootDir: Path, currentPath: Path, zos: ZipOutputStream) {
        val metadata = SystemFileSystem.metadataOrNull(currentPath) ?: return

        val entryName = currentPath.toString()
            .removePrefix(rootDir.toString())
            .trimStart(SystemPathSeparator)
            .replace(SystemPathSeparator, '/')

        if (metadata.isDirectory) {
            val dirEntry = ZipEntry("$entryName/")
            zos.putNextEntry(dirEntry)
            zos.closeEntry()

            SystemFileSystem.list(currentPath).forEach { child ->
                zipRecursively(rootDir, child, zos)
            }
        } else if (metadata.isRegularFile) {
            val fileEntry = ZipEntry(entryName)
            zos.putNextEntry(fileEntry)

            SystemFileSystem.source(currentPath).buffered().use { fileSource ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fileSource.readAtMostTo(buffer).also { bytesRead = it } != -1) {
                    zos.write(buffer, 0, bytesRead)
                }
            }
            zos.closeEntry()
        }
    }
}