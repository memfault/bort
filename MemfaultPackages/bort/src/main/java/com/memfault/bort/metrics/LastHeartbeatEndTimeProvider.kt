package com.memfault.bort.metrics

import android.content.SharedPreferences
import com.memfault.bort.BortJson
import com.memfault.bort.PREFERENCE_LAST_HEARTBEAT_END_TIME_JSON
import com.memfault.bort.shared.PreferenceKeyProvider
import com.memfault.bort.time.BaseLinuxBootRelativeTime
import com.memfault.bort.time.LinuxBootRelativeTime
import com.memfault.bort.time.boxed
import kotlin.time.milliseconds

/**
 * Provides the end time (expressed as uptime in milliseconds since boot) of the previously collected heartbeat.
 * This should be used as the start of the the next heartbeat to collect.
 */
interface LastHeartbeatEndTimeProvider {
    var lastEnd: BaseLinuxBootRelativeTime
}

class RealLastHeartbeatEndTimeProvider(
    sharedPreferences: SharedPreferences
) : LastHeartbeatEndTimeProvider, PreferenceKeyProvider<String>(
    sharedPreferences = sharedPreferences,
    defaultValue = BortJson.encodeToString(LinuxBootRelativeTime.serializer(), DEFAULT_LAST_END),
    preferenceKey = PREFERENCE_LAST_HEARTBEAT_END_TIME_JSON,
) {
    override var lastEnd
        get() = BortJson.decodeFromString(
            LinuxBootRelativeTime.serializer(), super.getValue()
        ) as BaseLinuxBootRelativeTime
        set(value) = super.setValue(
            BortJson.encodeToString(LinuxBootRelativeTime.serializer(), LinuxBootRelativeTime(value))
        )
}

private val DEFAULT_LAST_END = LinuxBootRelativeTime(0.milliseconds.boxed(), 0.milliseconds.boxed(), "")
