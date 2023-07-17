package com.memfault.bort

import android.app.Application
import android.content.ComponentName
import android.os.DropBoxManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import android.os.ParcelFileDescriptor
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.runCatching
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.shared.APPLICATION_ID_MEMFAULT_USAGE_REPORTER
import com.memfault.bort.shared.BatteryStatsCommand
import com.memfault.bort.shared.BatteryStatsRequest
import com.memfault.bort.shared.DEFAULT_REPLY_TIMEOUT
import com.memfault.bort.shared.DropBoxGetNextEntryRequest
import com.memfault.bort.shared.DropBoxGetNextEntryResponse
import com.memfault.bort.shared.DropBoxSetTagFilterRequest
import com.memfault.bort.shared.DropBoxSetTagFilterResponse
import com.memfault.bort.shared.ErrorResponse
import com.memfault.bort.shared.ErrorResponseException
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.LogcatRequest
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.MINIMUM_VALID_VERSION
import com.memfault.bort.shared.MINIMUM_VALID_VERSION_FILE_UPLOAD_V2_REPORTER_SETTINGS
import com.memfault.bort.shared.MINIMUM_VALID_VERSION_LOG_LEVEL
import com.memfault.bort.shared.MINIMUM_VALID_VERSION_METRIC_COLLECTION
import com.memfault.bort.shared.PackageManagerCommand
import com.memfault.bort.shared.PackageManagerRequest
import com.memfault.bort.shared.REPORTER_SERVICE_QUALIFIED_NAME
import com.memfault.bort.shared.RealServiceMessageConnection
import com.memfault.bort.shared.ReporterServiceMessage
import com.memfault.bort.shared.ServerSendFileRequest
import com.memfault.bort.shared.ServerSendFileResponse
import com.memfault.bort.shared.ServiceMessage
import com.memfault.bort.shared.ServiceMessageConnection
import com.memfault.bort.shared.ServiceMessageReplyHandler
import com.memfault.bort.shared.SetLogLevelRequest
import com.memfault.bort.shared.SetLogLevelResponse
import com.memfault.bort.shared.SetMetricCollectionIntervalRequest
import com.memfault.bort.shared.SetMetricCollectionIntervalResponse
import com.memfault.bort.shared.SetReporterSettingsRequest
import com.memfault.bort.shared.SetReporterSettingsResponse
import com.memfault.bort.shared.SleepCommand
import com.memfault.bort.shared.SleepRequest
import com.memfault.bort.shared.UnexpectedResponseException
import com.memfault.bort.shared.VersionRequest
import com.memfault.bort.shared.VersionResponse
import com.memfault.bort.shared.result.StdResult
import com.memfault.bort.shared.result.failure
import com.memfault.bort.shared.result.mapCatching
import com.memfault.bort.shared.result.success
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration
import okhttp3.internal.closeQuietly

typealias ReporterServiceConnection = ServiceMessageConnection<ReporterServiceMessage>

interface ReporterServiceConnector {
    suspend fun <R> connect(block: suspend (getService: ServiceGetter<ReporterClient>) -> R): R
}

@ContributesBinding(SingletonComponent::class, boundType = ReporterServiceConnector::class)
class RealReporterServiceConnector @Inject constructor(
    application: Application,
    @Main val inboundLooper: Looper,
) : ReporterServiceConnector, ServiceConnector<ReporterClient>(
    application,
    ComponentName(
        APPLICATION_ID_MEMFAULT_USAGE_REPORTER,
        REPORTER_SERVICE_QUALIFIED_NAME
    )
) {
    override fun createServiceWithBinder(binder: IBinder): ReporterClient =
        ReporterClient(
            RealServiceMessageConnection(
                Messenger(binder), inboundLooper, ReporterServiceMessage.Companion::fromMessage
            ),
            CommandRunnerClient
        )
}

