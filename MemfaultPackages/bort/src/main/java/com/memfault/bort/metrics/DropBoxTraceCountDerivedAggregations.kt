package com.memfault.bort.metrics

import com.memfault.bort.metrics.CrashFreeHoursMetricLogger.Companion.dropBoxTagCountMetric
import com.memfault.bort.metrics.custom.ReportType
import com.memfault.bort.metrics.custom.ReportType.Daily
import com.memfault.bort.metrics.custom.ReportType.Hourly
import com.memfault.bort.metrics.database.CalculateDerivedAggregations
import com.memfault.bort.metrics.database.DerivedAggregation
import com.memfault.bort.reporting.DataType
import com.memfault.bort.reporting.MetricType
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import javax.inject.Inject

@ContributesMultibinding(SingletonComponent::class, boundType = CalculateDerivedAggregations::class)
class DropBoxTraceCountDerivedAggregations
@Inject constructor() : CalculateDerivedAggregations {

    override fun calculate(
        reportType: ReportType,
        startTimestampMs: Long,
        endTimestampMs: Long,
        metrics: Map<String, JsonPrimitive>,
        internalMetrics: Map<String, JsonPrimitive>,
    ): List<DerivedAggregation> = when (reportType) {
        Hourly, Daily -> TAGS_TO_REASONS.map { (tag, reasons) ->
            val sum = reasons.sumOf { reason -> internalMetrics[reason]?.doubleOrNull ?: 0.0 }

            DerivedAggregation.create(
                metricName = dropBoxTagCountMetric(tag),
                metricValue = sum.toDouble(),
                metricType = MetricType.COUNTER,
                dataType = DataType.DOUBLE,
                collectionTimeMs = endTimestampMs,
                internal = false,
            )
        } + if (metrics.containsKey("${dropBoxTagCountMetric("panic")}.sum")) {
            emptyList()
        } else {
            listOf(
                DerivedAggregation.create(
                    metricName = dropBoxTagCountMetric("panic"),
                    metricValue = 0.0,
                    metricType = MetricType.COUNTER,
                    dataType = DataType.DOUBLE,
                    collectionTimeMs = endTimestampMs,
                    internal = false,
                ),
            )
        }

        else -> emptyList()
    }

    companion object {
        val DROP_BOX_TAGS = listOf(
            "anr",
            "exception",
            "kmsg",
            "native",
            "panic",
            "wtf",
        ).map { tag -> dropBoxTagCountMetric(tag) }

        private val TAGS_TO_REASONS = mapOf(
            "anr" to listOf(
                "drop_box_trace_data_app_anr_count.sum",
                "drop_box_trace_system_app_anr_count.sum",
                "drop_box_trace_system_server_anr_count.sum",
            ),
            "exception" to listOf(
                "drop_box_trace_data_app_crash_count.sum",
                "drop_box_trace_system_app_crash_count.sum",
                "drop_box_trace_system_server_crash_count.sum",
            ),
            "wtf" to listOf(
                "drop_box_trace_data_app_wtf_count.sum",
                "drop_box_trace_system_app_wtf_count.sum",
                "drop_box_trace_system_server_wtf_count.sum",
            ),
            "native" to listOf(
                "drop_box_trace_data_app_native_crash_count.sum",
                "drop_box_trace_system_app_native_crash_count.sum",
                "drop_box_trace_system_server_native_crash_count.sum",
                "drop_box_trace_system_tombstone_count.sum",
            ),
            "kmsg" to listOf(
                "drop_box_trace_system_last_kmsg_count.sum",
                "drop_box_trace_system_recovery_kmsg_count.sum",
            ),
        )
    }
}
