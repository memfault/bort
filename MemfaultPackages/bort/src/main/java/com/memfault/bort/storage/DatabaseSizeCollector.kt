package com.memfault.bort.storage

import android.app.Application
import com.memfault.bort.diagnostics.BORT_ERRORS_DB_NAME
import com.memfault.bort.metrics.InMemoryMetric
import com.memfault.bort.metrics.InMemoryMetricCollector
import com.memfault.bort.metrics.database.CURRENT_METRICS_DB_NAME
import com.memfault.bort.reporting.MetricType
import com.memfault.bort.time.CombinedTime
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import javax.inject.Inject

private const val METRICS_DB = "db.metrics"
private const val BORT_ERRORS_DB = "db.bort_errors"
private const val UNKNOWN_DB = "db.unknown"

class DatabaseSizeCollector
@Inject constructor(
    private val application: Application,
) : InMemoryMetricCollector {
    override suspend fun collect(
        collectionTime: CombinedTime,
    ): List<InMemoryMetric> =
        application.databaseList()
            .groupBy(
                keySelector = { name ->
                    if (name.startsWith(CURRENT_METRICS_DB_NAME)) {
                        METRICS_DB
                    } else if (name.startsWith(BORT_ERRORS_DB_NAME)) {
                        BORT_ERRORS_DB
                    } else {
                        UNKNOWN_DB
                    }
                },
                valueTransform = { name ->
                    try {
                        application.getDatabasePath(name)
                    } catch (e: Exception) {
                        null
                    }
                },
            )
            .map { (name, files) ->
                InMemoryMetric(
                    metricName = "$name.latest",
                    metricValue = JsonPrimitive(files.combinedBytes()),
                    metricType = MetricType.GAUGE,
                    internal = true,
                )
            }

    private fun List<File?>.combinedBytes() = sumOf { it?.length() ?: 0L }
}
