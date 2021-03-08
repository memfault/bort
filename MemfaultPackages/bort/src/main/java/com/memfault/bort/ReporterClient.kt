package com.memfault.bort

import android.content.ComponentName
import android.content.Context
import android.os.DropBoxManager
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.runCatching
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
import com.memfault.bort.shared.PackageManagerCommand
import com.memfault.bort.shared.PackageManagerRequest
import com.memfault.bort.shared.REPORTER_SERVICE_QUALIFIED_NAME
import com.memfault.bort.shared.RealServiceMessageConnection
import com.memfault.bort.shared.ReporterServiceMessage
import com.memfault.bort.shared.ServiceMessage
import com.memfault.bort.shared.ServiceMessageConnection
import com.memfault.bort.shared.ServiceMessageReplyHandler
import com.memfault.bort.shared.SetLogLevelRequest
import com.memfault.bort.shared.SetLogLevelResponse
import com.memfault.bort.shared.SleepCommand
import com.memfault.bort.shared.SleepRequest
import com.memfault.bort.shared.UnexpectedResponseException
import com.memfault.bort.shared.VersionRequest
import com.memfault.bort.shared.VersionResponse
import com.memfault.bort.shared.result.StdResult
import com.memfault.bort.shared.result.failure
import com.memfault.bort.shared.result.mapCatching
import com.memfault.bort.shared.result.success
import kotlin.time.Duration

typealias ReporterServiceConnection = ServiceMessageConnection<ReporterServiceMessage>
typealias ReporterServiceConnector = ServiceConnector<ReporterClient>

class RealReporterServiceConnector(
    context: Context,
    val inboundLooper: Looper
) : ReporterServiceConnector(
    context,
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
            CommandRunnerClient.RealFactory
        )
}

private const val MINIMUM_VALID_VERSION = 3
private const val MINIMUM_VALID_VERSION_LOG_LEVEL = 4

class ReporterClient(
    val connection: ReporterServiceConnection,
    val commandRunnerClientFactory: CommandRunnerClientFactory
) {
    private var cachedVersion: Int? = null

    suspend fun getVersion(): Int? =
        cachedVersion ?: sendWithoutVersionCheck(VersionRequest()) { response: VersionResponse ->
            response.version
        }.fold({ it }, { null }).also {
            version ->
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
        block: suspend (CommandRunnerClient.Invocation) -> StdResult<R>
    ): StdResult<R> =
        withVersion(context = "sleep") {
            commandRunnerClientFactory.create(timeout = timeout).run(block) { options ->
                sendAndGetReplyHandler(SleepRequest(SleepCommand(delaySeconds), options))
            }
        }

    suspend fun <R> batteryStatsRun(
        cmd: BatteryStatsCommand,
        timeout: Duration,
        block: suspend (CommandRunnerClient.Invocation) -> StdResult<R>
    ): StdResult<R> =
        withVersion(context = "battery stats") {
            commandRunnerClientFactory.create(timeout = timeout).run(block) { options ->
                sendAndGetReplyHandler(BatteryStatsRequest(cmd, options))
            }
        }

    suspend fun <R> logcatRun(
        cmd: LogcatCommand,
        timeout: Duration,
        block: suspend (CommandRunnerClient.Invocation) -> StdResult<R>,
    ): StdResult<R> =
        withVersion(context = "logcat") {
            commandRunnerClientFactory.create(timeout = timeout).run(block) { options ->
                sendAndGetReplyHandler(LogcatRequest(cmd, options))
            }
        }

    suspend fun <R> packageManagerRun(
        cmd: PackageManagerCommand,
        timeout: Duration,
        block: suspend (CommandRunnerClient.Invocation) -> StdResult<R>
    ): StdResult<R> =
        withVersion(context = "package manager") {
            commandRunnerClientFactory.create(timeout = timeout).run(block) { options ->
                sendAndGetReplyHandler(PackageManagerRequest(cmd, options))
            }
        }

    private suspend inline fun <R> withVersion(
        context: Any,
        minimumVersion: Int = MINIMUM_VALID_VERSION,
        block: () -> StdResult<R>
    ): StdResult<R> =
        getVersion().let { version ->
            if (version ?: 0 >= minimumVersion) {
                block()
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
        minimumVersion: Int = MINIMUM_VALID_VERSION
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
}
