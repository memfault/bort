package com.memfault.bort.metrics

import android.app.usage.UsageEvents.Event
import android.app.usage.UsageStatsManager
import com.memfault.bort.metrics.UsageEvent.DEVICE_SHUTDOWN
import com.memfault.bort.metrics.UsageEvent.DEVICE_STARTUP
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.time.AbsoluteTime
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours

class UsageStatsCollector @Inject constructor(
    private val usageStatsManager: UsageStatsManager,
) {
    fun collectUsageStats(from: AbsoluteTime?, to: AbsoluteTime) {
        val start = from?.timestamp ?: to.minus(FALLBACK_REPORT_DURATION).timestamp
        val events = usageStatsManager.queryEvents(start.toEpochMilli(), to.timestamp.toEpochMilli())
        val event = Event()
        while (events.getNextEvent(event)) {
            val eventType = UsageEvent.fromInt(event.eventType)
            if (event.packageName == DEVICE_EVENT_PACKAGE_NAME) {
                when (eventType) {
                    DEVICE_SHUTDOWN -> METRIC.add(value = EVENT_SHUTDOWN, timestamp = event.timeStamp)
                    DEVICE_STARTUP -> METRIC.add(value = EVENT_STARTED, timestamp = event.timeStamp)
                    else -> Unit
                }
            }
        }
    }

    companion object {
        private val METRIC = Reporting.report().event("device-powered", countInReport = false)
        private const val EVENT_SHUTDOWN = "shutdown"
        private const val EVENT_STARTED = "booted"
        private val FALLBACK_REPORT_DURATION = 2.hours

        // Hidden constant in UsageEvents.java
        private const val DEVICE_EVENT_PACKAGE_NAME: String = "android"
    }
}

/**
 * Copied form constants in UsageEvents.Event.
 */
enum class UsageEvent(val code: Int) {
    NONE(0),
    MOVE_TO_FOREGROUND(1),
    MOVE_TO_BACKGROUND(2),
    ACTIVITY_PAUSED(2),
    END_OF_DAY(3),
    CONTINUE_PREVIOUS_DAY(4),
    CONFIGURATION_CHANGE(5),
    SYSTEM_INTERACTION(6),
    USER_INTERACTION(7),
    SHORTCUT_INVOCATION(8),
    CHOOSER_ACTION(9),
    NOTIFICATION_SEEN(10),
    STANDBY_BUCKET_CHANGED(11),
    NOTIFICATION_INTERRUPTION(12),
    SLICE_PINNED_PRIV(13),
    SLICE_PINNED(14),
    SCREEN_INTERACTIVE(15),
    SCREEN_NON_INTERACTIVE(16),
    KEYGUARD_SHOWN(17),
    KEYGUARD_HIDDEN(18),
    FOREGROUND_SERVICE_START(19),
    FOREGROUND_SERVICE_STOP(20),
    CONTINUING_FOREGROUND_SERVICE(21),
    ROLLOVER_FOREGROUND_SERVICE(22),
    ACTIVITY_STOPPED(23),
    ACTIVITY_DESTROYED(24),
    FLUSH_TO_DISK(25),
    DEVICE_SHUTDOWN(26),
    DEVICE_STARTUP(27),
    USER_UNLOCKED(28),
    USER_STOPPED(29),
    LOCUS_ID_SET(30),
    APP_COMPONENT_USED(31),
    ;

    companion object {
        private val map = UsageEvent.entries.associateBy { it.code }
        fun fromInt(type: Int) = map[type]
    }
}
