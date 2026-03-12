package com.memfault.bort.reporting

import android.app.Application
import android.content.Context
import android.os.SystemClock
import com.memfault.bort.reporting.DataType.DOUBLE
import com.memfault.bort.reporting.DataType.STRING
import com.memfault.bort.reporting.MetricType.COUNTER
import com.memfault.bort.reporting.MetricType.EVENT
import com.memfault.bort.reporting.MetricType.GAUGE
import com.memfault.bort.reporting.MetricType.PROPERTY
import com.memfault.bort.reporting.MetricValue.MetricJsonFields.REPORTING_CLIENT_VERSION
import com.memfault.bort.reporting.NumericAgg.COUNT
import com.memfault.bort.reporting.NumericAgg.SUM
import com.memfault.bort.reporting.RemoteMetricsService.HEARTBEAT_REPORT
import com.memfault.bort.reporting.RemoteMetricsService.SESSION_REPORT
import com.memfault.bort.reporting.ReportingClient.Report
import com.memfault.bort.reporting.ReportingClient.SessionReport
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

private fun timestamp(): Long = System.currentTimeMillis()
private fun uptime(): Long = SystemClock.elapsedRealtime()

/**
 * Common interface for the Memfault Reporting library.
 */
public interface ReportingSdk {

    /**
     * Default report. Values are aggregated using the default heartbeat period.
     */
    public fun report(): Report

    /**
     * Session. Values are aggregated by the length of each individual session with the same name.
     *
     * Session [name]s must match [RemoteMetricsService.SESSION_NAME_REGEX] and not be a
     * [RemoteMetricsService.RESERVED_REPORT_NAMES].
     */
    public fun session(name: String): SessionReport

    /**
     * Start a session.
     *
     * Session [name]s must match [RemoteMetricsService.SESSION_NAME_REGEX] and not be a
     * [RemoteMetricsService.RESERVED_REPORT_NAMES].
     */
    @Deprecated("Use [SessionReport.start()]")
    public fun startSession(
        name: String,
        timestampMs: Long = timestamp(),
    ): CompletableFuture<Boolean>

    /**
     * Finish a session.
     */
    @Deprecated("Use [SessionReport.finish()]")
    public fun finishSession(
        name: String,
        timestampMs: Long = timestamp(),
    ): CompletableFuture<Boolean>

    /**
     * Shuts down the executor backing the Reporting library. The instance of the Reporting library will not process
     * any more metrics.
     */
    public fun shutdown()
}

/**
 * Simplest entry point into Memfault's Reporting library.
 *
 * Uses a singleton to record metrics into Memfault. By default, registers a ContentProvider to capture the static
 * app context, and executes all requests on an [Executors.newSingleThreadExecutor].
 *
 * For more configuration options, use [ReportingClient].
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
// For internal Memfault development, immediate is set to true for backwards compatibility. In the published library,
// the Reporting object will now default to using a single thread executor, while the ReportingClient class is now
// available if the less main-thread safe, immediate = true behavior is desired.
public object Reporting : ReportingSdk by ReportingClient(
    context = null,
    executorService = null,
    immediate = !BuildConfig.PUBLISHING,
)

/**
 * Configurable entry point into Memfault's Reporting library.
 *
 * Provides a customizable class that can be used to record metrics into Memfault.
 *
 * - Pass in an Application Context to allow the ability to hide the MetricsInitProvider node in your
 * AndroidManifest.xml, and avoid using a static Context.
 * - Pass in a custom ExecutorService to allow scheduling the IPC calls on a different thread. If none is provided,
 * work will be scheduled on an [Executors.newSingleThreadExecutor] by default, unless [immediate] is set to true.
 *
 * Sample usage:
 *
 * ```
 * val reportingClient = ReportingClient(context = application)
 *
 * val counter = reportingClient
 *     .counter("api-count", sumInReport=true)
 * ...
 * counter.increment()
 * ```
 *
 * See [Custom Metrics](https://mflt.io/android-custom-metrics) for more information.
 */
