package com.memfault.bort

import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Retrofit

@Serializable
sealed class FileUploadMetadata {
    @Serializable
    data class Package(
        val id: String,

        @SerialName("version_code")
        val versionCode: Long,

        @SerialName("version_name")
        val versionName: String,

        @SerialName("user_id")
        val userId: Int,

        @SerialName("code_path")
        val codePath: String,
    )
}

@Serializable
@SerialName("bugreport")
class BugReportFileUploadMetadata : FileUploadMetadata()

@Serializable
sealed class DropBoxEntryFileUploadMetadata : FileUploadMetadata() {
    /**
     * The DropBoxManager tag associated with the uploaded file.
     */
    abstract val tag: String

    /**
     * File modification timestamp (Epoch/UTC) in milliseconds of the DropBox file on disk or null in case it could not
     * be retrieved.
     */
    @SerialName("file_time")
    abstract val fileTime: AbsoluteTime?

    /**
     * DropBox Entry timestamp (Epoch/UTC) in milliseconds.
     * The DropBoxManagerService backdates this time in case of a backwards time change, therefore, it's recommended to
     * use file_time_ms instead.
     */
    @SerialName("entry_time")
    abstract val entryTime: AbsoluteTime

    /**
     * Information on packages pertinent to the entry.
     */
    abstract val packages: List<Package>

    /**
     * The time when the DropBox Entry was collected.
     */
    @SerialName("collection_time")
    abstract val collectionTime: BootRelativeTime
}

@Serializable
@SerialName("tombstone")
class TombstoneFileUploadMetadata(
    override val tag: String,

    @SerialName("file_time")
    override val fileTime: AbsoluteTime?,

    @SerialName("entry_time")
    override val entryTime: AbsoluteTime,

    override val packages: List<Package>,

    @SerialName("collection_time")
    override val collectionTime: BootRelativeTime
) : DropBoxEntryFileUploadMetadata()

interface FileUploader {
    suspend fun upload(file: File, metadata: FileUploadMetadata): TaskResult
}

interface FileUploaderFactory {
    fun create(
        retrofit: Retrofit,
        projectApiKey: String
    ): FileUploader
}

typealias FileUploaderSimpleFactory = () -> FileUploader
