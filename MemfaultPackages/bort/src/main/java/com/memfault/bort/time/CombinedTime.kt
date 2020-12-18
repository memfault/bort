package com.memfault.bort.time

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.memfault.bort.readLinuxBootId
import java.time.Instant
import kotlin.time.milliseconds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Combination of absolute- and boot-relative time for the same instant.
 */
@Serializable
data class CombinedTime(
    @Serializable(with = DurationAsMillisecondsLong::class)
    @SerialName("uptime_ms")
    override val uptime: BoxedDuration,

    @Serializable(with = DurationAsMillisecondsLong::class)
    @SerialName("elapsed_realtime_ms")
    override val elapsedRealtime: BoxedDuration,

    @SerialName("linux_boot_id")
    override val linuxBootId: String,

    @SerialName("boot_count")
    override val bootCount: Int,

    @Serializable(with = InstantAsIso8601String::class)
    override val timestamp: Instant,
) : BaseBootRelativeTime, BaseAbsoluteTime

interface CombinedTimeProvider {
    fun now(): CombinedTime
}

class RealCombinedTimeProvider(private val context: Context) : CombinedTimeProvider {
    override fun now() =
        getUptimeElapsedRealtimeAndTimestamp().let { (uptime, elapsedRealtime, timestamp) ->
            CombinedTime(
                uptime = uptime.milliseconds.boxed(),
                elapsedRealtime = elapsedRealtime.milliseconds.boxed(),
                linuxBootId = readLinuxBootId(),
                bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT),
                timestamp = timestamp,
            )
        }
}

private fun getUptimeElapsedRealtimeAndTimestamp(): Triple<Long, Long, Instant> =
    // Note: this is not 100% accurate. There is a little bit of time spent between these calls, so the timestamps
    // reflect slightly different moments in time:
    Triple(SystemClock.uptimeMillis(), SystemClock.elapsedRealtime(), Instant.ofEpochMilli(System.currentTimeMillis()))
