package com.memfault.bort.customevent

import com.memfault.bort.customevent.NumericAgg.SUM
import com.memfault.bort.customevent.StateAgg.LATEST_VALUE

object Reporting {
    /**
     * Default report. Values are aggregated using the default Bort heartbeat period.
     */
    @JvmStatic
    fun report() = Report(reportType = HEARTBEAT_REPORT)

    /**
     * Finish a report. This is currently private and only used within Bort for heartbeats.
     */
    @JvmStatic
    private fun finishReport(
        reportType: String = HEARTBEAT_REPORT,
        timeMs: Long = timestamp(),
        startNextReport: Boolean = false
    ): Boolean = RemoteMetricsService.finishReport(reportType, timeMs, startNextReport)

    // Bort heartbeat will call RemoteMetricsService.finishReport() using this report name.
    internal const val HEARTBEAT_REPORT = "Heartbeat"

    fun timestamp(): Long = System.currentTimeMillis()

    class Report internal constructor(val reportType: String) {
        /**
         * Aggregates the total count at the end of the period.
         */
        @JvmOverloads
        fun counter(
            name: String,
            internal: Boolean = false,
        ) = Counter(name = name, reportType = reportType, internal = internal)

        /**
         * Keeps track of a of the values recorded during the period.
         *
         * One metric will be generated for each [aggregations].
         */
        @JvmOverloads
        fun distribution(
            name: String,
            vararg aggregations: NumericAgg,
            internal: Boolean = false,
        ) = Distribution(name = name, reportType = reportType, aggregations.asList(), internal = internal)

        /**
         * Tracks total time spent in each state during the report period.
         */
        @JvmOverloads
        fun <T : Enum<T>> stateTracker(
            name: String,
            vararg aggregations: StateAgg,
            internal: Boolean = false,
        ) = StateTracker<T>(
            name = name, reportType = reportType, aggregations = aggregations.asList(),
            internal = internal
        )

        /**
         * Keep track of the latest value of a string property.
         */
        @JvmOverloads
        fun stringProperty(name: String, internal: Boolean = false) =
            StringProperty(name = name, reportType = reportType, internal = internal)

        /**
         * Keep track of the latest value of a string property.
         */
        @JvmOverloads
        fun numberProperty(name: String, internal: Boolean = false) = NumberProperty(
            name = name, reportType = reportType,
            internal = internal
        )
    }

    abstract class Metric internal constructor() {
        internal abstract val name: String
        internal abstract val reportType: String
        internal abstract val aggregations: List<AggregationType>
        internal abstract val internal: Boolean

        protected fun add(
            timeMs: Long,
            stringVal: String? = null,
            numberVal: Double? = null,
        ) {
            // Sends entire metric definition + current value over IPC to the logging/metrics daemon.
            RemoteMetricsService.record(
                MetricValue(
                    timeMs = timeMs,
                    reportType = reportType,
                    eventName = name,
                    aggregations = aggregations,
                    stringVal = stringVal,
                    numberVal = numberVal,
                    internal = internal,
                )
            )
        }
    }

    data class StringProperty internal constructor(
        override val name: String,
        override val reportType: String,
        override val internal: Boolean
    ) : Metric() {
        override val aggregations = listOf(LATEST_VALUE)

        @JvmOverloads
        fun update(value: String, timestamp: Long = timestamp()) = add(timeMs = timestamp, stringVal = value)
    }

    data class NumberProperty internal constructor(
        override val name: String,
        override val reportType: String,
        override val internal: Boolean
    ) : Metric() {
        override val aggregations = listOf(LATEST_VALUE)

        @JvmOverloads
        fun update(value: Double, timestamp: Long = timestamp()) = add(timeMs = timestamp, numberVal = value)

        @JvmOverloads
        fun update(value: Float, timestamp: Long = timestamp()) = add(timeMs = timestamp, numberVal = value.toDouble())

        @JvmOverloads
        fun update(value: Long, timestamp: Long = timestamp()) = add(timeMs = timestamp, numberVal = value.toDouble())

        @JvmOverloads
        fun update(value: Int, timestamp: Long = timestamp()) = add(timeMs = timestamp, numberVal = value.toDouble())

        @JvmOverloads
        fun update(value: Boolean, timestamp: Long = timestamp()) =
            add(timeMs = timestamp, numberVal = value.asNumber())
    }

    data class Counter internal constructor(
        override val name: String,
        override val reportType: String,
        override val internal: Boolean
    ) : Metric() {
        override val aggregations = listOf(SUM)

        @JvmOverloads
        fun incrementBy(byDouble: Double = 1.0, timestamp: Long = timestamp()) {
            add(timeMs = timestamp, numberVal = byDouble)
        }

        @JvmName("incrementByInt")
        @JvmOverloads
        fun incrementBy(by: Int = 1, timestamp: Long = timestamp()) {
            add(timeMs = timestamp, numberVal = by.toDouble())
        }

        @JvmOverloads
        fun increment(timestamp: Long = timestamp()) = incrementBy(1, timestamp = timestamp)
    }

    data class StateTracker<T : Enum<T>> internal constructor(
        override val name: String,
        override val reportType: String,
        override val aggregations: List<StateAgg>,
        override val internal: Boolean
    ) : Metric() {
        @JvmOverloads
        fun state(state: T?, timestamp: Long = timestamp()) {
            add(timeMs = timestamp, stringVal = state?.name)
        }
    }

    data class Distribution internal constructor(
        override val name: String,
        override val reportType: String,
        override val aggregations: List<NumericAgg>,
        override val internal: Boolean
    ) : Metric() {
        @JvmOverloads
        fun record(value: Double, timestamp: Long = timestamp()) {
            add(timeMs = timestamp, numberVal = value)
        }

        @JvmOverloads
        fun record(value: Long, timestamp: Long = timestamp()) {
            add(timeMs = timestamp, numberVal = value.toDouble())
        }
    }
}

private fun Boolean.asNumber() = if (this) 1.0 else 0.0

interface AggregationType

enum class NumericAgg : AggregationType {
    /**
     * Minimum value seen during the period.
     */
    MIN,
    /**
     * Maximum value seen during the period.
     */
    MAX,
    /**
     * Sum of all values seen during the period.
     */
    SUM,
    /**
     * Mean value seen during the period.
     */
    MEAN,
    /**
     * Number of values seen during the period.
     */
    COUNT,
    // Future: more aggregations e.g. Std Dev, Percentile
}

enum class StateAgg : AggregationType {
    /**
     * Metric per state, reporting time spent in that state during the period.
     */
    TIME_TOTALS,
    /**
     * Metric per state, reporting time spent in that state during the period (per hour).
     */
    TIME_PER_HOUR,
    /**
     * The latest value reported for this property.
     */
    LATEST_VALUE,
}
