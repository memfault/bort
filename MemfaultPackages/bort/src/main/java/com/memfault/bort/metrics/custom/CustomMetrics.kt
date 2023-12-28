package com.memfault.bort.metrics.custom

import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.dropbox.MetricReport
import com.memfault.bort.metrics.database.MetricsDb
import com.memfault.bort.reporting.MetricValue
import com.memfault.bort.settings.StructuredLogSettings
import java.io.File
import javax.inject.Inject

class CustomMetrics @Inject constructor(
    private val db: MetricsDb,
    private val temporaryFileFactory: TemporaryFileFactory,
    private val structuredLogSettings: StructuredLogSettings,
) {
    suspend fun add(metric: MetricValue) {
        db.dao().insert(metric)
    }

    suspend fun finishReport(reportType: String, endTimestampMs: Long): CustomReport {
        return db.dao().finishReport(
            reportType = reportType,
            endTimestampMs = endTimestampMs,
            hrtFile = if (structuredLogSettings.highResMetricsEnabled) {
                temporaryFileFactory.createTemporaryFile(suffix = "hrt").useFile { file, preventDeletion ->
                    preventDeletion()
                    file
                }
            } else {
                null
            },
        )
    }
}

data class CustomReport(
    val report: MetricReport,
    val hrt: File?,
)
