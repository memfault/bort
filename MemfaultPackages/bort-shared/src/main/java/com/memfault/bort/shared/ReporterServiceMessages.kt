package com.memfault.bort.shared

import android.os.Bundle
import android.os.DropBoxManager
import android.os.Message
import android.os.ParcelFileDescriptor

class UnknownMessageException(message: String) : Exception(message)
class ErrorResponseException(message: String) : Exception(message)
class UnexpectedResponseException(message: String) : Exception(message)

val REPORTER_SERVICE_VERSION: Int = 5

// Generic responses:
val ERROR_RSP = -1

// General messages:
val VERSION_REQ: Int = 1
val VERSION_RSP: Int = 2

// Log level messages
val LOG_LEVEL_SET_REQ: Int = 3
val LOG_LEVEL_SET_RSP: Int = 4

// DropBox related messages:
val DROPBOX_SET_TAG_FILTER_REQ: Int = 100
val DROPBOX_SET_TAG_FILTER_RSP: Int = 101

val DROPBOX_GET_NEXT_ENTRY_REQ: Int = 102
val DROPBOX_GET_NEXT_ENTRY_RSP: Int = 103

// Command Runner messages:
val RUN_COMMAND_BATTERYSTATS_REQ: Int = 500
val RUN_COMMAND_LOGCAT_REQ: Int = 501
val RUN_COMMAND_SLEEP_REQ: Int = 502
val RUN_COMMAND_PACKAGE_MANAGER_REQ: Int = 503
val RUN_COMMAND_CONT: Int = 600
val RUN_COMMAND_RSP: Int = 601

// Client/server file upload messages:
val SEND_FILE_TO_SERVER_REQ: Int = 700
val SEND_FILE_TO_SERVER_RSP: Int = 701

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

                LOG_LEVEL_SET_REQ -> SetLogLevelRequest.fromBundle(message.data)
                LOG_LEVEL_SET_RSP -> SetLogLevelResponse

                DROPBOX_SET_TAG_FILTER_REQ -> DropBoxSetTagFilterRequest.fromBundle(message.data)
                DROPBOX_SET_TAG_FILTER_RSP -> DropBoxSetTagFilterResponse()

                DROPBOX_GET_NEXT_ENTRY_REQ -> DropBoxGetNextEntryRequest.fromBundle(message.data)
                DROPBOX_GET_NEXT_ENTRY_RSP -> DropBoxGetNextEntryResponse.fromBundle(message.data)

                RUN_COMMAND_BATTERYSTATS_REQ -> BatteryStatsRequest.fromBundle(message.data)
                RUN_COMMAND_LOGCAT_REQ -> LogcatRequest.fromBundle(message.data)
                RUN_COMMAND_SLEEP_REQ -> SleepRequest.fromBundle(message.data)
                RUN_COMMAND_PACKAGE_MANAGER_REQ -> PackageManagerRequest.fromBundle(message.data)
                RUN_COMMAND_CONT -> RunCommandContinue()
                RUN_COMMAND_RSP -> RunCommandResponse.fromBundle(message.data)

                SEND_FILE_TO_SERVER_REQ -> ServerSendFileRequest.fromBundle(message.data)
                SEND_FILE_TO_SERVER_RSP -> ServerSendFileResponse

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

private const val LOG_LEVEL = "LOG_LEVEL"

data class SetLogLevelRequest(val level: LogLevel) : ReporterServiceMessage() {
    override val messageId: Int = LOG_LEVEL_SET_REQ
    override fun toBundle(): Bundle = Bundle().apply {
        putInt(LOG_LEVEL, level.level)
    }

    companion object {
        fun fromBundle(bundle: Bundle) = SetLogLevelRequest(
            LogLevel.fromInt(bundle.getInt(LOG_LEVEL)) ?: LogLevel.NONE
        )
    }
}

object SetLogLevelResponse : SimpleReporterServiceMessage(LOG_LEVEL_SET_RSP)

private const val INCLUDED_TAGS = "INCLUDED_TAGS"

data class DropBoxSetTagFilterRequest(val includedTags: List<String>) : ReporterServiceMessage() {
    override val messageId: Int = DROPBOX_SET_TAG_FILTER_REQ
    override fun toBundle(): Bundle = Bundle().apply {
        putStringArray(INCLUDED_TAGS, includedTags.toTypedArray())
    }

