package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.BortJson
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.metrics.HeartbeatReportCollector
import com.memfault.bort.settings.MetricReportEnabled
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.tokenbucket.MetricReportStore
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import okio.IOException
import okio.buffer
import okio.sink
import okio.source

private const val DROPBOX_ENTRY_TAG = "memfault_report"
private const val HEARTBEAT_REPORT_TYPE = "Heartbeat"

@ContributesMultibinding(SingletonComponent::class)
class MetricReportEntryProcessor @Inject constructor(
    private val temporaryFileFactory: TemporaryFileFactory,
    @MetricReportStore private val tokenBucketStore: TokenBucketStore,
    private val metricReportEnabledConfig: MetricReportEnabled,
    private val heartbeatReportCollector: HeartbeatReportCollector,
) : EntryProcessor() {
    override val tags: List<String> = listOf(DROPBOX_ENTRY_TAG)

    private fun allowedByRateLimit(): Boolean = tokenBucketStore.takeSimple(key = DROPBOX_ENTRY_TAG, tag = "report")

    override suspend fun process(entry: DropBoxManager.Entry, fileTime: AbsoluteTime?) {
        if (!metricReportEnabledConfig()) {
            return
        }

        if (!allowedByRateLimit()) {
            return
        }

        temporaryFileFactory.createTemporaryFile(entry.tag, "txt").useFile { tempFile, preventDeletion ->
            val report = try {
                tempFile.sink().buffer().use { sink ->
                    val inStream = entry.inputStream ?: return@useFile
                    inStream.buffered().source().use { if (sink.writeAll(it) == 0L) return@useFile }
                }

                val report = try {
                    BortJson.decodeFromString(
                        MetricReport.serializer(),
                        tempFile.readText(),
                    )
                } catch (ex: SerializationException) {
                    Logger.w("Received an unparseable metric report, ignoring")
                    return@useFile
                }

                report
            } catch (ex: IOException) {
                Logger.w("Failed to parse metric report entry", ex)
                return@useFile
            }

            if (report.reportType != HEARTBEAT_REPORT_TYPE) {
                Logger.w(
                    "Received a metric report of type ${report.reportType} " +
                        "but currently on handling heartbeats"
                )
                return@useFile
            }

            // Don't directly upload the heartbeat report: the metrics collection task should be waiting for it.
            heartbeatReportCollector.handleFinishedHeartbeatReport(report)
        }
    }
}

data class MetricReportWithHighResFile(
    val metricReport: MetricReport,
    val highResFile: File?,
)

@Serializable
data class MetricReport(
    val version: Int,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val reportType: String,
    val metrics: Map<String, JsonPrimitive>,
    val internalMetrics: Map<String, JsonPrimitive> = mapOf(),
)
