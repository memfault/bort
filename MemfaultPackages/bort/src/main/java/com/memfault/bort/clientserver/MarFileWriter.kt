package com.memfault.bort.clientserver

import androidx.annotation.VisibleForTesting
import com.memfault.bort.BortJson
import com.memfault.bort.DeviceInfo
import com.memfault.bort.SOFTWARE_TYPE
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.addZipEntry
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.settings.HttpApiSettings
import com.memfault.bort.settings.ZipCompressionLevel
import com.memfault.bort.zip.openZipFile
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

class MarFileWriter @Inject constructor(
    private val httpSettings: HttpApiSettings,
    private val temporaryFileFactory: TemporaryFileFactory,
    private val zipCompressionLevel: ZipCompressionLevel,
) {
    fun createMarFile(
        file: File?,
        manifest: MarManifest,
    ): Result<MarFileWithManifest> = runCatching {
        val marFile = createMarFile(manifest.type).useFile { marFile, preventDeletion ->
            writeMarFile(
                marFile = marFile,
                manifest = manifest,
                inputFile = file,
                compressionLevel = zipCompressionLevel(),
            )
            preventDeletion()
            marFile
        }

        return Result.success(MarFileWithManifest(marFile, manifest))
    }

    fun batchMarFiles(inputMarFiles: List<File>): List<File> {
        if (inputMarFiles.isEmpty()) return emptyList()
        val batches = splitFilesIntoBatchesForMaxSize(inputMarFiles)
        val batchedFiles = batches.map { batch ->
            createMarFile(type = BATCHED_MAR_TYPE).useFile { file, preventDeletion ->
                writeBatchedMarFile(
                    marFile = file,
                    inputFiles = batch,
                    compressionLevel = zipCompressionLevel(),
                )
                preventDeletion()
                file
            }
        }
        return batchedFiles
    }

    @VisibleForTesting
    internal fun splitFilesIntoBatchesForMaxSize(inputMarFiles: List<File>): List<List<File>> {
        // We can't know for sure exactly how much storage will be used when adding a file to a compressed zip - however
        // we do know the compressed size of each individual input mar file; it is unlikely to take more space in the
        // merged mar file (assuming the same compression rate is used).
        // To be safe, we add a tolerance to the size limit.
        val maxMarSize = (httpSettings.maxMarFileSizeBytes - MAR_SIZE_TOLERANCE_BYTES).coerceAtLeast(0)
        return inputMarFiles.chunkByElementSize(maxMarSize.toLong()) { file -> file.length() }
    }

    private fun createMarFile(type: String) =
        temporaryFileFactory.createTemporaryFile(
            prefix = "${type}_".padEnd(length = 3, padChar = '_'),
            suffix = ".$MAR_EXTENSION",
        )

    companion object {
        private const val MANIFEST_NAME = "manifest.json"
        const val MAR_EXTENSION = "mar"
        const val BATCHED_MAR_TYPE = "batched"

        /** Because we can't exactly predict how much storage will be used adding to a zip file, add some margin */
        internal const val MAR_SIZE_TOLERANCE_BYTES = 1_000_000

        /**
         * Split the list into several sublists, each containing a maximum total element value of [maxPerChunk], as
         * defined by [elementSize].
         *
         * If a single element is over the limit, it is still added (as its own chunk).
         */
        fun <E> List<E>.chunkByElementSize(maxPerChunk: Long, elementSize: (E) -> Long): List<List<E>> {
            val chunks = mutableListOf<List<E>>()
            var currentChunk = mutableListOf<E>()
            var currentChunkSize = 0L
            forEach { element ->
                val size = elementSize(element)
                if (currentChunkSize + size > maxPerChunk && !currentChunk.isEmpty()) {
                    chunks.add(currentChunk)
                    currentChunk = mutableListOf()
                    currentChunkSize = 0
                }
                currentChunk.add(element)
                currentChunkSize += size
            }
            chunks.add(currentChunk)
            return chunks
        }

        /**
         * Writes a Mar file with the given [inputFile] and [manifest]. Returns a [Result].
         *
         * If the [inputFile] is not null and does not exist on the the filesystem, throws a [FileMissingException].
         */
        @VisibleForTesting
        internal fun writeMarFile(
            marFile: File,
            manifest: MarManifest,
            inputFile: File?,
            compressionLevel: Int,
        ) {
            FileOutputStream(marFile).use { fileOut ->
                ZipOutputStream(fileOut).use { zipOut ->
                    zipOut.setLevel(compressionLevel)

                    val folderUuid = UUID.randomUUID().toString()
                    val dateString = manifest.collectionTime.timestamp.toString().replace(':', '-').replace('.', '-')
                    val folder = "${marFile.name}/$dateString-$folderUuid/"
                    zipOut.putNextEntry(ZipEntry(folder))
                    zipOut.closeEntry()

                    zipOut.putNextEntry(ZipEntry("$folder$MANIFEST_NAME"))
                    val metadataBytes = BortJson.encodeToString(manifest).encodeToByteArray()
                    zipOut.write(metadataBytes)
                    zipOut.closeEntry()

                    inputFile?.apply {
                        if (exists()) {
                            inputStream().use { input ->
                                zipOut.addZipEntry("$folder$name", input)
                            }
                        } else {
                            Reporting.report().event("mar.input_file.missing", countInReport = true, internal = true)
                                .add(absolutePath)
                            throw FileMissingException(absolutePath)
                        }
                    }
                }
            }
        }

        @VisibleForTesting
        internal fun writeBatchedMarFile(
            marFile: File,
            inputFiles: List<File>,
            compressionLevel: Int,
        ) {
            FileOutputStream(marFile).use { fileOut ->
                ZipOutputStream(fileOut).use { zipOut ->
                    zipOut.setLevel(compressionLevel)

                    inputFiles.forEach { inFile ->
                        openZipFile(inFile)?.use { zipIn ->
                            for (entry in zipIn.entries()) {
                                // The name includes the full directory structure: this includes the mar filename, so
                                // replace with the new mar filename.
                                val name = entry.name.replace(inFile.name, marFile.name)
                                zipOut.addZipEntry(name, zipIn.getInputStream(entry))
                            }
                        }
                        inFile.delete()
                    }
                }
            }
        }
    }
}

fun DeviceInfo.asDevice(projectKey: String) = MarDevice(
    projectKey = projectKey,
    hardwareVersion = hardwareVersion,
    softwareVersion = softwareVersion,
    softwareType = SOFTWARE_TYPE,
    deviceSerial = deviceSerial,
)

data class MarFileWithManifest(
    val marFile: File,
    val manifest: MarManifest,
)

class FileMissingException(path: String) : Exception("File $path is expected but missing!")
