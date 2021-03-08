package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.FileUploadPayload
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.TimezoneWithId
import com.memfault.bort.TombstoneFileUploadMetadata
import com.memfault.bort.parsers.NativeBacktraceParser
import com.memfault.bort.parsers.TombstoneParser
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BootRelativeTime
import java.io.File
import java.io.InputStream

class TombstoneUploadingEntryProcessorDelegate(
    private val packageManagerClient: PackageManagerClient,
) : UploadingEntryProcessorDelegate {
    override val tags = listOf("SYSTEM_TOMBSTONE")

    override val debugTag: String
        get() = "UPLOAD_TOMBSTONE"

    override suspend fun createMetadata(
        entryInfo: EntryInfo,
        tag: String,
        fileTime: AbsoluteTime?,
        entryTime: AbsoluteTime,
        collectionTime: BootRelativeTime
    ) = TombstoneFileUploadMetadata(
        tag = tag,
        fileTime = fileTime,
        entryTime = entryTime,
        packages = entryInfo.packages,
        collectionTime = collectionTime,
        timezone = TimezoneWithId.deviceDefault,
    )

    override suspend fun getEntryInfo(entry: DropBoxManager.Entry, entryFile: File): EntryInfo {
        val processName = findProcessName(entryFile)
        return EntryInfo(entry.tag, processName, getPackages(processName))
    }

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

    private suspend fun getPackages(processName: String?): List<FileUploadPayload.Package> {
        processName ?: return emptyList<FileUploadPayload.Package>().also {
            Logger.e("Tombstone failed to parse")
        }

        val packageInfo = packageManagerClient.findPackagesByProcessName(processName)
        val uploaderPackage =
            packageInfo?.toUploaderPackage() ?: return emptyList<FileUploadPayload.Package>().also {
                Logger.e("Failed to resolve package: processName=$processName packageInfo=$packageInfo")
            }
        return listOf(uploaderPackage)
    }
}
