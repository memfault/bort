package com.memfault.bort.shared

import android.os.Bundle
import android.os.DropBoxManager
import android.os.Message

class UnknownMessageException(message: String) : Exception(message)

val REPORTER_SERVICE_VERSION: Int = 1

// Generic responses:
val ERROR_RSP = -1

// General messages:
val VERSION_REQ: Int = 1
val VERSION_RSP: Int = 2

// DropBox related messages:
val DROPBOX_SET_TAG_FILTER_REQ: Int = 100
val DROPBOX_SET_TAG_FILTER_RSP: Int = 101

val DROPBOX_GET_NEXT_ENTRY_REQ: Int = 102
val DROPBOX_GET_NEXT_ENTRY_RSP: Int = 103

// Batterystats related messages:
val BATTERYSTATS_REQ: Int = 200
val BATTERYSTATS_RSP: Int = 201

// Logcat related messages:
val LOGCAT_REQ: Int = 300
val LOGCAT_RSP: Int = 301

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
                VERSION_REQ -> VersionRequest()
                VERSION_RSP -> VersionResponse.fromBundle(message.data)

                DROPBOX_SET_TAG_FILTER_REQ -> DropBoxSetTagFilterRequest.fromBundle(message.data)
                DROPBOX_SET_TAG_FILTER_RSP -> DropBoxSetTagFilterResponse()

                DROPBOX_GET_NEXT_ENTRY_REQ -> DropBoxGetNextEntryRequest.fromBundle(message.data)
                DROPBOX_GET_NEXT_ENTRY_RSP -> DropBoxGetNextEntryResponse.fromBundle(message.data)

                BATTERYSTATS_REQ -> BatteryStatsRequest.fromBundle(message.data)
                BATTERYSTATS_RSP -> BatteryStatsResponse()

                LOGCAT_REQ -> LogcatRequest.fromBundle(message.data)
                LOGCAT_RSP -> LogcatResponse()

                ERROR_RSP -> ErrorResponse.fromBundle(message.data)
                else -> throw UnknownMessageException("Unknown ReporterServiceMessage ID: ${message.what}")
            }
    }
}

open class SimpleReporterServiceMessage(override val messageId: Int) : ReporterServiceMessage() {
    override fun toBundle(): Bundle = Bundle()

    override fun hashCode(): Int {
        return messageId
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return messageId == (other as SimpleReporterServiceMessage).messageId
    }

    override fun toString(): String =
        "${this::class.simpleName ?: this::class}(messageId=$messageId)"
}

private const val VERSION = "VERSION"

class VersionRequest : SimpleReporterServiceMessage(VERSION_REQ)
class VersionResponse(val version: Int) : ReporterServiceMessage() {
    override val messageId: Int = VERSION_RSP
    override fun toBundle(): Bundle = Bundle().apply {
        putInt(VERSION, version)
    }

    companion object {
        fun fromBundle(bundle: Bundle) = VersionResponse(bundle.getInt(VERSION))
    }
}

private const val INCLUDED_TAGS = "INCLUDED_TAGS"

data class DropBoxSetTagFilterRequest(val includedTags: List<String>) : ReporterServiceMessage() {
    override val messageId: Int = DROPBOX_SET_TAG_FILTER_REQ
    override fun toBundle(): Bundle = Bundle().apply {
        putStringArray(INCLUDED_TAGS, includedTags.toTypedArray())
    }

    companion object {
        fun fromBundle(bundle: Bundle) =
            DropBoxSetTagFilterRequest(
                bundle.getStringArray(INCLUDED_TAGS)?.toList() ?: emptyList()
            )
    }
}

class DropBoxSetTagFilterResponse : SimpleReporterServiceMessage(DROPBOX_SET_TAG_FILTER_RSP)

private const val LAST = "LAST"

data class DropBoxGetNextEntryRequest(val lastTimeMillis: Long) : ReporterServiceMessage() {
    override val messageId: Int = DROPBOX_GET_NEXT_ENTRY_REQ
    override fun toBundle(): Bundle = Bundle().apply {
        putLong(LAST, lastTimeMillis)
    }

    companion object {
        fun fromBundle(bundle: Bundle) = DropBoxGetNextEntryRequest(bundle.getLong(LAST))
    }
}

private const val ENTRY = "ENTRY"

data class DropBoxGetNextEntryResponse(val entry: DropBoxManager.Entry?) : ReporterServiceMessage() {
    override val messageId: Int = DROPBOX_GET_NEXT_ENTRY_RSP
    override fun toBundle(): Bundle = Bundle().apply {
        putParcelable(ENTRY, entry)
    }

    companion object {
        fun fromBundle(bundle: Bundle) =
            DropBoxGetNextEntryResponse(bundle.getParcelable(ENTRY))
    }
}

private const val COMMAND = "ARGS"
private const val RUN_OPTS = "RUN_OPTS"

abstract class RunCommandRequest<C : Command>() : ReporterServiceMessage() {
    abstract val command: C
    abstract val runnerOptions: CommandRunnerOptions
    final override fun toBundle(): Bundle = Bundle().apply {
        putParcelable(COMMAND, command.toBundle())
        putParcelable(RUN_OPTS, runnerOptions.toBundle())
    }
}

fun Bundle.getCommandRunnerOptions() =
    this.getParcelable<Bundle>(RUN_OPTS)?.let {
        CommandRunnerOptions.fromBundle(it)
    } ?: CommandRunnerOptions(null)

fun Bundle.getCommandRunnerCommand() =
    this.getParcelable<Bundle>(COMMAND)

data class BatteryStatsRequest(
    override val command: BatteryStatsCommand,
    override val runnerOptions: CommandRunnerOptions
) : RunCommandRequest<BatteryStatsCommand>() {
    override val messageId: Int = BATTERYSTATS_REQ
    companion object {
        fun fromBundle(bundle: Bundle) = BatteryStatsRequest(
            bundle.getCommandRunnerCommand()?.let { BatteryStatsCommand.fromBundle(it) } ?: BatteryStatsCommand(),
            bundle.getCommandRunnerOptions()
        )
    }
}

class BatteryStatsResponse : SimpleReporterServiceMessage(BATTERYSTATS_RSP)

data class LogcatRequest(
    override val command: LogcatCommand,
    override val runnerOptions: CommandRunnerOptions
) : RunCommandRequest<LogcatCommand>() {
    override val messageId: Int = LOGCAT_REQ
    companion object {
        fun fromBundle(bundle: Bundle) = LogcatRequest(
            bundle.getCommandRunnerCommand()?.let { LogcatCommand.fromBundle(it) } ?: LogcatCommand(),
            bundle.getCommandRunnerOptions()
        )
    }
}

class LogcatResponse : SimpleReporterServiceMessage(LOGCAT_RSP)

private const val ERROR_MESSAGE = "MSG"

data class ErrorResponse(val error: String?) : ReporterServiceMessage() {
    override val messageId: Int = ERROR_RSP
    override fun toBundle(): Bundle = Bundle().apply {
        putString(ERROR_MESSAGE, error)
    }

    companion object {
        fun fromBundle(bundle: Bundle) = ErrorResponse(bundle.getString(ERROR_MESSAGE))

        fun fromException(e: Exception) = ErrorResponse(e.toString())
    }
}