    companion object {
        fun fromBundle(bundle: Bundle) =
            DropBoxSetTagFilterRequest(
                bundle.getStringArray(INCLUDED_TAGS).listify()
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

abstract class RunCommandRequest<C : Command> : ReporterServiceMessage() {
    abstract val command: C
    abstract val runnerOptions: CommandRunnerOptions
    final override fun toBundle(): Bundle = Bundle().apply {
        putParcelable(COMMAND, command.toBundle())
        putParcelable(RUN_OPTS, runnerOptions.toBundle())
    }
}

/**
 * Message that is sent by the ReporterService after the RunCommandRequest command
 * has started executing and Bort can start reading from the read end of the pipe.
 */
class RunCommandContinue : SimpleReporterServiceMessage(RUN_COMMAND_CONT)

private const val EXIT_CODE = "EXIT_CODE"
private const val DID_TIMEOUT = "DID_TIMEOUT"

data class RunCommandResponse(val exitCode: Int?, val didTimeout: Boolean) : ReporterServiceMessage() {
    override val messageId: Int = RUN_COMMAND_RSP
    override fun toBundle(): Bundle = Bundle().apply {
        exitCode?.let { putInt(EXIT_CODE, it) }
        putBoolean(DID_TIMEOUT, didTimeout)
    }

    companion object {
        fun fromBundle(bundle: Bundle) = with(bundle) {
            RunCommandResponse(
                getIntOrNull(EXIT_CODE),
                getBoolean(DID_TIMEOUT)
            )
        }
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
    override val messageId: Int = RUN_COMMAND_BATTERYSTATS_REQ
    companion object {
        fun fromBundle(bundle: Bundle) = BatteryStatsRequest(
            bundle.getCommandRunnerCommand()?.let { BatteryStatsCommand.fromBundle(it) } ?: BatteryStatsCommand(),
            bundle.getCommandRunnerOptions()
        )
    }
}

data class LogcatRequest(
    override val command: LogcatCommand,
    override val runnerOptions: CommandRunnerOptions
) : RunCommandRequest<LogcatCommand>() {
    override val messageId: Int = RUN_COMMAND_LOGCAT_REQ
    companion object {
        fun fromBundle(bundle: Bundle) = LogcatRequest(
            bundle.getCommandRunnerCommand()?.let { LogcatCommand.fromBundle(it) } ?: LogcatCommand(),
            bundle.getCommandRunnerOptions()
        )
    }
}

data class SleepRequest(
    override val command: SleepCommand,
    override val runnerOptions: CommandRunnerOptions
) : RunCommandRequest<SleepCommand>() {
    override val messageId: Int = RUN_COMMAND_SLEEP_REQ
    companion object {
        fun fromBundle(bundle: Bundle) = SleepRequest(
            bundle.getCommandRunnerCommand()?.let { SleepCommand.fromBundle(it) } ?: SleepCommand(0),
            bundle.getCommandRunnerOptions()
        )
    }
}

data class PackageManagerRequest(
    override val command: PackageManagerCommand,
    override val runnerOptions: CommandRunnerOptions
) : RunCommandRequest<PackageManagerCommand>() {
    override val messageId: Int = RUN_COMMAND_PACKAGE_MANAGER_REQ
    companion object {
        fun fromBundle(bundle: Bundle) = PackageManagerRequest(
            bundle.getCommandRunnerCommand()?.let { PackageManagerCommand.fromBundle(it) } ?: PackageManagerCommand(),
            bundle.getCommandRunnerOptions()
        )
    }
}

private const val ERROR_MESSAGE = "MSG"

data class ErrorResponse(val error: String?) : ReporterServiceMessage() {
    override val messageId: Int = ERROR_RSP
    override fun toBundle(): Bundle = Bundle().apply {
        putString(ERROR_MESSAGE, error)
    }

    companion object {
        fun fromBundle(bundle: Bundle) = ErrorResponse(bundle.getString(ERROR_MESSAGE))

        fun fromException(e: Exception) = ErrorResponse(e.stackTraceToString())
    }
}

private const val FILE_DESCRIPTOR = "FILE_DESCRIPTOR"
private const val FILE_NAME = "FILE_NAME"

data class ServerSendFileRequest(
    val filename: String,
    val descriptor: ParcelFileDescriptor,
) : ReporterServiceMessage() {
    override val messageId: Int = SEND_FILE_TO_SERVER_REQ
    override fun toBundle(): Bundle = Bundle().apply {
        putString(FILE_NAME, filename)
        putParcelable(FILE_DESCRIPTOR, descriptor)
    }

    companion object {
        fun fromBundle(bundle: Bundle) =
            ServerSendFileRequest(
                bundle.getString(FILE_NAME)
                    ?: throw IllegalArgumentException("ServerSendFileRequest: missing $FILE_NAME"),
                bundle.getParcelable(FILE_DESCRIPTOR)
                    ?: throw IllegalArgumentException("ServerSendFileRequest: missing $FILE_DESCRIPTOR")
            )
    }
}

object ServerSendFileResponse : SimpleReporterServiceMessage(SEND_FILE_TO_SERVER_RSP)
