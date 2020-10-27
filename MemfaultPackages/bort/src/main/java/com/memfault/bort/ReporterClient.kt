package com.memfault.bort

import android.content.ComponentName
import android.content.Context
import android.os.DropBoxManager
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import com.memfault.bort.shared.APPLICATION_ID_MEMFAULT_USAGE_REPORTER
import com.memfault.bort.shared.BatteryStatsCommand
import com.memfault.bort.shared.BatteryStatsRequest
import com.memfault.bort.shared.BatteryStatsResponse
import com.memfault.bort.shared.DropBoxGetNextEntryRequest
import com.memfault.bort.shared.DropBoxGetNextEntryResponse
import com.memfault.bort.shared.DropBoxSetTagFilterRequest
import com.memfault.bort.shared.DropBoxSetTagFilterResponse
import com.memfault.bort.shared.ErrorResponse
import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.LogcatRequest
import com.memfault.bort.shared.LogcatResponse
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.REPORTER_SERVICE_QUALIFIED_NAME
import com.memfault.bort.shared.RealServiceMessageConnection
import com.memfault.bort.shared.ReporterServiceMessage
import com.memfault.bort.shared.ServiceMessage
import com.memfault.bort.shared.ServiceMessageConnection
import com.memfault.bort.shared.VersionRequest
import com.memfault.bort.shared.VersionResponse
import java.io.FileInputStream

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

private const val MINIMUM_VALID_VERSION = 1

class ReporterClient(
    val connection: ReporterServiceConnection,
    val commandRunnerClientFactory: CommandRunnerClientFactory
) {
    private var cachedVersion: Int? = null

    suspend fun getVersion(): Int? =
        cachedVersion
            ?: sendWithoutVersionCheck(
                VersionRequest(),
                { response: VersionResponse -> response.version }, { null }
            ).also { version ->
                cachedVersion = version
            }

    suspend fun dropBoxSetTagFilter(includedTags: List<String>): Boolean =
        send(
            DropBoxSetTagFilterRequest(includedTags),
            { _: DropBoxSetTagFilterResponse -> true },
            { false }
        )

    suspend fun dropBoxGetNextEntry(lastTimeMillis: Long): Pair<DropBoxManager.Entry?, Boolean> =
        send(
            DropBoxGetNextEntryRequest(lastTimeMillis),
            { response: DropBoxGetNextEntryResponse -> Pair(response.entry, true) },
            { Pair(null, false) }
        )

    suspend fun <R> batteryStatsRun(
        cmd: BatteryStatsCommand,
        block: suspend (FileInputStream?) -> R
    ): R =
        commandRunnerClientFactory.create().use { client ->
            send(
                BatteryStatsRequest(cmd, client.options),
                { _: BatteryStatsResponse -> block(client.handOffAndGetInputStream()) },
                { block(null) }
            )
        }

    suspend fun <R> batteryStatsGetHistory(
        lastTimeMillis: Long,
        block: suspend (FileInputStream?) -> R
    ): R =
        batteryStatsRun(BatteryStatsCommand(c = true, historyStart = lastTimeMillis), block)

    suspend fun <R> logcatRun(cmd: LogcatCommand, block: suspend (FileInputStream?) -> R): R =
        commandRunnerClientFactory.create().use { client ->
            send(
                LogcatRequest(cmd, client.options),
                { _: LogcatResponse -> block(client.handOffAndGetInputStream()) },
                { block(null) }
            )
        }

    private suspend inline fun <reified RS, RV> send(
        request: ReporterServiceMessage,
        responseBlock: (RS) -> RV,
        errorBlock: () -> RV,
        minimumVersion: Int = MINIMUM_VALID_VERSION
    ): RV =
        getVersion().let { version ->
            if (version ?: 0 >= minimumVersion) {
                sendWithoutVersionCheck(request, responseBlock, errorBlock)
            } else {
                errorBlock().also {
                    Logger.d("Unsupported request $request ($version < $minimumVersion)")
                }
            }
        }

    private suspend inline fun <reified RS, RV> sendWithoutVersionCheck(
        request: ReporterServiceMessage,
        responseBlock: (RS) -> RV,
        errorBlock: () -> RV
    ): RV =
        when (val response = connection.sendAndReceive(request)) {
            is RS -> responseBlock(response)
            else -> errorBlock().also {
                logErrorOrUnexpectedResponse(request, response)
            }
        }

    private fun logErrorOrUnexpectedResponse(request: ServiceMessage, response: ServiceMessage) {
        when (response) {
            is ErrorResponse -> Logger.e("Error response to $request: ${response.error}")
            else -> Logger.e("Unexpected response to $request: $response")
        }
    }
}
