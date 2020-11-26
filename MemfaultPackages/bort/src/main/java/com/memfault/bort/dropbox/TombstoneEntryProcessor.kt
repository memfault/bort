package com.memfault.bort.dropbox

import com.memfault.bort.AbsoluteTime
import com.memfault.bort.BootRelativeTime
import com.memfault.bort.BootRelativeTimeProvider
import com.memfault.bort.FileUploadMetadata
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.TombstoneFileUploadMetadata
import com.memfault.bort.parsers.NativeBacktraceParser
import com.memfault.bort.parsers.TombstoneParser
import com.memfault.bort.shared.Logger
import java.io.File
import java.io.InputStream

class TombstoneEntryProcessor(
    tempFileFactory: TemporaryFileFactory,
    enqueueFileUpload: EnqueueFileUpload,
    bootRelativeTimeProvider: BootRelativeTimeProvider,
    private val packageManagerClient: PackageManagerClient,
) : UploadingEntryProcessor(tempFileFactory, enqueueFileUpload, bootRelativeTimeProvider) {
    override val tags = listOf("SYSTEM_TOMBSTONE")

    override val debugTag: String
        get() = "UPLOAD_TOMBSTONE"

    override suspend fun createMetadata(
        tempFile: File,
        tag: String,
        fileTime: AbsoluteTime?,
        entryTime: AbsoluteTime,
        collectionTime: BootRelativeTime
    ) = TombstoneFileUploadMetadata(
        tag = tag,
        fileTime = fileTime,
        entryTime = entryTime,
        packages = getPackages(tempFile),
        collectionTime = collectionTime,
    )

    private fun findProcessName(tempFile: File) =
        listOf(
            { it: InputStream -> TombstoneParser(it).parse().processName },
            { it: InputStream -> NativeBacktraceParser(it).parse().processes[0].cmdLine }
        ).asSequence().map { parse ->
            try {
                tempFile.inputStream().use {
                    parse(it)
                }
            } catch (e: Exception) {
                null
            }
        }.filterNotNull().firstOrNull()

    private suspend fun getPackages(tempFile: File): List<FileUploadMetadata.Package> {
        val processName = findProcessName(tempFile) ?: return emptyList<FileUploadMetadata.Package>().also {
            Logger.e("Tombstone failed to parse")
        }

        val packageInfo = packageManagerClient.findPackagesByProcessName(processName)
        val uploaderPackage =
            packageInfo?.toUploaderPackage() ?: return emptyList<FileUploadMetadata.Package>().also {
                Logger.e("Failed to resolve package: processName=$processName packageInfo=$packageInfo")
            }
        return listOf(uploaderPackage)
    }
}
