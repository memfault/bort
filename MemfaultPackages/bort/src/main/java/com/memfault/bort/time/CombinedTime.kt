package com.memfault.bort.time

import android.content.ContentResolver
import android.os.SystemClock
import android.provider.Settings
import com.memfault.bort.LinuxBootId
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

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

@ContributesBinding(SingletonComponent::class)
class RealCombinedTimeProvider @Inject constructor(
    private val contentResolver: ContentResolver,
    private val readLinuxBootId: LinuxBootId,
) : CombinedTimeProvider {
    override fun now() =
        getUptimeElapsedRealtimeAndTimestamp().let { (uptime, elapsedRealtime, timestamp) ->
            CombinedTime(
                uptime = uptime.milliseconds.boxed(),
                elapsedRealtime = elapsedRealtime.milliseconds.boxed(),
                linuxBootId = readLinuxBootId(),
                bootCount = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT),
                timestamp = timestamp,
            )
        }
}

private fun getUptimeElapsedRealtimeAndTimestamp(): Triple<Long, Long, Instant> =
    // Note: this is not 100% accurate. There is a little bit of time spent between these calls, so the timestamps
    // reflect slightly different moments in time:
    Triple(SystemClock.uptimeMillis(), SystemClock.elapsedRealtime(), Instant.ofEpochMilli(System.currentTimeMillis()))
