package com.memfault.usagereporter

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.DropBoxManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import android.os.RemoteException
import com.memfault.bort.shared.CommandRunnerOptions
import com.memfault.bort.shared.ErrorResponse
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.REPORTER_SERVICE_VERSION
import com.memfault.bort.shared.ReporterServiceMessage
import com.memfault.bort.shared.RunCommandContinue
import com.memfault.bort.shared.RunCommandRequest
import com.memfault.bort.shared.RunCommandResponse
import com.memfault.bort.shared.ServerSendFileRequest
import com.memfault.bort.shared.ServerSendFileResponse
import com.memfault.bort.shared.ServiceMessage
import com.memfault.bort.shared.SetLogLevelRequest
import com.memfault.bort.shared.SetLogLevelResponse
import com.memfault.bort.shared.SetMetricCollectionIntervalRequest
import com.memfault.bort.shared.SetMetricCollectionIntervalResponse
import com.memfault.bort.shared.SetReporterSettingsRequest
import com.memfault.bort.shared.SetReporterSettingsResponse
import com.memfault.bort.shared.UnknownMessageException
import com.memfault.bort.shared.VersionRequest
import com.memfault.bort.shared.VersionResponse
import com.memfault.usagereporter.UsageReporter.Companion.b2bClientServer
import com.memfault.usagereporter.clientserver.B2BClientServer
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val COMMAND_EXECUTOR_MAX_THREADS = 2
private const val COMMAND_EXECUTOR_TERMINATION_WAIT_SECS: Long = 30

typealias SendReply = (reply: ServiceMessage) -> Unit

// android.os.Message cannot be instantiated in unit tests. The odd code splitting & injecting is
// done to keep the toMessage() and fromMessage() out of the main body of code.
class ReporterServiceMessageHandler(
    private val enqueueCommand: (List<String>, CommandRunnerOptions, CommandRunnerReportResult) -> CommandRunner,
    private val serviceMessageFromMessage: (message: Message) -> ReporterServiceMessage,
    private val setLogLevel: (logLevel: LogLevel) -> Unit,
    private val getSendReply: (message: Message) -> SendReply,
    private val b2BClientServer: B2BClientServer,
    private val reporterSettings: ReporterSettingsPreferenceProvider,
    private val createPipe: CreatePipe,
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
        message: Message,
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
            is SetLogLevelRequest -> handleSetLogLevelRequest(serviceMessage.level, sendReply)
            is VersionRequest -> handleVersionRequest(sendReply)
            is ServerSendFileRequest -> handleSendFileRequest(serviceMessage, sendReply)
            is SetMetricCollectionIntervalRequest -> handleSetMetricIntervalRequest(sendReply)
            is SetReporterSettingsRequest -> handleSettingsUpdate(serviceMessage, sendReply)
            null -> sendReply(ErrorResponse("Unknown Message: $message")).also {
                Logger.e("Unknown Message: $message")
            }
            else -> sendReply(ErrorResponse("Cannot handle: $serviceMessage")).also {
                Logger.e("Cannot handle: $serviceMessage")
            }
        }

        return true
    }

    private fun handleSettingsUpdate(
        message: SetReporterSettingsRequest,
        sendReply: (reply: ServiceMessage) -> Unit,
    ) {
        reporterSettings.set(message)
        // Take any actions required after settings have been updated here.
        sendReply(SetReporterSettingsResponse)
    }

    private fun handleSendFileRequest(
        message: ServerSendFileRequest,
        sendReply: (reply: ServiceMessage) -> Unit,
    ) {
        b2BClientServer.enqueueFile(message.dropboxTag, message.descriptor)
        sendReply(ServerSendFileResponse)
    }

    private fun handleVersionRequest(sendReply: (reply: ServiceMessage) -> Unit) {
        sendReply(VersionResponse(REPORTER_SERVICE_VERSION))
    }

    private fun handleSetLogLevelRequest(
        level: LogLevel,
        sendReply: (reply: ServiceMessage) -> Unit,
    ) {
        setLogLevel(level)
        sendReply(SetLogLevelResponse)
    }

    private fun handleSetMetricIntervalRequest(
        sendReply: (reply: ServiceMessage) -> Unit,
    ) {
        sendReply(SetMetricCollectionIntervalResponse)
    }

    private fun handleRunCommandRequest(request: RunCommandRequest<*>, sendReply: SendReply) {
        val reportResult: CommandRunnerReportResult = { exitCode, didTimeout ->
            sendReply(RunCommandResponse(exitCode, didTimeout))
        }
        val (readFd, writeFd) = createPipe()
        enqueueCommand(request.command.toList(), request.runnerOptions.copy(outFd = writeFd), reportResult)
        sendReply(RunCommandContinue(readFd))
        readFd.close()
    }
}

@AndroidEntryPoint
class ReporterService : Service() {

    @Inject lateinit var reporterSettingsPreferenceProvider: ReporterSettingsPreferenceProvider

    @Inject lateinit var logLevelPreferenceProvider: LogLevelPreferenceProvider

    private var messenger: Messenger? = null
    private lateinit var commandExecutor: TimeoutThreadPoolExecutor
    private lateinit var handlerThread: HandlerThread
    private lateinit var messageHandler: ReporterServiceMessageHandler

    override fun onCreate() {
        super.onCreate()
        Logger.d("Creating ReporterService")
        handlerThread = HandlerThread("ReporterService", THREAD_PRIORITY_BACKGROUND).apply {
            start()
        }
        commandExecutor = TimeoutThreadPoolExecutor(COMMAND_EXECUTOR_MAX_THREADS)

        messageHandler = ReporterServiceMessageHandler(
            enqueueCommand = ::enqueueCommand,
            serviceMessageFromMessage = ReporterServiceMessage.Companion::fromMessage,
            setLogLevel = { logLevel ->
                logLevelPreferenceProvider.setLogLevel(logLevel)
                Logger.updateMinLogcatLevel(logLevel)
                Logger.test("Reporter received a log level update $logLevel")
            },
            getSendReply = ::getSendReply,
            b2BClientServer = b2bClientServer,
            reporterSettings = reporterSettingsPreferenceProvider,
            createPipe = ParcelFileDescriptor::createPipe,
        )
    }

    override fun onDestroy() {
        Logger.d("Destroying ReporterService")

        Handler(handlerThread.looper).post {
            commandExecutor.shutdown()
        }
        handlerThread.quitSafely()
        Logger.v("handlerThread quit safely!")

        val timedOut = !commandExecutor.awaitTermination(
            COMMAND_EXECUTOR_TERMINATION_WAIT_SECS,
            TimeUnit.SECONDS,
        )
        Logger.v("commandExecutor shut down! (timedOut=$timedOut)")

        Logger.test("Destroyed ReporterService")
    }

    private fun enqueueCommand(
        command: List<String>,
        runnerOptions: CommandRunnerOptions,
        reportResult: CommandRunnerReportResult,
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

fun Context.getDropBoxManager(): DropBoxManager? =
    getSystemService(Service.DROPBOX_SERVICE) as DropBoxManager?
