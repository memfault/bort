package com.memfault.bort.reporting

import com.memfault.bort.reporting.DataType.DOUBLE
import com.memfault.bort.reporting.DataType.STRING
import com.memfault.bort.reporting.MetricType.COUNTER
import com.memfault.bort.reporting.MetricType.EVENT
import com.memfault.bort.reporting.MetricType.GAUGE
import com.memfault.bort.reporting.MetricType.PROPERTY
import com.memfault.bort.reporting.MetricValue.MetricJsonFields.REPORTING_CLIENT_VERSION
import com.memfault.bort.reporting.NumericAgg.COUNT
import com.memfault.bort.reporting.NumericAgg.SUM
import com.memfault.bort.reporting.RemoteMetricsService.FinishReport

/**
 * Entry point to Memfault's Reporting library.
 *
 * Sample usage:
 *
 * ```
 * val counter = Reporting.report()
 *     .counter("api-count", sumInReport=true)
 * ...
 * counter.increment()
 * ```
 *
 * See [Custom Metrics](https://mflt.io/android-custom-metrics) for more information.
 */
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
    ): Boolean =
        RemoteMetricsService.finishReport(FinishReport(timeMs, REPORTING_CLIENT_VERSION, reportType, startNextReport))

    // Bort heartbeat will call RemoteMetricsService.finishReport() using this report name.
    private const val HEARTBEAT_REPORT: String = "Heartbeat"

    private fun timestamp(): Long = System.currentTimeMillis()

    public class Report internal constructor(public val reportType: String) {
        /**
         * Aggregates the total count at the end of the period.
         *
         * @param name the name of the metric.
         * @param sumInReport if true, includes the sum of all counts in the heartbeat report.
         */
        @JvmOverloads
        public fun counter(
            name: String,
            sumInReport: Boolean = true,
            internal: Boolean = false,
        ): Counter = Counter(name = name, reportType = reportType, internal = internal, sumInReport = sumInReport)

        /**
         * All-purpose success and failure counter for any sync-like events.
         *
         * @param sumInReport if true, includes the sum of successes and failures in the heartbeat report.
         */
        @JvmOverloads
        public fun sync(
            sumInReport: Boolean = true,
        ): SuccessOrFailure = successOrFailure(
            name = "sync",
            sumInReport = sumInReport,
            internal = false,
        )

        /**
         * Counts the number of success and failures of a custom metric type in the period.
         *
         * Prefer using underscores as separators in the metric name and avoiding spaces.
         *
         * @param name the name of the metric.
         * @param sumInReport if true, includes the sum of successes and failures in the heartbeat report.
         */
        @JvmOverloads
        public fun successOrFailure(
            name: String,
            sumInReport: Boolean = true,
            internal: Boolean = false,
        ): SuccessOrFailure {
            check(name.isNotBlank()) { "Name '$name' must not be blank." }

            val successCounter = counter(
                name = "${name}_successful",
                sumInReport = sumInReport,
                internal = internal,
            )

            val failureCounter = counter(
                name = "${name}_failure",
                sumInReport = sumInReport,
                internal = internal,
            )

            return SuccessOrFailure(successCounter, failureCounter)
        }

        /**
         * Keeps track of a distribution of the values recorded during the period.
         *
         * @param name the name of the metric.
         * @param aggregations a list of [NumericAgg]regations to perform on the values recorded during the heartbeat
         *                     period, included as metrics in the heartbeat report.
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
         *
         * @param name the name of the metric.
         * @param aggregations a list of [StateAgg]regations to perform on the values recorded during the heartbeat
         *                     period, included as metrics in the heartbeat report.
         */
        @JvmOverloads
        public fun <T : Enum<T>> stateTracker(
            name: String,
            aggregations: List<StateAgg> = listOf(),
            internal: Boolean = false,
        ): StateTracker<T> = StateTracker(
            name = name,
            reportType = reportType,
            aggregations = aggregations,
            internal = internal,
        )

        /**
         * Tracks total time spent in each state during the report period.
         *
         * For use with string representations of state.
         *
         * @param name the name of the metric.
         * @param aggregations a list of [StateAgg]regations to perform on the values recorded during the heartbeat
         *                     period, included as metrics in the heartbeat report.
         */
        @JvmOverloads
        public fun stringStateTracker(
            name: String,
            aggregations: List<StateAgg> = listOf(),
            internal: Boolean = false,
        ): StringStateTracker = StringStateTracker(
            name = name,
            reportType = reportType,
            aggregations = aggregations,
            internal = internal,
        )

        /**
         * Tracks total time spent in each state during the report period.
         *
         * For use with boolean representations of state.
         *
         * @param name the name of the metric.
         * @param aggregations a list of [StateAgg]regations to perform on the values recorded during the heartbeat
         *                     period, included as metrics in the heartbeat report.
         */
        @JvmOverloads
        public fun boolStateTracker(
            name: String,
            aggregations: List<StateAgg> = listOf(),
            internal: Boolean = false,
        ): BoolStateTracker = BoolStateTracker(
            name = name,
            reportType = reportType,
            aggregations = aggregations,
            internal = internal,
        )

        /**
         * Keep track of the latest value of a string property.
         *
         * @param name the name of the metric.
         * @param addLatestToReport if true, includes the latest value of the metric in the heartbeat report.
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
         * Keep track of the latest value of a number property.
         *
         * @param name the name of the metric.
         * @param addLatestToReport if true, includes the latest value of the metric in the heartbeat report.
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
         *
         * @param name the name of the metric.
         * @param countInReport if true, includes a count of the number of events reported during the heartbeat period,
         *                      in the the heartbeat report.
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
    }

    /**
     * Convenience method for Java callers instead of using `asList()`.
     */
    public fun numericAggs(vararg aggregations: NumericAgg): List<NumericAgg> = aggregations.asList()

    /**
     * Convenience method for Java callers instead of using `asList()`.
     */
    public fun statsAggs(vararg aggregations: StateAgg): List<StateAgg> = aggregations.asList()

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
                    name,
                    reportType,
                    aggregations,
                    internal,
                    metricType,
                    dataType,
                    carryOverValue,
                    timeMs,
                    stringVal,
                    numberVal,
                    boolVal,
                    REPORTING_CLIENT_VERSION,
                ),
            )
        }
    }

    public data class StringProperty internal constructor(
        override val name: String,
        override val reportType: String,
        override val internal: Boolean,
        private val addLatestToReport: Boolean,
    ) : Metric() {
        override val aggregations = if (addLatestToReport) listOf(StateAgg.LATEST_VALUE) else emptyList()
        override val metricType = PROPERTY
        override val dataType = STRING
        override val carryOverValue = true

        @JvmOverloads
        public fun update(
            value: String?,
            timestamp: Long = timestamp(),
        ): Unit =
            add(timeMs = timestamp, stringVal = value ?: "")
    }

    public data class NumberProperty internal constructor(
        override val name: String,
        override val reportType: String,
        override val internal: Boolean,
        private val addLatestToReport: Boolean,
    ) : Metric() {
        override val aggregations = if (addLatestToReport) listOf(NumericAgg.LATEST_VALUE) else emptyList()
        override val metricType = PROPERTY
        override val dataType = DOUBLE
        override val carryOverValue = true

        @JvmOverloads
        public fun update(
            value: Double?,
            timestamp: Long = timestamp(),
        ): Unit =
            add(timeMs = timestamp, numberVal = value)

        @JvmOverloads
        public fun update(
            value: Float?,
            timestamp: Long = timestamp(),
        ): Unit =
            add(timeMs = timestamp, numberVal = value?.toDouble())

        @JvmOverloads
        public fun update(
            value: Long?,
            timestamp: Long = timestamp(),
        ): Unit =
            add(timeMs = timestamp, numberVal = value?.toDouble())

        @JvmOverloads
        public fun update(
            value: Int?,
            timestamp: Long = timestamp(),
        ): Unit =
            add(timeMs = timestamp, numberVal = value?.toDouble())

        @JvmOverloads
        public fun update(
            value: Boolean?,
            timestamp: Long = timestamp(),
        ): Unit =
            add(timeMs = timestamp, numberVal = value?.asNumber())
    }

    public class SuccessOrFailure internal constructor(
        private val successCounter: Counter,
        private val failureCounter: Counter,
    ) {
        public fun record(successful: Boolean) {
            if (successful) {
                success()
            } else {
                failure()
            }
        }

        public fun success(): Unit = successCounter.increment()

        public fun failure(): Unit = failureCounter.increment()
    }

    public data class Counter internal constructor(
        override val name: String,
        override val reportType: String,
        override val internal: Boolean,
        private val sumInReport: Boolean,
    ) : Metric() {
        override val aggregations = if (sumInReport) listOf(SUM) else emptyList()
        override val metricType = COUNTER
        override val dataType = DOUBLE
        override val carryOverValue = false

        @JvmOverloads
        public fun incrementBy(
            byDouble: Double = 1.0,
            timestamp: Long = timestamp(),
        ) {
            add(timeMs = timestamp, numberVal = byDouble)
        }

        @JvmName("incrementByInt")
        @JvmOverloads
        public fun incrementBy(
            by: Int = 1,
            timestamp: Long = timestamp(),
        ) {
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
        override val metricType = PROPERTY
        override val dataType = STRING
        override val carryOverValue = true

        @JvmOverloads
        public fun state(
            state: T?,
            timestamp: Long = timestamp(),
        ) {
            add(timeMs = timestamp, stringVal = state?.name ?: "")
        }
    }

    public data class StringStateTracker internal constructor(
        override val name: String,
        override val reportType: String,
        override val aggregations: List<StateAgg>,
        override val internal: Boolean,
    ) : Metric() {
        override val metricType = PROPERTY
        override val dataType = STRING
        override val carryOverValue = true

        @JvmOverloads
        public fun state(
            state: String?,
            timestamp: Long = timestamp(),
        ) {
            add(timeMs = timestamp, stringVal = state ?: "")
        }
    }

    public data class BoolStateTracker internal constructor(
        override val name: String,
        override val reportType: String,
        override val aggregations: List<StateAgg>,
        override val internal: Boolean,
    ) : Metric() {
        override val metricType = PROPERTY
        override val dataType = DataType.BOOLEAN
        override val carryOverValue = true

        @JvmOverloads
        public fun state(
            state: Boolean,
            timestamp: Long = timestamp(),
        ) {
            add(timeMs = timestamp, boolVal = state)
        }
    }

    public data class Distribution internal constructor(
        override val name: String,
        override val reportType: String,
        override val aggregations: List<NumericAgg>,
        override val internal: Boolean,
    ) : Metric() {
        override val metricType = GAUGE
        override val dataType = DOUBLE
        override val carryOverValue = false

        @JvmOverloads
        public fun record(
            value: Double,
            timestamp: Long = timestamp(),
        ) {
            add(timeMs = timestamp, numberVal = value)
        }

        @JvmOverloads
        public fun record(
            value: Long,
            timestamp: Long = timestamp(),
        ) {
            add(timeMs = timestamp, numberVal = value.toDouble())
        }
    }

    public data class Event internal constructor(
        override val name: String,
        override val reportType: String,
        private val countInReport: Boolean,
        override val internal: Boolean,
    ) : Metric() {
        override val metricType = EVENT
        override val dataType = STRING
        override val carryOverValue = false
        override val aggregations = if (countInReport) listOf(COUNT) else emptyList()

        @JvmOverloads
        public fun add(
            value: String,
            timestamp: Long = timestamp(),
        ): Unit = add(timeMs = timestamp, stringVal = value)
    }
}

private fun Boolean.asNumber() = if (this) 1.0 else 0.0
