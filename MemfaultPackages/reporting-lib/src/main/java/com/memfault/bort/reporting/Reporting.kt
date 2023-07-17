package com.memfault.bort.reporting

import com.memfault.bort.reporting.NumericAgg.COUNT
import com.memfault.bort.reporting.NumericAgg.SUM
import com.memfault.bort.reporting.StateAgg.LATEST_VALUE

public object Reporting {
    /**
     * Default report. Values are aggregated using the default Bort heartbeat period.
     */
    @JvmStatic
    public fun report(): Report = Report(reportType = HEARTBEAT_REPORT)

    /**
     * Finish a report. This is currently private and only used within Bort for heartbeats.
     */
    @JvmStatic
    private fun finishReport(
        reportType: String = HEARTBEAT_REPORT,
        timeMs: Long = timestamp(),
        startNextReport: Boolean = false,
    ): Boolean = RemoteMetricsService.finishReport(reportType, timeMs, startNextReport)

    // Bort heartbeat will call RemoteMetricsService.finishReport() using this report name.
    private const val HEARTBEAT_REPORT: String = "Heartbeat"

    private fun timestamp(): Long = System.currentTimeMillis()

    public class Report internal constructor(public val reportType: String) {
        /**
         * Aggregates the total count at the end of the period.
         */
        @JvmOverloads
        public fun counter(
            name: String,
            sumInReport: Boolean = true,
            internal: Boolean = false,
        ): Counter = Counter(name = name, reportType = reportType, internal = internal, sumInReport = sumInReport)

        /**
         * Keeps track of a of the values recorded during the period.
         *
         * One metric will be generated for each [aggregations].
         */
        @JvmOverloads
        public fun distribution(
            name: String,
            aggregations: List<NumericAgg> = listOf(),
            internal: Boolean = false,
        ): Distribution = Distribution(
            name = name,
            reportType = reportType,
            aggregations = aggregations,
            internal = internal,
        )

        /**
         * Tracks total time spent in each state during the report period.
         *
         * For use with enums.
         */
        @JvmOverloads
        public fun <T : Enum<T>> stateTracker(
            name: String,
            aggregations: List<StateAgg> = listOf(),
            internal: Boolean = false,
        ): StateTracker<T> = StateTracker(
            name = name, reportType = reportType, aggregations = aggregations,
            internal = internal,
        )

        /**
         * Tracks total time spent in each state during the report period.
         *
         * For use with string representations of state.
         */
        @JvmOverloads
        public fun stringStateTracker(
            name: String,
            aggregations: List<StateAgg> = listOf(),
            internal: Boolean = false,
        ): StringStateTracker = StringStateTracker(
            name = name, reportType = reportType, aggregations = aggregations,
            internal = internal,
        )

        /**
         * Tracks total time spent in each state during the report period.
         *
         * For use with string representations of state.
         */
        @JvmOverloads
        public fun boolStateTracker(
            name: String,
            aggregations: List<StateAgg> = listOf(),
            internal: Boolean = false,
        ): BoolStateTracker = BoolStateTracker(
            name = name, reportType = reportType, aggregations = aggregations,
            internal = internal,
        )

        /**
         * Keep track of the latest value of a string property.
         */
        @JvmOverloads
        public fun stringProperty(
            name: String,
            addLatestToReport: Boolean = true,
            internal: Boolean = false,
        ): StringProperty = StringProperty(
            name = name,
            reportType = reportType,
            addLatestToReport = addLatestToReport,
            internal = internal,
        )

        /**
         * Keep track of the latest value of a string property.
         */
        @JvmOverloads
        public fun numberProperty(
            name: String,
            addLatestToReport: Boolean = true,
            internal: Boolean = false,
        ): NumberProperty = NumberProperty(
            name = name,
            reportType = reportType,
            addLatestToReport = addLatestToReport,
            internal = internal,
        )

        /**
         * Track individual events. Replacement for Custom Events.
         */
        @JvmOverloads
        public fun event(
            name: String,
            countInReport: Boolean = false,
            internal: Boolean = false,
        ): Event = Event(
            name = name,
            reportType = reportType,
            countInReport = countInReport,
            internal = internal,
        )

        public fun numericAggs(vararg aggregations: NumericAgg): List<NumericAgg> = aggregations.asList()

        public fun statsAggs(vararg aggregations: StateAgg): List<StateAgg> = aggregations.asList()
    }

    public abstract class Metric internal constructor() {
        internal abstract val name: String
        internal abstract val reportType: String
        internal abstract val aggregations: List<AggregationType>
        internal abstract val internal: Boolean
        internal abstract val metricType: MetricType
        internal abstract val dataType: DataType
        internal abstract val carryOverValue: Boolean

