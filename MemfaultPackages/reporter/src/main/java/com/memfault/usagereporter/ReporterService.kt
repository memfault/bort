package com.memfault.usagereporter

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.DropBoxManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import android.os.RemoteException
import androidx.preference.PreferenceManager
import com.memfault.bort.shared.CommandRunnerOptions
import com.memfault.bort.shared.DropBoxGetNextEntryRequest
import com.memfault.bort.shared.DropBoxGetNextEntryResponse
import com.memfault.bort.shared.DropBoxSetTagFilterRequest
import com.memfault.bort.shared.DropBoxSetTagFilterResponse
import com.memfault.bort.shared.ErrorResponse
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider
import com.memfault.bort.shared.REPORTER_SERVICE_VERSION
import com.memfault.bort.shared.ReporterServiceMessage
import com.memfault.bort.shared.RunCommandContinue
import com.memfault.bort.shared.RunCommandRequest
import com.memfault.bort.shared.RunCommandResponse
import com.memfault.bort.shared.ServiceMessage
import com.memfault.bort.shared.UnknownMessageException
import com.memfault.bort.shared.VersionRequest
import com.memfault.bort.shared.VersionResponse
import java.util.concurrent.TimeUnit

private const val COMMAND_EXECUTOR_MAX_THREADS = 2
private const val COMMAND_EXECUTOR_TERMINATION_WAIT_SECS: Long = 30

typealias SendReply = (reply: ServiceMessage) -> Unit

interface DropBoxFilterSettingsProvider {
    var includedTags: Set<String>
}

class DropBoxMessageHandler(
    private val getDropBoxManager: () -> DropBoxManager?,
    private val filterSettingsProvider: DropBoxFilterSettingsProvider
) {
    fun handleSetTagFilterMessage(message: DropBoxSetTagFilterRequest, sendReply: SendReply) {
        filterSettingsProvider.includedTags = message.includedTags.toSet()
        sendReply(DropBoxSetTagFilterResponse())
    }

    fun handleGetNextEntryRequest(request: DropBoxGetNextEntryRequest, sendReply: SendReply) {
        val db = getDropBoxManager() ?: return sendReply(ErrorResponse("Failed to get DropBoxManager"))

        try {
            findFirstMatchingEntry(db, request.lastTimeMillis).use {
                sendReply(DropBoxGetNextEntryResponse(it))
            }
        } catch (e: Exception) {
            sendReply(ErrorResponse.fromException(e))
        }
    }

    private fun findFirstMatchingEntry(db: DropBoxManager, lastTimeMillis: Long): DropBoxManager.Entry? {
        val includedTagsSet = filterSettingsProvider.includedTags
        var cursorTimeMillis = lastTimeMillis
        while (true) {
            val entry = db.getNextEntry(null, cursorTimeMillis)
            if (entry == null) return null
            if (entry.tag in includedTagsSet) return entry
            entry.close()
            cursorTimeMillis = entry.timeMillis
        }
    }
}

// android.os.Message cannot be instantiated in unit tests. The odd code splitting & injecting is
// done to keep the toMessage() and fromMessage() out of the main body of code.
class ReporterServiceMessageHandler(
    private val enqueueCommand: (List<String>, CommandRunnerOptions, CommandRunnerReportResult) -> CommandRunner,
    private val dropBoxMessageHandler: DropBoxMessageHandler,
    private val serviceMessageFromMessage: (message: Message) -> ReporterServiceMessage,
    private val getSendReply: (message: Message) -> SendReply
) : Handler.Callback {
    override fun handleMessage(message: Message): Boolean {
        val serviceMessage = try {
            serviceMessageFromMessage(message)
        } catch (e: UnknownMessageException) {
            null
        }
        return handleServiceMessage(serviceMessage, message)
    }

    internal fun handleServiceMessage(
        serviceMessage: ReporterServiceMessage?,
        message: Message
    ): Boolean {
        Logger.v("Got serviceMessage: $serviceMessage")

        // Immediately get the replyTo, because the message might have been recycled by the time the lambda gets called!
        val unsafeSendReply = getSendReply(message)

        val sendReply: SendReply = {
            try {
                unsafeSendReply(it)
            } catch (e: RemoteException) {
                Logger.e("Failed to send reply: $it", e)
            }
        }
        when (serviceMessage) {
            is RunCommandRequest<*> -> handleRunCommandRequest(serviceMessage, sendReply)
            is DropBoxSetTagFilterRequest ->
                dropBoxMessageHandler.handleSetTagFilterMessage(serviceMessage, sendReply)
            is DropBoxGetNextEntryRequest ->
                dropBoxMessageHandler.handleGetNextEntryRequest(serviceMessage, sendReply)
            is VersionRequest -> handleVersionRequest(sendReply)
            null -> sendReply(ErrorResponse("Unknown Message: $message")).also {
                Logger.e("Unknown Message: $message")
            }
            else -> sendReply(ErrorResponse("Cannot handle: $serviceMessage")).also {
                Logger.e("Cannot handle: $serviceMessage")
            }
        }

        return true
    }

    private fun handleVersionRequest(sendReply: (reply: ServiceMessage) -> Unit) {
        sendReply(VersionResponse(REPORTER_SERVICE_VERSION))
    }

    private fun handleRunCommandRequest(request: RunCommandRequest<*>, sendReply: SendReply) {
        val reportResult: CommandRunnerReportResult = { exitCode, didTimeout ->
            sendReply(RunCommandResponse(exitCode, didTimeout))
        }
        enqueueCommand(request.command.toList(), request.runnerOptions, reportResult)
        sendReply(RunCommandContinue())
    }
}

