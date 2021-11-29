package com.memfault.bort

import com.memfault.bort.fileExt.deleteSilently
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Add a file to an exist zip archive. The only way to do this is to copy everything from the existing archive into a
 * new archive.
 */
fun addFileToZip(zipFile: File, newFile: File, newfileName: String) {
    // Rename bugreport file to .tmp
    val existingZipFile = File(zipFile.parent, "${zipFile.name}.tmp")
    zipFile.renameTo(existingZipFile)

    // Create a new file, with the original name.
    ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
        zipOut.setLevel(COMPRESSION_LEVEL_HIGHEST)
        ZipFile(existingZipFile).use { zipIn ->
            for (entry in zipIn.entries()) {
                zipOut.addZipEntry(entry.name, zipIn.getInputStream(entry))
            }
        }
        newFile.inputStream().buffered().use { fileIn ->
            zipOut.addZipEntry(newfileName, fileIn)
        }
    }

    existingZipFile.deleteSilently()
}

fun ZipOutputStream.addZipEntry(entryName: String, inputStream: InputStream) {
    // Must create a new entry: old one has compressed size etc already set and will error.
    val entry = ZipEntry(entryName)
    putNextEntry(entry)
    if (!entry.isDirectory) {
        var bytesRead: Int
        val buffer = ByteArray(BUFFER)
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            write(buffer, 0, bytesRead)
        }
    }
    closeEntry()
}

private const val BUFFER = 2048
const val COMPRESSION_LEVEL_HIGHEST = 9
