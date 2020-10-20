package com.memfault.usagereporter

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.*
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import androidx.preference.PreferenceManager
import com.memfault.bort.shared.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
        sendReply(when (val db = getDropBoxManager()) {
            null -> ErrorResponse("Failed to get DropBoxManager")
            else -> try {
                DropBoxGetNextEntryResponse(findFirstMatchingEntry(db, request.lastTimeMillis))
            } catch (e: Exception) {
                ErrorResponse.fromException(e)
            }
        })
    }

    private fun findFirstMatchingEntry(db: DropBoxManager, lastTimeMillis: Long): DropBoxManager.Entry? {
        val includedTagsSet = filterSettingsProvider.includedTags
        var cursorTimeMillis = lastTimeMillis
        while (true) {
            val entry = db.getNextEntry(null, cursorTimeMillis)
            if (entry == null) return null
            if (entry.tag in includedTagsSet) return entry
            cursorTimeMillis = entry.timeMillis
        }
    }
}

class RunCommandMessageHandler(
    val enqueueCommand: (List<String>, CommandRunnerOptions) -> CommandRunner
) {
    private fun <C : Command> enqueueCommandRequest(request: RunCommandRequest<C>) =
        enqueueCommand(request.command.toList(), request.runnerOptions)

    fun handleBatteryStatsRequest(request: BatteryStatsRequest, sendReply: SendReply) =
        enqueueCommandRequest(request).also {
            sendReply(BatteryStatsResponse())
        }
}

// android.os.Message cannot be instantiated in unit tests. The odd code splitting & injecting is
// done to keep the toMessage() and fromMessage() out of the main body of code.
class ReporterServiceMessageHandler(
    private val runCommandMessageHandler: RunCommandMessageHandler,
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
        Logger.v("Got serviceMessage: ${serviceMessage}")

        val sendReply: SendReply = {
            try {
                getSendReply(message)(it)
            } catch (e: RemoteException) {
                Logger.e("Failed to send reply: $it", e)
            }
        }
        when (serviceMessage) {
            is BatteryStatsRequest ->
                runCommandMessageHandler.handleBatteryStatsRequest(serviceMessage, sendReply)
            is DropBoxSetTagFilterRequest ->
                dropBoxMessageHandler.handleSetTagFilterMessage(serviceMessage, sendReply)
            is DropBoxGetNextEntryRequest ->
                dropBoxMessageHandler.handleGetNextEntryRequest(serviceMessage, sendReply)
            is VersionRequest -> handleVersionRequest(sendReply)
            null -> sendReply(ErrorResponse("Unknown Message: ${message}")).also {
                Logger.e("Unknown Message: ${message}")
            }
            else -> sendReply(ErrorResponse("Cannot handle: ${serviceMessage}")).also {
                Logger.e("Cannot handle: ${serviceMessage}")
            }
        }

        return true
    }

    private fun handleVersionRequest(sendReply: (reply: ServiceMessage) -> Unit) {
        sendReply(VersionResponse(REPORTER_SERVICE_VERSION))
    }
}

class ReporterService : Service() {
    private var messenger: Messenger? = null
    private lateinit var commandExecutor: ExecutorService
    private lateinit var handlerThread: HandlerThread
    private lateinit var messageHandler: ReporterServiceMessageHandler

    override fun onCreate() {
        Logger.d("Creating ReporterService")
        handlerThread = HandlerThread("ReporterService", THREAD_PRIORITY_BACKGROUND).apply {
            start()
        }
        commandExecutor = Executors.newFixedThreadPool(COMMAND_EXECUTOR_MAX_THREADS)

        messageHandler = ReporterServiceMessageHandler(
            runCommandMessageHandler = RunCommandMessageHandler(::enqueueCommand),
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

    private fun enqueueCommand(command: List<String>, runnerOptions: CommandRunnerOptions): CommandRunner =
        CommandRunner(command, runnerOptions).also {
            commandExecutor.submit(it)
            Logger.v("Enqueued command ${it.options.id} to $commandExecutor")
        }

    private fun getSendReply(message: Message): SendReply {
        return { serviceMessage: ServiceMessage ->
                message.replyTo.send(serviceMessage.toMessage())
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
