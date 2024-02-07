package com.memfault.bort.time

import android.content.ContentResolver
import android.os.SystemClock
import android.provider.Settings
import com.memfault.bort.boot.LinuxBootId
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

interface BaseLinuxBootRelativeTime {
    /**
     * System uptime since boot, not counting time spent being suspended,
     * as provided by SystemClock.uptimeMillis() (CLOCK_MONOTONIC).
     * Side note: dmesg uses CLOCK_MONOTONIC.
     */
    val uptime: BoxedDuration

    /**
     * System uptime since boot, including time spent being suspended,
     * as provided by SystemClock.elapsedRealtime() (CLOCK_BOOTTIME).
     */
    val elapsedRealtime: BoxedDuration

    /**
     * Linux boot ID, as provided by /proc/sys/kernel/random/boot_id.
     */
    val linuxBootId: String
}

interface BaseBootRelativeTime : BaseLinuxBootRelativeTime {
    /**
     * Number of times Android system server has fully booted up (Settings.Global BOOT_COUNT).
     */
    val bootCount: Int
}

@Serializable
data class LinuxBootRelativeTime(
    @Serializable(with = DurationAsMillisecondsLong::class)
    @SerialName("uptime_ms")
    override val uptime: BoxedDuration,

    @Serializable(with = DurationAsMillisecondsLong::class)
    @SerialName("elapsed_realtime_ms")
    override val elapsedRealtime: BoxedDuration,

    @SerialName("linux_boot_id")
    override val linuxBootId: String,
) : BaseLinuxBootRelativeTime {
    constructor(bootRelativeTime: BaseLinuxBootRelativeTime) : this(
        bootRelativeTime.uptime,
        bootRelativeTime.elapsedRealtime,
        bootRelativeTime.linuxBootId,
    )
}

@Serializable
data class BootRelativeTime(
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
) : BaseBootRelativeTime

interface BootRelativeTimeProvider {
    fun now(): BootRelativeTime
}

@ContributesBinding(SingletonComponent::class)
class RealBootRelativeTimeProvider @Inject constructor(
    private val contentResolver: ContentResolver,
    private val readLinuxBootId: LinuxBootId,
) : BootRelativeTimeProvider {
    override fun now(): BootRelativeTime =
        getUptimeAndElapsedRealtime().let { (uptime, elapsedRealtime) ->
            BootRelativeTime(
                uptime = uptime.milliseconds.boxed(),
                elapsedRealtime = elapsedRealtime.milliseconds.boxed(),
                linuxBootId = readLinuxBootId(),
                bootCount = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT),
            )
        }
}

private fun getUptimeAndElapsedRealtime(): Pair<Long, Long> =
// Note: this is not 100% accurate. There is a little bit of time spent between these calls, so the timestamps
    // reflect slightly different moments in time:
    Pair(SystemClock.uptimeMillis(), SystemClock.elapsedRealtime())
