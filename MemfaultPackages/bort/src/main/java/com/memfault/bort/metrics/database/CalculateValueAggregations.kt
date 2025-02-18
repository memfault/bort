package com.memfault.bort.metrics.database

import com.memfault.bort.reporting.AggregationType
import com.memfault.bort.reporting.StateAgg
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * [CalculateValueAggregations] are aggregations that want to iterate over the entire range of values in ascending
 * time, to calculate a more complex aggregation, like the time spent in each state.
 */
interface CalculateValueAggregations {
    /**
     * [onEach] will be called once with a value <= [reportStartMs], if such a value exists. This allows the
     * aggregation to track the "initial value" of the metric at the start of the report. Alternatively we could amend
     * the interface to pass the latest [DbMetricValue] first.
     */
    suspend fun onEach(
        reportStartMs: Long,
        reportEndMs: Long,
        metricValue: DbMetricValue,
    )

    suspend fun finish(
        reportStartMs: Long,
        reportEndMs: Long,
    ): Map<String, JsonPrimitive>
}

class TimeInStateValueAggregations(
    private val aggs: List<AggregationType>,
    private val key: String,
) : CalculateValueAggregations {

    private val timeInStateMs = mutableMapOf<String, Long>()
    private var pendingShift: Shift? = null
    private val shifts = mutableListOf<Shift>()
    private var last: DbMetricValue? = null

    private data class Shift(
        val state: String,
        var duration: Long,
    )

    override suspend fun onEach(
        reportStartMs: Long,
        reportEndMs: Long,
        metricValue: DbMetricValue,
    ) {
        last?.let { l ->
            val state = l.jsonValue().contentOrNull ?: return@let
            val existingTimeInState = timeInStateMs.getOrDefault(state, 0)
            val timeOfLastRecord = l.timestampMs.coerceAtLeast(reportStartMs)
            val timeOfCurrentRecord = metricValue.timestampMs.coerceAtLeast(reportStartMs)
            val durationSinceLastRecord = timeOfCurrentRecord - timeOfLastRecord
            timeInStateMs[state] = existingTimeInState + durationSinceLastRecord

            val absoluteDuration = metricValue.timestampMs - l.timestampMs

            val currentShift = pendingShift ?: Shift(state, 0)

            val nextState = metricValue.jsonValue().contentOrNull ?: return@let
            if (nextState != currentShift.state) {
                // The previous state is different from the current state, then extend the time of the last shift
                // and commit its finalized duration.
                currentShift.duration += absoluteDuration
                shifts.add(currentShift)

                pendingShift = Shift(nextState, 0)
            } else {
                // Otherwise, only extend the time of the current shift.
                currentShift.duration += absoluteDuration

                pendingShift = currentShift
            }
        }
        last = metricValue
    }

    override suspend fun finish(
        reportStartMs: Long,
        reportEndMs: Long,
    ): Map<String, JsonPrimitive> {
        last?.let { l ->
            val state = l.jsonValue().contentOrNull ?: return@let
            val existingTimeInState = timeInStateMs.getOrDefault(state, 0)
            val timeOfLastRecord = l.timestampMs.coerceAtLeast(reportStartMs)
            val timeOfCurrentRecord = reportEndMs.coerceAtLeast(reportStartMs)
            val durationSinceLastRecord = timeOfCurrentRecord - timeOfLastRecord
            timeInStateMs[state] = existingTimeInState + durationSinceLastRecord
        }

        // Note that we don't finish shifts automatically as that would make the shift length inaccurate.

        val reportDurationMs = reportEndMs - reportStartMs
        val metrics = mutableMapOf<String, JsonPrimitive>()

        timeInStateMs.forEach { (state, timeMs) ->
            if (StateAgg.TIME_PER_HOUR in aggs) {
                if (timeMs <= reportDurationMs) {
                    metrics["${key}_$state.secs/hour"] =
                        JsonPrimitive(
                            if (reportDurationMs != 0L) {
                                val scale = timeMs / reportDurationMs.toDouble()
                                1.hours.inWholeSeconds.times(scale).roundToLong()
                            } else {
                                0L
                            },
                        )
                }
            }
            if (StateAgg.TIME_TOTALS in aggs) {
                metrics["${key}_$state.total_secs"] = JsonPrimitive(timeMs.milliseconds.inWholeSeconds)
            }
        }

        val shiftsInState = shifts.groupBy(keySelector = { it.state }, valueTransform = { it.duration })
        shiftsInState.forEach { (state, shifts) ->
            if (StateAgg.TIME_TOTALS in aggs) {
                val filteredShifts = shifts.filterNot { it == 0L }
                if (filteredShifts.isNotEmpty()) {
                    val meanMs = filteredShifts.average()
                    metrics["${key}_$state.mean_time_in_state_ms"] = JsonPrimitive(meanMs.roundToLong())
                }
            }
        }

        return metrics
    }
}

class ValueDropValueAggregations(
    private val key: String,
) : CalculateValueAggregations {

    private var valueDrop = 0.0
    private var last: DbMetricValue? = null

    override suspend fun onEach(
        reportStartMs: Long,
        reportEndMs: Long,
        metricValue: DbMetricValue,
    ) {
        last?.let { l ->
            val lastValue = l.numberVal ?: return@let
            val currentValue = metricValue.numberVal ?: return@let
            val drop = currentValue - lastValue
            if (drop < 0.0) {
                valueDrop += abs(drop)
            }
        }
        last = metricValue
    }

    override suspend fun finish(
        reportStartMs: Long,
        reportEndMs: Long,
    ): Map<String, JsonPrimitive> {
        val metrics = mutableMapOf<String, JsonPrimitive>()
        metrics["${key}_drop"] = JsonPrimitive(valueDrop)
        return metrics
    }
}