class ReporterService : Service() {
    private var messenger: Messenger? = null
    private lateinit var commandExecutor: TimeoutThreadPoolExecutor
    private lateinit var handlerThread: HandlerThread
    private lateinit var messageHandler: ReporterServiceMessageHandler

    override fun onCreate() {
        Logger.d("Creating ReporterService")
        handlerThread = HandlerThread("ReporterService", THREAD_PRIORITY_BACKGROUND).apply {
            start()
        }
        commandExecutor = TimeoutThreadPoolExecutor(COMMAND_EXECUTOR_MAX_THREADS)

        messageHandler = ReporterServiceMessageHandler(
            enqueueCommand = ::enqueueCommand,
            dropBoxMessageHandler = DropBoxMessageHandler(
                getDropBoxManager = ::getDropBoxManager,
                filterSettingsProvider = RealDropBoxFilterSettingsProvider(
                    PreferenceManager.getDefaultSharedPreferences(this)
                )
            ),
            serviceMessageFromMessage = ReporterServiceMessage.Companion::fromMessage,
            getSendReply = ::getSendReply
        )
    }

    override fun onDestroy() {
        Logger.d("Destroying ReporterService")

        handlerThread.quitSafely()
        Logger.v("handlerThread quit safely!")

        commandExecutor.shutdown()
        val timedOut = !commandExecutor.awaitTermination(
            COMMAND_EXECUTOR_TERMINATION_WAIT_SECS,
            TimeUnit.SECONDS
        )
        Logger.v("commandExecutor shut down! (timedOut=$timedOut)")

        Logger.test("Destroyed ReporterService")
    }

    private fun enqueueCommand(
        command: List<String>,
        runnerOptions: CommandRunnerOptions,
        reportResult: CommandRunnerReportResult
    ): CommandRunner =
        CommandRunner(command, runnerOptions, reportResult).also {
            commandExecutor.submitWithTimeout(it, runnerOptions.timeout)
            Logger.v("Enqueued command ${it.options.id} to $commandExecutor")
        }

    private fun getSendReply(message: Message): SendReply {
        // Pull out the replyTo, because the message might have been recycled by the time the lambda gets called!
        val replyTo = message.replyTo
        return { serviceMessage: ServiceMessage ->
            replyTo.send(serviceMessage.toMessage())
        }
    }

    private fun getDropBoxManager(): DropBoxManager? =
        this.getSystemService(DROPBOX_SERVICE) as DropBoxManager?

    override fun onBind(intent: Intent): IBinder? {
        Logger.d("ReporterService: onBind: $intent")
        return Messenger(Handler(handlerThread.looper, messageHandler)).also {
            messenger = it
        }.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Logger.d("ReporterService: onUnbind: $intent")
        messenger = null
        return false
    }
}

class RealDropBoxFilterSettingsProvider(
    sharedPreferences: SharedPreferences
) : DropBoxFilterSettingsProvider, PreferenceKeyProvider<Set<String>>(
    sharedPreferences = sharedPreferences,
    defaultValue = emptySet(),
    preferenceKey = PREFERENCE_DROPBOX_INCLUDED_ENTRY_TAGS
) {
    override var includedTags
        get() = super.getValue()
        set(value) = super.setValue(value)
}
