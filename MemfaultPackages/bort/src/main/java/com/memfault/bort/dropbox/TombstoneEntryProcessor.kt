package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.BootRelativeTimeProvider
import com.memfault.bort.FileUploaderSimpleFactory
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.TombstoneFileUploadMetadata
import com.memfault.bort.parsers.InvalidTombstoneException
import com.memfault.bort.parsers.TombstoneParser
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.listify
import com.memfault.bort.toAbsoluteTime

class TombstoneEntryProcessor(
    private val tempFileFactory: TemporaryFileFactory,
    private val fileUploaderSimpleFactory: FileUploaderSimpleFactory,
    private val bootRelativeTimeProvider: BootRelativeTimeProvider,
    private val packageManagerClient: PackageManagerClient,
) : EntryProcessor() {
    override val tags = listOf("SYSTEM_TOMBSTONE")

    override suspend fun process(entry: DropBoxManager.Entry) {
        tempFileFactory.createTemporaryFile("tombstone", ".txt").useFile { tempFile ->
            // Call fstat() before entry.inputStream.use() because it will invalidate the file descriptor!
            val fileTime = entry.fstat()?.st_mtime?.toAbsoluteTime()

            tempFile.outputStream().use { outStream ->
                entry.inputStream.use {
                    inStream ->
                    inStream.copyTo(outStream)
                }
            }

            val tombstone = try {
                tempFile.inputStream().use {
                    TombstoneParser(it).parse()
                }
            } catch (e: InvalidTombstoneException) {
                Logger.e("Tombstone failed to parse", e)
                null
            }

            val packageInfo = tombstone?.processName?.let {
                packageManagerClient.findPackagesByProcessName(it)
            }
            val packages = packageInfo?.toUploaderPackage().listify().also {
                if (it.isEmpty()) Logger.e(
                    "Missing package info: processName=${tombstone?.processName}, packageInfo=$packageInfo"
                )
            }

            fileUploaderSimpleFactory().upload(
                tempFile,
                TombstoneFileUploadMetadata(
                    tag = entry.tag,
                    fileTime = fileTime,
                    entryTime = entry.timeMillis.toAbsoluteTime(),
                    packages = packages,
                    collectionTime = bootRelativeTimeProvider.now()
                )
            )
        }
    }
}