class ReporterClient(
    val connection: ReporterServiceConnection,
    val commandRunnerClientFactory: CommandRunnerClientFactory,
) {
    private var cachedVersion: Int? = null

    suspend fun getVersion(): Int? =
        cachedVersion ?: sendWithoutVersionCheck(VersionRequest()) { response: VersionResponse ->
            response.version
        }.fold({ it }, { null }).also { version ->
            cachedVersion = version
        }

    suspend fun setLogLevel(level: LogLevel): StdResult<Unit> =
        withVersion(context = "loglevel", minimumVersion = MINIMUM_VALID_VERSION_LOG_LEVEL) {
            send(SetLogLevelRequest(level)) { _: SetLogLevelResponse -> }
        }

    suspend fun dropBoxSetTagFilter(includedTags: List<String>): StdResult<Unit> =
        send(DropBoxSetTagFilterRequest(includedTags)) { _: DropBoxSetTagFilterResponse -> Result.success(Unit) }

    /**
     * Note: make sure to .close() the entry when you are done with it!
     */
    suspend fun dropBoxGetNextEntry(lastTimeMillis: Long): StdResult<DropBoxManager.Entry?> =
        send(DropBoxGetNextEntryRequest(lastTimeMillis)) { response: DropBoxGetNextEntryResponse -> response.entry }

    suspend fun <R> sleep(
        delaySeconds: Int,
        timeout: Duration,
        block: suspend (CommandRunnerClient.Invocation) -> StdResult<R>,
    ): StdResult<R> =
        withVersion(context = "sleep") { version ->
            commandRunnerClientFactory.create(timeout = timeout, reporterVersion = version).run(block) { options ->
                sendAndGetReplyHandler(SleepRequest(SleepCommand(delaySeconds), options))
            }
        }

    suspend fun <R> batteryStatsRun(
        cmd: BatteryStatsCommand,
        timeout: Duration,
        block: suspend (CommandRunnerClient.Invocation) -> StdResult<R>,
    ): StdResult<R> =
        withVersion(context = "battery stats") { version ->
            commandRunnerClientFactory.create(timeout = timeout, reporterVersion = version).run(block) { options ->
                sendAndGetReplyHandler(BatteryStatsRequest(cmd, options))
            }
        }

    suspend fun <R> logcatRun(
        cmd: LogcatCommand,
        timeout: Duration,
        block: suspend (CommandRunnerClient.Invocation) -> StdResult<R>,
    ): StdResult<R> =
        withVersion(context = "logcat") { version ->
            commandRunnerClientFactory.create(timeout = timeout, reporterVersion = version).run(block) { options ->
                sendAndGetReplyHandler(LogcatRequest(cmd, options))
            }
        }

    suspend fun <R> packageManagerRun(
        cmd: PackageManagerCommand,
        timeout: Duration,
        block: suspend (CommandRunnerClient.Invocation) -> StdResult<R>,
    ): StdResult<R> =
        withVersion(context = "package manager") { version ->
            commandRunnerClientFactory.create(timeout = timeout, reporterVersion = version).run(block) { options ->
                sendAndGetReplyHandler(PackageManagerRequest(cmd, options))
            }
        }

    suspend fun sendFileToLinkedDevice(file: File, dropboxTag: String): StdResult<Unit> {
        return withVersion(
            context = "uploadfile",
            minimumVersion = MINIMUM_VALID_VERSION_FILE_UPLOAD_V2_REPORTER_SETTINGS
        ) {
            val descriptor = ParcelFileDescriptor.open(
                file, ParcelFileDescriptor.MODE_READ_ONLY, MAIN_THREAD_HANDLER
            ) {
                file.deleteSilently()
            }
            try {
                send(ServerSendFileRequest(dropboxTag, descriptor)) { _: ServerSendFileResponse -> }
            } finally {
                // Always close the pfd. We expect it to be normally closed by the service (using an
                // AutoCloseInputStream) but make sure. Not using `use {}` for this, because that can potentially
                // throw if the resource is already closed (and there wasn't a caught exception).
                descriptor.closeQuietly()
            }
        }
    }

    suspend fun setMetricsCollectionInterval(interval: Duration): StdResult<Unit> =
        withVersion(context = "metricinterval", minimumVersion = MINIMUM_VALID_VERSION_METRIC_COLLECTION) {
            send(SetMetricCollectionIntervalRequest(interval)) { _: SetMetricCollectionIntervalResponse -> }
        }

    suspend fun setReporterSettings(settings: SetReporterSettingsRequest): StdResult<Unit> =
        withVersion(
            context = "reportersettings",
            minimumVersion = MINIMUM_VALID_VERSION_FILE_UPLOAD_V2_REPORTER_SETTINGS
        ) {
            send(settings) { _: SetReporterSettingsResponse -> }
        }

    private suspend inline fun <R> withVersion(
        context: Any,
        minimumVersion: Int = MINIMUM_VALID_VERSION,
        block: (Int) -> StdResult<R>,
    ): StdResult<R> =
        getVersion().let { version ->
            val v = version ?: 0
            if (v >= minimumVersion) {
                block(v)
            } else {
                Result.failure(
                    UnsupportedOperationException(
                        "Unsupported request for $context ($version < $minimumVersion)".also { Logger.v(it) }
                    )
                )
            }
        }

    private suspend inline fun <reified RS, RV> send(
        request: ReporterServiceMessage,
        minimumVersion: Int = MINIMUM_VALID_VERSION,
        timeout: Duration = DEFAULT_REPLY_TIMEOUT,
        responseBlock: (RS) -> RV,
    ): StdResult<RV> =
        withVersion(request, minimumVersion) {
            sendWithoutVersionCheck(request, timeout, responseBlock)
        }

    private suspend fun sendAndGetReplyHandler(
        message: ReporterServiceMessage,
        minimumVersion: Int = MINIMUM_VALID_VERSION,
    ): StdResult<ServiceMessageReplyHandler<ReporterServiceMessage>> =
        withVersion(message, minimumVersion) { runCatching { connection.sendAndGetReplyHandler(message) } }

    private suspend inline fun <reified RS : ReporterServiceMessage, RV> sendWithoutVersionCheck(
        request: ReporterServiceMessage,
        timeout: Duration = DEFAULT_REPLY_TIMEOUT,
        responseBlock: (RS) -> RV,
    ): StdResult<RV> =
        connection.sendAndReceive(request, timeout).mapCatching { response ->
            when (response) {
                is RS -> responseBlock(response)
                else -> throw errorOrUnexpectedResponseException(request, response).also {
                    Logger.e("Request failed", it)
                }
            }
        }

    private fun errorOrUnexpectedResponseException(request: ServiceMessage, response: ServiceMessage?): Throwable =
        when (response) {
            is ErrorResponse -> ErrorResponseException("Error response to $request: ${response.error}")
            else -> UnexpectedResponseException("Unexpected response to $request: $response")
        }

    companion object {
        val MAIN_THREAD_HANDLER: Handler by lazy { Handler(Looper.getMainLooper()!!) }
    }
}
