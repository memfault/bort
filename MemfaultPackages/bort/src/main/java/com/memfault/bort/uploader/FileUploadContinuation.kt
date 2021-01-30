package com.memfault.bort.uploader

import android.content.Context
import com.memfault.bort.BugReportRequestStatus
import com.memfault.bort.broadcastReply
import com.memfault.bort.shared.BugReportRequest
import kotlinx.serialization.Serializable

@Serializable
sealed class FileUploadContinuation {
    abstract fun success(context: Context)
    abstract fun failure(context: Context)
}

@Serializable
data class BugReportFileUploadContinuation(
    val request: BugReportRequest,
) : FileUploadContinuation() {
    override fun success(context: Context) =
        request.broadcastReply(context, BugReportRequestStatus.OK_UPLOAD_COMPLETED)
    override fun failure(context: Context) =
        request.broadcastReply(context, BugReportRequestStatus.ERROR_UPLOAD_FAILED)
}
