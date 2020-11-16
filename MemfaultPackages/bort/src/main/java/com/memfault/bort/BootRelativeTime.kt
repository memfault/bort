package com.memfault.bort

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BootRelativeTime(
    /**
     * System uptime in milliseconds since boot, as provided by SystemClock.uptimeMillis().
     */
    val uptime: Long,

    /**
     * Linux boot ID, as provided by /proc/sys/kernel/random/boot_id.
     */
    @SerialName("linux_boot_id")
    val linuxBootId: String,

    /**
     * Number of times Android system server has fully booted up (Settings.Global BOOT_COUNT).
     */
    @SerialName("boot_count")
    val bootCount: Int,
)

interface BootRelativeTimeProvider {
    fun now(): BootRelativeTime
}

class RealBootRelativeTimeProvider(private val context: Context) : BootRelativeTimeProvider {
    override fun now() =
        BootRelativeTime(
            uptime = SystemClock.uptimeMillis(),
            linuxBootId = readLinuxBootId(),
            bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT),
        )
}
