package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.AndroidPackage
import com.memfault.bort.PackageManagerClient
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.parsers.NativeBacktraceParser
import com.memfault.bort.parsers.TombstoneParser
import com.memfault.bort.settings.DropboxScrubTombstones
import com.memfault.bort.settings.DropboxUseNativeCrashTombstones
import com.memfault.bort.settings.OperationalCrashesExclusions
import com.memfault.bort.shared.Logger
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.tokenbucket.Tombstone
import okhttp3.internal.indexOfNonWhitespace
import java.io.File
import java.io.InputStream
import javax.inject.Inject

class TombstoneUploadingEntryProcessorDelegate @Inject constructor(
    private val packageManagerClient: PackageManagerClient,
    @Tombstone private val tokenBucketStore: TokenBucketStore,
    private val tempFileFactory: TemporaryFileFactory,
    private val scrubTombstones: DropboxScrubTombstones,
    private val useNativeCrashTombstones: DropboxUseNativeCrashTombstones,
    private val operationalCrashesExclusions: OperationalCrashesExclusions,
) : UploadingEntryProcessorDelegate {
    override val tags: List<String>
        get() = if (useNativeCrashTombstones()) {
            listOf(
                "data_app_native_crash",
                "system_app_native_crash",
                "system_server_native_crash",
            )
        } else {
            listOf("SYSTEM_TOMBSTONE")
        }

    override val debugTag: String
        get() = "UPLOAD_TOMBSTONE"

    override val crashTag: String? = null

    override fun allowedByRateLimit(tokenBucketKey: String, tag: String): Boolean =
        tokenBucketStore.allowedByRateLimit(tokenBucketKey = tokenBucketKey, tag = tag)

    override suspend fun getEntryInfo(entry: DropBoxManager.Entry, entryFile: File): EntryInfo {
        val processName = findProcessName(entryFile)
        return EntryInfo(
            tokenBucketKey = entry.tag,
            packageName = processName,
            packages = getPackages(processName),
        )
    }

    override fun scrub(inputFile: File, tag: String): File {
        if (!scrubTombstones()) return inputFile
        tempFileFactory.createTemporaryFile(tag, ".txt").useFile { scrubbedFile, preventDeletion ->
            scrubbedFile.bufferedWriter().use { outStream ->
                inputFile.bufferedReader().use { inStream ->
                    var inMemoryBlock = false
                    inStream.forEachLine { line ->
                        if (line.isNotEmpty() && line.indexOfNonWhitespace(0) == 0) {
                            inMemoryBlock = line.startsWith("memory near ") || line.startsWith("code around ")
                        }
                        if (!inMemoryBlock) {
                            outStream.write(line)
                            outStream.newLine()
                        }
                    }
                }
            }
            inputFile.delete()
            preventDeletion()
            return scrubbedFile
        }
    }

    override fun isCrash(entry: DropBoxManager.Entry, entryFile: File): Boolean =
        entry.tag !in operationalCrashesExclusions()

    private fun findProcessName(tempFile: File) =
        listOf(
            { it: InputStream -> TombstoneParser(it).parse().processName },
            { it: InputStream -> NativeBacktraceParser(it).parse().processes[0].cmdLine },
        ).asSequence().map { parse ->
            try {
                tempFile.inputStream().use {
                    parse(it)
                }
            } catch (e: Exception) {
                null
            }
        }.filterNotNull().firstOrNull()

    private suspend fun getPackages(processName: String?): List<AndroidPackage> {
        processName ?: return emptyList<AndroidPackage>().also {
            Logger.e("Tombstone failed to parse")
        }

        val packages = packageManagerClient.getPackageManagerReport()
        val packageInfo = packages.findPackagesByProcessName(processName)
        val uploaderPackage =
            packageInfo?.toUploaderPackage() ?: return emptyList<AndroidPackage>().also {
                Logger.e("Failed to resolve package: processName=$processName packageInfo=$packageInfo")
            }
        return listOf(uploaderPackage)
    }
}