        protected fun add(
            timeMs: Long,
            stringVal: String? = null,
            numberVal: Double? = null,
            boolVal: Boolean? = null,
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
                    boolVal = boolVal,
                    internal = internal,
                    metricType = metricType,
                    dataType = dataType,
                    carryOverValue = carryOverValue,
                )
            )
        }
    }

    public data class StringProperty internal constructor(
        override val name: String,
        override val reportType: String,
        override val internal: Boolean,
        private val addLatestToReport: Boolean,
    ) : Metric() {
        override val aggregations = if (addLatestToReport) listOf(LATEST_VALUE) else emptyList()
        override val metricType = MetricType.PROPERTY
        override val dataType = DataType.STRING
        override val carryOverValue = true

        @JvmOverloads
        public fun update(value: String?, timestamp: Long = timestamp()): Unit =
            add(timeMs = timestamp, stringVal = value ?: "")
    }

    public data class NumberProperty internal constructor(
        override val name: String,
        override val reportType: String,
        override val internal: Boolean,
        private val addLatestToReport: Boolean,
    ) : Metric() {
        override val aggregations = if (addLatestToReport) listOf(LATEST_VALUE) else emptyList()
        override val metricType = MetricType.PROPERTY
        override val dataType = DataType.DOUBLE
        override val carryOverValue = true

        @JvmOverloads
        public fun update(value: Double?, timestamp: Long = timestamp()): Unit =
            add(timeMs = timestamp, numberVal = value)

        @JvmOverloads
        public fun update(value: Float?, timestamp: Long = timestamp()): Unit =
            add(timeMs = timestamp, numberVal = value?.toDouble())

        @JvmOverloads
        public fun update(value: Long?, timestamp: Long = timestamp()): Unit =
            add(timeMs = timestamp, numberVal = value?.toDouble())

        @JvmOverloads
        public fun update(value: Int?, timestamp: Long = timestamp()): Unit =
            add(timeMs = timestamp, numberVal = value?.toDouble())

        @JvmOverloads
        public fun update(value: Boolean?, timestamp: Long = timestamp()): Unit =
            add(timeMs = timestamp, numberVal = value?.asNumber())
    }

    public data class Counter internal constructor(
        override val name: String,
        override val reportType: String,
        override val internal: Boolean,
        private val sumInReport: Boolean,
    ) : Metric() {
        override val aggregations = if (sumInReport) listOf(SUM) else emptyList()
        override val metricType = MetricType.COUNTER
        override val dataType = DataType.DOUBLE
        override val carryOverValue = false

        @JvmOverloads
        public fun incrementBy(byDouble: Double = 1.0, timestamp: Long = timestamp()) {
            add(timeMs = timestamp, numberVal = byDouble)
        }

        @JvmName("incrementByInt")
        @JvmOverloads
        public fun incrementBy(by: Int = 1, timestamp: Long = timestamp()) {
            add(timeMs = timestamp, numberVal = by.toDouble())
        }

        @JvmOverloads
        public fun increment(timestamp: Long = timestamp()): Unit = incrementBy(1, timestamp = timestamp)
    }

    public data class StateTracker<T : Enum<T>> internal constructor(
        override val name: String,
        override val reportType: String,
        override val aggregations: List<StateAgg>,
        override val internal: Boolean,
    ) : Metric() {
        override val metricType = MetricType.PROPERTY
        override val dataType = DataType.STRING
        override val carryOverValue = true

        @JvmOverloads
        public fun state(state: T?, timestamp: Long = timestamp()) {
            add(timeMs = timestamp, stringVal = state?.name ?: "")
        }
    }

    public data class StringStateTracker internal constructor(
        override val name: String,
        override val reportType: String,
        override val aggregations: List<StateAgg>,
        override val internal: Boolean,
    ) : Metric() {
        override val metricType = MetricType.PROPERTY
        override val dataType = DataType.STRING
        override val carryOverValue = true

        @JvmOverloads
        public fun state(state: String?, timestamp: Long = timestamp()) {
            add(timeMs = timestamp, stringVal = state ?: "")
        }
    }

    public data class BoolStateTracker internal constructor(
        override val name: String,
        override val reportType: String,
        override val aggregations: List<StateAgg>,
        override val internal: Boolean,
    ) : Metric() {
        override val metricType = MetricType.PROPERTY
        override val dataType = DataType.BOOLEAN
        override val carryOverValue = true

        @JvmOverloads
        public fun state(state: Boolean, timestamp: Long = timestamp()) {
            add(timeMs = timestamp, boolVal = state)
        }
    }

    public data class Distribution internal constructor(
        override val name: String,
        override val reportType: String,
        override val aggregations: List<NumericAgg>,
        override val internal: Boolean,
    ) : Metric() {
        override val metricType = MetricType.GAUGE
        override val dataType = DataType.DOUBLE
        override val carryOverValue = false

        @JvmOverloads
        public fun record(value: Double, timestamp: Long = timestamp()) {
            add(timeMs = timestamp, numberVal = value)
        }

        @JvmOverloads
        public fun record(value: Long, timestamp: Long = timestamp()) {
            add(timeMs = timestamp, numberVal = value.toDouble())
        }
    }

    public data class Event internal constructor(
        override val name: String,
        override val reportType: String,
        private val countInReport: Boolean,
        override val internal: Boolean,
    ) : Metric() {
        override val metricType = MetricType.EVENT
        override val dataType = DataType.STRING
        override val carryOverValue = false
        override val aggregations = if (countInReport) listOf(COUNT) else emptyList()

        @JvmOverloads
        public fun add(value: String, timestamp: Long = timestamp()): Unit = add(timeMs = timestamp, stringVal = value)
    }
}

private fun Boolean.asNumber() = if (this) 1.0 else 0.0

public interface AggregationType

public enum class NumericAgg : AggregationType {
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

    LATEST_VALUE,
    // Future: more aggregations e.g. Std Dev, Percentile
}

public enum class StateAgg : AggregationType {
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

public enum class DataType(
    public val value: String,
) {
    DOUBLE("double"),
    STRING("string"),
    BOOLEAN("boolean"),
}

public enum class MetricType(
    public val value: String,
) {
    COUNTER("counter"),
    GAUGE("gauge"),
    PROPERTY("property"),
    EVENT("event"),
}
