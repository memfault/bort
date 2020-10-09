package com.memfault.bort.shared

import android.os.Bundle
import android.os.DropBoxManager
import android.os.Message
import java.lang.Exception

class UnknownMessageException(message: String) : Exception(message)

// Generic responses:
val ERROR_RSP = -1

// DropBox related messages:
val DROPBOX_SET_TAG_FILTER_REQ: Int = 100
val DROPBOX_SET_TAG_FILTER_RSP: Int = 101

val DROPBOX_GET_NEXT_ENTRY_REQ: Int = 102
val DROPBOX_GET_NEXT_ENTRY_RSP: Int = 103

abstract class ReporterServiceMessage : ServiceMessage {
    abstract val messageId: Int

    abstract fun toBundle(): Bundle

    override fun toMessage(messageFactory: () -> Message) = messageFactory().apply {
        what = messageId
        data = toBundle()
    }

    companion object {
        fun fromMessage(message: Message): ReporterServiceMessage =
            when (message.what) {
                DROPBOX_SET_TAG_FILTER_REQ -> SetTagFilterRequest.fromBundle(message.data)
                DROPBOX_SET_TAG_FILTER_RSP -> SetTagFilterResponse()

                DROPBOX_GET_NEXT_ENTRY_REQ -> GetNextEntryRequest.fromBundle(message.data)
                DROPBOX_GET_NEXT_ENTRY_RSP -> GetNextEntryResponse.fromBundle(message.data)

                ERROR_RSP -> ErrorResponse.fromBundle(message.data)
                else -> throw UnknownMessageException("Unknown ReporterServiceMessage ID: ${message.what}")
            }
    }
}

private const val INCLUDED_TAGS = "INCLUDED_TAGS"

data class SetTagFilterRequest(val includedTags: List<String>): ReporterServiceMessage() {
    override val messageId: Int = DROPBOX_SET_TAG_FILTER_REQ
    override fun toBundle(): Bundle = Bundle().apply {
        putStringArray(INCLUDED_TAGS, includedTags.toTypedArray())
    }

    companion object {
        fun fromBundle(bundle: Bundle) =
            SetTagFilterRequest(
                bundle.getStringArray(INCLUDED_TAGS)?.toList() ?: emptyList()
            )
    }
}

data class SetTagFilterResponse(var unused: Int = 0): ReporterServiceMessage() {
    override val messageId: Int = DROPBOX_SET_TAG_FILTER_RSP
    override fun toBundle(): Bundle = Bundle()
}

private const val LAST = "LAST"

data class GetNextEntryRequest(val lastTimeMillis: Long): ReporterServiceMessage() {
    override val messageId: Int = DROPBOX_GET_NEXT_ENTRY_REQ
    override fun toBundle(): Bundle = Bundle().apply {
        putLong(LAST, lastTimeMillis)
    }

    companion object {
        fun fromBundle(bundle: Bundle) = GetNextEntryRequest(bundle.getLong(LAST))
    }
}

private const val ENTRY = "ENTRY"

data class GetNextEntryResponse(val entry: DropBoxManager.Entry?): ReporterServiceMessage() {
    override val messageId: Int = DROPBOX_GET_NEXT_ENTRY_RSP
    override fun toBundle(): Bundle = Bundle().apply {
        putParcelable(ENTRY, entry)
    }

    companion object {
        fun fromBundle(bundle: Bundle) =
            GetNextEntryResponse(bundle.getParcelable(ENTRY))
    }
}

private const val ERROR_MESSAGE = "MSG"

data class ErrorResponse(val error: String?): ReporterServiceMessage() {
    override val messageId: Int = ERROR_RSP
    override fun toBundle(): Bundle = Bundle().apply {
        putString(ERROR_MESSAGE, error)
    }

    companion object {
        fun fromBundle(bundle: Bundle) = ErrorResponse(bundle.getString(ERROR_MESSAGE))

        fun fromException(e: Exception) = ErrorResponse(e.toString())
    }
}
