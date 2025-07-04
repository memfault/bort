package com.memfault.bort.metrics

import android.content.SharedPreferences
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.ReportingClient.Counter
import com.memfault.bort.settings.OperationalCrashesComponentGroupsProvider
import com.memfault.bort.shared.SerializedCachedPreferenceKeyProvider
import com.memfault.bort.time.BootRelativeTimeProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.Serializable
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.DurationUnit.MILLISECONDS
import kotlin.time.toDuration

interface CrashHandler {
    suspend fun onCrash(componentName: String?, crashTimestamp: Instant)
    fun onBoot()
    fun process()
}

interface CrashFreeHoursStorage {
    var state: CrashFreeHoursState
}

@Singleton
@ContributesBinding(SingletonComponent::class, boundType = CrashFreeHoursStorage::class)
class RealCrashFreeHoursStorage @Inject constructor(
    sharedPreferences: SharedPreferences,
) : SerializedCachedPreferenceKeyProvider<CrashFreeHoursState>(
    sharedPreferences,
    CrashFreeHoursState(),
    CrashFreeHoursState.serializer(),
    "CRASH_FREE_HOURS",
),
    CrashFreeHoursStorage

@Serializable
data class CrashFreeHoursState(
    /** Start of the current tracked period. This is updated when a period finishes. */
    val hourStartedAtElapsedRealtimeMs: Long = 0,
    /** Was there a crash in the last hour? */
    val lastHourHadCrash: Boolean = false,
)

class CrashFreeHoursMetricLogger @Inject constructor() {
    fun incrementOperationalHours(hours: Int) {
        OPERATIONAL_HOURS_METRIC.incrementBy(by = hours)
    }

    fun incrementCrashFreeHours(hours: Int) {
        CRASH_FREE_HOURS_METRIC.incrementBy(by = hours)
    }

    fun incrementCrashes(componentName: String?, timestamp: Long) {
        if (componentName?.isNotBlank() == true) {
            Reporting.report()
                .counter(name = "${OPERATIONAL_CRASHES_METRIC_KEY}_$componentName")
                .increment(timestamp = timestamp)
        } else {
            OPERATIONAL_CRASHES_METRIC
                .increment(timestamp = timestamp)
        }
    }

    companion object {
        fun dropBoxTagCountMetric(tag: String): String = "drop_box_${tag}_count"
        fun dropBoxTagCounter(tag: String): Counter = Reporting.report().counter(dropBoxTagCountMetric(tag))
        const val OPERATIONAL_CRASHES_METRIC_KEY = "operational_crashes"
        const val OPERATIONAL_HOURS_METRIC_KEY = "operational_hours"
        const val CRASH_FREE_HOURS_METRIC_KEY = "operational_crashfree_hours"
        private val OPERATIONAL_CRASHES_METRIC = Reporting.report().counter(OPERATIONAL_CRASHES_METRIC_KEY)
        private val OPERATIONAL_HOURS_METRIC = Reporting.report().counter(OPERATIONAL_HOURS_METRIC_KEY)
        private val CRASH_FREE_HOURS_METRIC = Reporting.report().counter(CRASH_FREE_HOURS_METRIC_KEY)
    }
}

/**
 * Tracks crashes vs uptime, in 1-hour chunks.
 *
 * On [onBoot], initialize [storage] with [thisHourHadCrash] = false.
 *
 * On every crash, [onCrash] is called, which will flag the current hour with a crash.
 *
 * Periodically (usually every 15 minutes) [process] will be called. This will increment the metric counters.
 */
@Singleton
@ContributesBinding(SingletonComponent::class)
class CrashFreeHours @Inject constructor(
    private val timeProvider: BootRelativeTimeProvider,
    private val storage: CrashFreeHoursStorage,
    private val metricLogger: CrashFreeHoursMetricLogger,
    private val operationalCrashesComponentGroupsProvider: OperationalCrashesComponentGroupsProvider,
) : CrashHandler {
    override suspend fun onCrash(componentName: String?, crashTimestamp: Instant) {
        val crashTimestampMs = crashTimestamp.toEpochMilli()

        // Always record an operational crash
        metricLogger.incrementCrashes(componentName = null, timestamp = crashTimestampMs)

        // If there are operational crash component groups, record a crash if the crash component matches the pattern.
        val component = componentName.orEmpty()
        val componentGroups = operationalCrashesComponentGroupsProvider()
        componentGroups.groups
            .forEach { group ->
                if (group.patterns.isEmpty() ||
                    group.patterns.any { pattern -> pattern.toRegex().matches(component) }
                ) {
                    metricLogger.incrementCrashes(componentName = group.name, timestamp = crashTimestampMs)
                    return@forEach
                }
            }

        // Call process before setting the crash flag - this ensures that any previous crash-free hours are processed,
        // before marking the current hour as lastHourHadCrash.
        process()
        storage.state = storage.state.copy(lastHourHadCrash = true)
    }

    override fun onBoot() {
        storage.state = CrashFreeHoursState()
    }

    override fun process() {
        val now = timeProvider.now().elapsedRealtime.duration
        val elapsed = now - storage.state.hourStartedAtElapsedRealtimeMs.toDuration(MILLISECONDS)
        val elapsedHours = elapsed.inWholeHours.toInt()

        if (elapsedHours > 0) {
            metricLogger.incrementOperationalHours(elapsedHours)
            // If there was a crash, then process was already called (i.e. there will only be one crashy hour).
            val crashFreeHours = if (storage.state.lastHourHadCrash) {
                elapsedHours - 1
            } else {
                elapsedHours
            }
            if (crashFreeHours > 0) {
                metricLogger.incrementCrashFreeHours(crashFreeHours)
            }
            storage.state = CrashFreeHoursState(
                hourStartedAtElapsedRealtimeMs = storage.state.hourStartedAtElapsedRealtimeMs +
                    elapsedHours.hours.inWholeMilliseconds,
                lastHourHadCrash = false,
            )
        }
    }
}
