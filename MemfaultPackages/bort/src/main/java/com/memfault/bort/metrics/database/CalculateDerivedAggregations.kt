package com.memfault.bort.metrics.database

import com.memfault.bort.reporting.DataType
import com.memfault.bort.reporting.MetricType
import kotlinx.serialization.json.JsonPrimitive

/**
 * A single metric value that is manually derived from metrics in an existing report.
 *
 * See [CalculateDerivedAggregations].
 */
data class DerivedAggregation(
    val metadata: DbMetricMetadata,
    val value: DbMetricValue,
) {

    companion object {
        fun List<DerivedAggregation>.asMetrics(internal: Boolean): Map<String, JsonPrimitive> =
            mapNotNull { aggregation ->
                (aggregation.metadata.eventName to aggregation.value.jsonValue())
                    .takeIf { aggregation.metadata.internal == internal }
            }
                .toMap()

        fun create(
            metricName: String,
            metricValue: Double,
            metricType: MetricType,
            dataType: DataType,
            collectionTimeMs: Long,
            internal: Boolean,
        ): DerivedAggregation = DerivedAggregation(
            metadata = DbMetricMetadata(
                reportId = UNUSED_ID,
                eventName = metricName,
                metricType = metricType,
                dataType = dataType,
                carryOver = false,
                aggregations = Aggs(emptyList()),
                internal = internal,
            ),
            value = DbMetricValue(
                metadataId = UNUSED_ID,
                version = UNUSED_VERSION,
                timestampMs = collectionTimeMs,
                numberVal = metricValue,
            ),
        )

        private const val UNUSED_ID = -1L
        private const val UNUSED_VERSION = -1
    }
}

/**
 * Before a [MetricReport] is finalized, we may want to add some additional metrics based off of any completed
 * aggregations of that report, like the time totals.
 *
 * This will add the value to both the Report, and HRT if applicable.
 */
fun interface CalculateDerivedAggregations {
    fun calculate(
        startTimestampMs: Long,
        endTimestampMs: Long,
        metrics: Map<String, JsonPrimitive>,
        internalMetrics: Map<String, JsonPrimitive>,
    ): List<DerivedAggregation>
}