public class ReportingClient @JvmOverloads constructor(
    context: Context?,
    executorService: ExecutorService?,
    immediate: Boolean = false,
) : ReportingSdk {
    init {
        require(context == null || context is Application) {
            "A provided $context must be the Application Context."
        }
    }

    private val remoteMetricsService: RemoteMetricsService by lazy {
        RemoteMetricsService.Builder()
            .context(context)
            .executorService(executorService)
            .immediate(immediate)
            .build()
    }

    public override fun report(): Report = HeartbeatReport(
        remoteMetricsService = remoteMetricsService,
    )

    public override fun session(name: String): SessionReport = SessionReport(
        remoteMetricsService = remoteMetricsService,
        reportName = name,
    )

    @Deprecated("Use [SessionReport.start()]")
    public override fun startSession(
        name: String,
        timestampMs: Long,
    ): CompletableFuture<Boolean> = remoteMetricsService.startReport(
        StartReport(
            timestampMs,
            uptime(),
            REPORTING_CLIENT_VERSION,
            SESSION_REPORT,
            name,
        ),
    )

    /**
     * Finish a session.
     */
    @Deprecated("Use [SessionReport.finish()]")
    public override fun finishSession(
        name: String,
        timestampMs: Long,
    ): CompletableFuture<Boolean> = remoteMetricsService.finishReport(
        FinishReport(
            timestampMs,
            uptime(),
            REPORTING_CLIENT_VERSION,
            SESSION_REPORT,
            /** startNextReport */
            false,
            name,
        ),
    )

    public override fun shutdown(): Unit = remoteMetricsService.shutdown()

    public class SessionReport internal constructor(
        remoteMetricsService: RemoteMetricsService,
        reportName: String,
    ) : Report(remoteMetricsService = remoteMetricsService, reportType = SESSION_REPORT, reportName = reportName) {
        public fun start(): CompletableFuture<Boolean> = start(timestamp(), uptime())

        @Deprecated(
            message = "Consider passing an uptime in addition to the timestamp or the current uptime will be used",
            replaceWith = ReplaceWith("start(timestamp, uptime)"),
        )
        public fun start(
            timestampMs: Long,
        ): CompletableFuture<Boolean> = start(timestampMs, uptime())

        public fun start(
            timestampMs: Long,
            uptimeMs: Long,
        ): CompletableFuture<Boolean> = remoteMetricsService.startReport(
            StartReport(
                timestampMs,
                uptimeMs,
                REPORTING_CLIENT_VERSION,
                SESSION_REPORT,
                reportName,
            ),
        )

        public fun finish(): CompletableFuture<Boolean> = finish(timestamp(), uptime())

        @Deprecated(
            message = "Consider passing an uptime in addition to the timestamp or the current uptime will be used",
            replaceWith = ReplaceWith("finish(timestamp, uptime)"),
        )
        public fun finish(timestampMs: Long): CompletableFuture<Boolean> = finish(timestampMs, uptime())

        public fun finish(timestampMs: Long, uptimeMs: Long): CompletableFuture<Boolean> =
            remoteMetricsService.finishReport(
                FinishReport(
                    timestampMs,
                    uptimeMs,
                    REPORTING_CLIENT_VERSION,
                    SESSION_REPORT,
                    /** startNextReport */
                    false,
                    reportName,
                ),
            )
    }

    public class HeartbeatReport internal constructor(
        remoteMetricsService: RemoteMetricsService,
    ) : Report(remoteMetricsService = remoteMetricsService, reportType = HEARTBEAT_REPORT)

    public sealed class Report protected constructor(
        protected val remoteMetricsService: RemoteMetricsService,
        public val reportType: String,
        public val reportName: String? = null,
    ) {
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
        ): Counter = Counter(
            remoteMetricsService = remoteMetricsService,
            name = name,
            reportType = reportType,
            reportName = reportName,
            internal = internal,
            sumInReport = sumInReport,
        )

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
            remoteMetricsService = remoteMetricsService,
            name = name,
            reportType = reportType,
            reportName = reportName,
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
            remoteMetricsService = remoteMetricsService,
            name = name,
            reportType = reportType,
            reportName = reportName,
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
            remoteMetricsService = remoteMetricsService,
            name = name,
            reportType = reportType,
            reportName = reportName,
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
            remoteMetricsService = remoteMetricsService,
            name = name,
            reportType = reportType,
            reportName = reportName,
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
            remoteMetricsService = remoteMetricsService,
            name = name,
            reportType = reportType,
            reportName = reportName,
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
            remoteMetricsService = remoteMetricsService,
            name = name,
            reportType = reportType,
            reportName = reportName,
            addLatestToReport = addLatestToReport,
            internal = internal,
        )

        /**
         * Track individual events. Replacement for Custom Events.
         *
         * @param name the name of the metric.
         * @param countInReport if true, includes a count of the number of events reported during the heartbeat period,
         *                      in the heartbeat report.
         * @param latestInReport if true, includes the latest event reported during the heartbeat period, in the
         *                       heartbeat report.
         */
        @JvmOverloads
        public fun event(
            name: String,
            countInReport: Boolean = false,
            latestInReport: Boolean = false,
            internal: Boolean = false,
        ): Event = Event(
            remoteMetricsService = remoteMetricsService,
            name = name,
            reportType = reportType,
            reportName = reportName,
            countInReport = countInReport,
            latestInReport = latestInReport,
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
        internal abstract val remoteMetricsService: RemoteMetricsService
        internal abstract val name: String
        internal abstract val reportType: String
        internal abstract val reportName: String?
        internal abstract val aggregations: List<AggregationType>
        internal abstract val internal: Boolean
        internal abstract val metricType: MetricType
        internal abstract val dataType: DataType
        internal abstract val carryOverValue: Boolean

        protected fun add(
            timeMs: Long,
            uptimeMs: Long,
            stringVal: String? = null,
            numberVal: Double? = null,
            boolVal: Boolean? = null,
        ): CompletableFuture<Void> {
            // Sends entire metric definition + current value over IPC to the logging/metrics daemon.
            return remoteMetricsService.record(
                MetricValue(
                    name,
                    reportType,
                    aggregations,
                    internal,
                    metricType,
                    dataType,
                    carryOverValue,
                    timeMs,
                    uptimeMs,
                    stringVal,
                    numberVal,
                    boolVal,
                    REPORTING_CLIENT_VERSION,
                    reportName,
                ),
            )
        }
    }

    public data class StringProperty internal constructor(
        override val remoteMetricsService: RemoteMetricsService,
        override val name: String,
        override val reportType: String,
        override val reportName: String?,
        override val internal: Boolean,
        private val addLatestToReport: Boolean,
    ) : Metric() {
        override val aggregations = if (addLatestToReport) listOf(StateAgg.LATEST_VALUE) else emptyList()
        override val metricType = PROPERTY
        override val dataType = STRING
        override val carryOverValue = true

        public fun update(
            value: String?,
        ): CompletableFuture<Void> =
            add(timeMs = timestamp(), uptimeMs = uptime(), stringVal = value ?: "")

        @Deprecated(
            message = "Consider passing an uptime in addition to the timestamp or the current uptime will be used",
            replaceWith = ReplaceWith("update(value, timestamp, uptime)"),
        )
        public fun update(
            value: String?,
            timestamp: Long,
        ): CompletableFuture<Void> =
            add(timeMs = timestamp, uptimeMs = uptime(), stringVal = value ?: "")

        public fun update(
            value: String?,
            timestamp: Long,
            uptime: Long,
        ): CompletableFuture<Void> =
            add(timeMs = timestamp, uptimeMs = uptime, stringVal = value ?: "")
    }

    public data class NumberProperty internal constructor(
        override val remoteMetricsService: RemoteMetricsService,
        override val name: String,
        override val reportType: String,
        override val reportName: String?,
        override val internal: Boolean,
        private val addLatestToReport: Boolean,
    ) : Metric() {
        override val aggregations = if (addLatestToReport) listOf(NumericAgg.LATEST_VALUE) else emptyList()
        override val metricType = PROPERTY
        override val dataType = DOUBLE
        override val carryOverValue = true

        public fun update(
            value: Number,
        ): CompletableFuture<Void> =
            add(timeMs = timestamp(), uptimeMs = uptime(), numberVal = value.toDouble())

        @Deprecated(
            message = "Consider passing an uptime in addition to the timestamp or the current uptime will be used",
            replaceWith = ReplaceWith("update(value, timestamp, uptime)"),
        )
        public fun update(
            value: Number,
            timestamp: Long,
        ): CompletableFuture<Void> =
            add(timeMs = timestamp, uptimeMs = uptime(), numberVal = value.toDouble())

        public fun update(
            value: Number,
            timestamp: Long,
            uptime: Long,
        ): CompletableFuture<Void> =
            add(timeMs = timestamp, uptimeMs = uptime, numberVal = value.toDouble())

        public fun update(
            value: Boolean,
        ): CompletableFuture<Void> =
            add(timeMs = timestamp(), uptimeMs = uptime(), numberVal = value.asNumber())

        @Deprecated(
            message = "Consider passing an uptime in addition to the timestamp or the current uptime will be used",
            replaceWith = ReplaceWith("update(value, timestamp, uptime)"),
        )
        public fun update(
            value: Boolean,
            timestamp: Long,
        ): CompletableFuture<Void> =
            add(timeMs = timestamp, uptimeMs = uptime(), numberVal = value.asNumber())

        public fun update(
            value: Boolean,
            timestamp: Long,
            uptime: Long,
        ): CompletableFuture<Void> =
            add(timeMs = timestamp, uptimeMs = uptime, numberVal = value.asNumber())
    }

    public class SuccessOrFailure internal constructor(
        private val successCounter: Counter,
        private val failureCounter: Counter,
    ) {
        public fun record(successful: Boolean): CompletableFuture<Void> =
            record(successful, timestamp(), uptime())

        @Deprecated(
            message = "Consider passing an uptime in addition to the timestamp or the current uptime will be used",
            replaceWith = ReplaceWith("record(successful, timestamp, uptime)"),
        )
        public fun record(successful: Boolean, timestamp: Long): CompletableFuture<Void> =
            record(successful, timestamp, uptime())

        public fun record(
            successful: Boolean,
            timestamp: Long,
            uptime: Long,
        ): CompletableFuture<Void> = if (successful) {
            success(timestamp = timestamp, uptime = uptime)
        } else {
            failure(timestamp = timestamp, uptime = uptime)
        }

        public fun success(): CompletableFuture<Void> = success(timestamp(), uptime())

        @Deprecated(
            message = "Consider passing an uptime in addition to the timestamp or the current uptime will be used",
            replaceWith = ReplaceWith("success(timestamp, uptime)"),
        )
        public fun success(timestamp: Long): CompletableFuture<Void> = success(timestamp, uptime())
        public fun success(timestamp: Long, uptime: Long): CompletableFuture<Void> =
            successCounter.increment(timestamp = timestamp, uptime = uptime)

        public fun failure(): CompletableFuture<Void> = failure(timestamp(), uptime())

        @Deprecated(
            message = "Consider passing an uptime in addition to the timestamp or the current uptime will be used",
            replaceWith = ReplaceWith("failure(timestamp, uptime)"),
        )
        public fun failure(timestamp: Long): CompletableFuture<Void> = failure(timestamp, uptime())
        public fun failure(timestamp: Long, uptime: Long): CompletableFuture<Void> =
            failureCounter.increment(timestamp = timestamp, uptime = uptime)
    }

    public data class Counter internal constructor(
        override val remoteMetricsService: RemoteMetricsService,
        override val name: String,
        override val reportType: String,
        override val reportName: String?,
        override val internal: Boolean,
        private val sumInReport: Boolean,
    ) : Metric() {
        override val aggregations = if (sumInReport) listOf(SUM) else emptyList()
        override val metricType = COUNTER
        override val dataType = DOUBLE
        override val carryOverValue = false

        public fun incrementBy(
            by: Number = 1.0,
        ): CompletableFuture<Void> = add(timeMs = timestamp(), uptimeMs = uptime(), numberVal = by.toDouble())

        @Deprecated(
            message = "Consider passing an uptime in addition to the timestamp or the current uptime will be used",
            replaceWith = ReplaceWith("incrementBy(value, timestamp, uptime)"),
        )
        public fun incrementBy(
            by: Number = 1.0,
            timestamp: Long,
        ): CompletableFuture<Void> = add(timeMs = timestamp, uptimeMs = uptime(), numberVal = by.toDouble())

        public fun incrementBy(
            by: Number = 1.0,
            timestamp: Long,
            uptime: Long,
        ): CompletableFuture<Void> = add(timeMs = timestamp, uptimeMs = uptime, numberVal = by.toDouble())

        public fun increment(): CompletableFuture<Void> = incrementBy(
            1,
            timestamp = timestamp(),
            uptime = uptime(),
        )

        @Deprecated(
            message = "Consider passing an uptime in addition to the timestamp or the current uptime will be used",
            replaceWith = ReplaceWith("increment(timestamp, uptime)"),
        )
        public fun increment(timestamp: Long): CompletableFuture<Void> = incrementBy(
            1,
            timestamp = timestamp,
            uptime = uptime(),
        )

        public fun increment(timestamp: Long, uptime: Long): CompletableFuture<Void> = incrementBy(
            1,
            timestamp = timestamp,
            uptime = uptime,
        )
    }

    public data class StateTracker<T : Enum<T>> internal constructor(
        override val remoteMetricsService: RemoteMetricsService,
        override val name: String,
        override val reportType: String,
        override val reportName: String?,
        override val aggregations: List<StateAgg>,
        override val internal: Boolean,
    ) : Metric() {
        override val metricType = PROPERTY
        override val dataType = STRING
        override val carryOverValue = true

        public fun state(
            state: T?,
        ) {
            add(timeMs = timestamp(), uptimeMs = uptime(), stringVal = state?.name ?: "")
        }

        @Deprecated(
            message = "Consider passing an uptime in addition to the timestamp or the current uptime will be used",
            replaceWith = ReplaceWith("state(S, timestamp, uptime)"),
        )
        public fun state(
            state: T?,
            timestamp: Long,
        ) {
            add(timeMs = timestamp, uptimeMs = uptime(), stringVal = state?.name ?: "")
        }

        public fun state(
            state: T?,
            timestamp: Long,
            uptime: Long,
        ) {
            add(timeMs = timestamp, uptimeMs = uptime, stringVal = state?.name ?: "")
        }
    }

    public data class StringStateTracker internal constructor(
        override val remoteMetricsService: RemoteMetricsService,
        override val name: String,
        override val reportType: String,
        override val reportName: String?,
        override val aggregations: List<StateAgg>,
        override val internal: Boolean,
    ) : Metric() {
        override val metricType = PROPERTY
        override val dataType = STRING
        override val carryOverValue = true

        public fun state(
            state: String?,
        ) {
            add(timeMs = timestamp(), uptimeMs = uptime(), stringVal = state ?: "")
        }

        @Deprecated(
            message = "Consider passing an uptime in addition to the timestamp or the current uptime will be used",
            replaceWith = ReplaceWith("state(S, timestamp, uptime)"),
        )
        public fun state(
            state: String?,
            timestamp: Long,
        ) {
            add(timeMs = timestamp, uptimeMs = uptime(), stringVal = state ?: "")
        }

        public fun state(
            state: String?,
            timestamp: Long,
            uptime: Long,
        ) {
            add(timeMs = timestamp, uptimeMs = uptime, stringVal = state ?: "")
        }
    }

    public data class BoolStateTracker internal constructor(
        override val remoteMetricsService: RemoteMetricsService,
        override val name: String,
        override val reportType: String,
        override val reportName: String?,
        override val aggregations: List<StateAgg>,
        override val internal: Boolean,
    ) : Metric() {
        override val metricType = PROPERTY
        override val dataType = DataType.BOOLEAN
        override val carryOverValue = true

        public fun state(
            state: Boolean,
        ) {
            add(timeMs = timestamp(), uptimeMs = uptime(), boolVal = state)
        }

        @Deprecated(
            message = "Consider passing an uptime in addition to the timestamp or the current uptime will be used",
            replaceWith = ReplaceWith("state(S, timestamp, uptime)"),
        )
        public fun state(
            state: Boolean,
            timestamp: Long,
        ) {
            add(timeMs = timestamp, uptimeMs = uptime(), boolVal = state)
        }

        public fun state(
            state: Boolean,
            timestamp: Long,
            uptime: Long,
        ) {
            add(timeMs = timestamp, uptimeMs = uptime, boolVal = state)
        }
    }

    public data class Distribution internal constructor(
        override val remoteMetricsService: RemoteMetricsService,
        override val name: String,
        override val reportType: String,
        override val reportName: String?,
        override val aggregations: List<NumericAgg>,
        override val internal: Boolean,
    ) : Metric() {
        override val metricType = GAUGE
        override val dataType = DOUBLE
        override val carryOverValue = false

        public fun record(
            value: Number,
        ) {
            add(timeMs = timestamp(), uptimeMs = uptime(), numberVal = value.toDouble())
        }

        @Deprecated(
            message = "Consider passing an uptime in addition to the timestamp or the current uptime will be used",
            replaceWith = ReplaceWith("record(value, timestamp, uptime)"),
        )
        public fun record(
            value: Number,
            timestamp: Long,
        ) {
            add(timeMs = timestamp, uptimeMs = uptime(), numberVal = value.toDouble())
        }

        public fun record(
            value: Number,
            timestamp: Long,
            uptime: Long,
        ) {
            add(timeMs = timestamp, uptimeMs = uptime, numberVal = value.toDouble())
        }
    }

    public data class Event internal constructor(
        override val remoteMetricsService: RemoteMetricsService,
        override val name: String,
        override val reportType: String,
        override val reportName: String?,
        private val countInReport: Boolean,
        private val latestInReport: Boolean,
        override val internal: Boolean,
    ) : Metric() {
        override val metricType = EVENT
        override val dataType = STRING
        override val carryOverValue = false
        override val aggregations = listOfNotNull<AggregationType>(
            if (countInReport) COUNT else null,
            if (latestInReport) StateAgg.LATEST_VALUE else null,
        )

        public fun add(
            value: String,
        ): CompletableFuture<Void> = add(timeMs = timestamp(), uptimeMs = uptime(), stringVal = value)

        @Deprecated(
            message = "Consider passing an uptime in addition to the timestamp or the current uptime will be used",
            replaceWith = ReplaceWith("add(value, timestamp, uptime)"),
        )
        public fun add(
            value: String,
            timestamp: Long,
        ): CompletableFuture<Void> = add(timeMs = timestamp, uptimeMs = uptime(), stringVal = value)

        public fun add(
            value: String,
            timestamp: Long,
            uptime: Long,
        ): CompletableFuture<Void> = add(timeMs = timestamp, uptimeMs = uptime, stringVal = value)
    }
}

private fun Boolean.asNumber() = if (this) 1.0 else 0.0
