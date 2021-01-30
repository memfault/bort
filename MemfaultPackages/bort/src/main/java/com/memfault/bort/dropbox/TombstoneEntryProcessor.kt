package com.memfault.bort.dropbox

import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.FileUploadPayload
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.TimezoneWithId
import com.memfault.bort.TombstoneFileUploadMetadata
import com.memfault.bort.parsers.NativeBacktraceParser
import com.memfault.bort.parsers.TombstoneParser
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BootRelativeTime
import com.memfault.bort.time.BootRelativeTimeProvider
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueFileUpload
import java.io.File
import java.io.InputStream

class TombstoneEntryProcessor(
    tempFileFactory: TemporaryFileFactory,
    enqueueFileUpload: EnqueueFileUpload,
    bootRelativeTimeProvider: BootRelativeTimeProvider,
    deviceInfoProvider: DeviceInfoProvider,
    private val packageManagerClient: PackageManagerClient,
    tokenBucketStore: TokenBucketStore,
) : UploadingEntryProcessor(
    tempFileFactory,
    enqueueFileUpload,
    bootRelativeTimeProvider,
    deviceInfoProvider,
    tokenBucketStore,
) {
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
        timezone = TimezoneWithId.deviceDefault,
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

    private suspend fun getPackages(tempFile: File): List<FileUploadPayload.Package> {
        val processName = findProcessName(tempFile) ?: return emptyList<FileUploadPayload.Package>().also {
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
