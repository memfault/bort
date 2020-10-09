package com.memfault.bort

import android.content.ComponentName
import android.content.Context
import android.os.*
import com.memfault.bort.shared.*

typealias ReporterServiceConnection = ServiceMessageConnection<ReporterServiceMessage>
typealias ReporterServiceConnector = ServiceConnector<ReporterServiceConnection>

class RealReporterServiceConnector(
    context: Context, val inboundLooper: Looper
) : ReporterServiceConnector(
    context,
    ComponentName(
        APPLICATION_ID_MEMFAULT_USAGE_REPORTER,
        REPORTER_SERVICE_QUALIFIED_NAME
    )
) {
    override fun createServiceWithBinder(binder: IBinder): ReporterServiceConnection =
        RealServiceMessageConnection(
            Messenger(binder), inboundLooper, ReporterServiceMessage.Companion::fromMessage
        )
}

suspend fun ReporterServiceConnection.setTagFilter(includedTags: List<String>): Boolean =
    when (val reply = sendAndReceive(SetTagFilterRequest(includedTags))) {
        is SetTagFilterResponse -> true
        is ErrorResponse -> false.also {
            Logger.e("Error response to setTagFilter: ${reply.error}")
        }
        else -> false.also {
            Logger.e("Unexpected response to setTagFilter: $reply")
        }
    }


suspend fun ReporterServiceConnection.getNextEntry(lastTimeMillis: Long): Pair<DropBoxManager.Entry?, Boolean> =
    when (val reply = sendAndReceive(GetNextEntryRequest(lastTimeMillis))) {
        is GetNextEntryResponse -> Pair(reply.entry, true)
        is ErrorResponse -> Pair(null, false).also {
            Logger.e("Error response to getNextEntry: ${reply.error}")
        }
        else -> Pair(null, false).also {
            Logger.e("Unexpected response to getNextEntry: $reply")
        }
    }
