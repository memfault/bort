package com.memfault.bort.time

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.memfault.bort.shared.PreferenceKeyProvider
import com.memfault.bort.tokenbucket.realElapsedRealtime
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.time.minutes
import kotlin.time.toDuration
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Continuously tracks device uptime (every [UPDATE_PERIOD]), while the bort process is running.
 *
 * If Bort process dies, than whenever it starts again (i.e. when any scheduled job fires, or any dropbox entry is
 * received) then we will continue to track uptime.
 */
class UptimeTracker(
    prefs: SharedPreferences,
    private val linuxBootId: String,
) {
    private val previousUptimePrefMillis = PreviousUptimePreference(prefs)
    private val currentUptimePrefMillis = CurrentUptimePreference(prefs)
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Starts tracking device uptime. Call once when Bort process starts.
     */
    fun trackUptimeOnStart() {
        val storedUptime = currentUptimePrefMillis.get()
        // If boot ID is different, then store previous uptime.
        if (storedUptime.bootId != linuxBootId) {
            previousUptimePrefMillis.setValue(storedUptime.uptimeMillis)
        }
        trackCurrentUptime()
    }

    fun getPreviousUptime() = previousUptimePrefMillis.getValue().toDuration(MILLISECONDS)

    private fun trackCurrentUptime() {
        currentUptimePrefMillis.setValue(
            UptimeWithBootId(
                bootId = linuxBootId,
                uptimeMillis = realElapsedRealtime().toLongMilliseconds()
            )
        )
        // Run again, after UPDATE_PERIOD.
        handler.postDelayed(::trackCurrentUptime, UPDATE_PERIOD.toLongMilliseconds())
    }
}

@Serializable
data class UptimeWithBootId(
    val bootId: String,
    val uptimeMillis: Long,
)

private class CurrentUptimePreference(
    prefs: SharedPreferences,
) : PreferenceKeyProvider<String>(
    sharedPreferences = prefs,
    defaultValue = DEFAULT_PREVIOUS_BOOT_UPTIME,
    preferenceKey = CURRENT_UPTIME_KEY
) {
    fun setValue(uptime: UptimeWithBootId) = setValue(Json.encodeToString(UptimeWithBootId.serializer(), uptime))
    fun get(): UptimeWithBootId = Json.decodeFromString(UptimeWithBootId.serializer(), getValue())
}

private class PreviousUptimePreference(
    prefs: SharedPreferences,
) : PreferenceKeyProvider<Long>(sharedPreferences = prefs, defaultValue = 0, preferenceKey = PREVIOUS_UPTIME_KEY)

private const val PREVIOUS_UPTIME_KEY = "previous_uptime"
private const val CURRENT_UPTIME_KEY = "current_uptime"
private val UPDATE_PERIOD = 10.minutes
private val DEFAULT_PREVIOUS_BOOT_UPTIME = Json.encodeToString(UptimeWithBootId.serializer(), UptimeWithBootId("", 0))
