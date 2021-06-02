package com.memfault.bort

import android.content.SharedPreferences
import android.os.SystemClock
import com.memfault.bort.ingress.IngressService
import com.memfault.bort.ingress.RebootEvent
import com.memfault.bort.ingress.RebootEventInfo
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider
import com.memfault.bort.tokenbucket.TokenBucketStore
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

interface LastTrackedBootCountProvider {
    var bootCount: Int
}

class RealLastTrackedBootCountProvider(
    sharedPreferences: SharedPreferences
) : LastTrackedBootCountProvider, PreferenceKeyProvider<Int>(
    sharedPreferences = sharedPreferences,
    defaultValue = 0,
    preferenceKey = PREFERENCE_LAST_TRACKED_BOOT_COUNT
) {
    override var bootCount
        get() = super.getValue()
        set(value) = super.setValue(value)
}

internal class BootCountTracker(
    val lastTrackedBootCountProvider: LastTrackedBootCountProvider,
    val untrackedBootCountHandler: (bootCount: Int) -> Unit
) {
    fun trackIfNeeded(bootCount: Int) {
        if (bootCount <= lastTrackedBootCountProvider.bootCount) {
            Logger.v("Boot $bootCount already tracked")
            return
        }
        untrackedBootCountHandler(bootCount).also {
            lastTrackedBootCountProvider.bootCount = bootCount
            Logger.v("Tracked boot $bootCount")
        }
    }
}

internal fun getBootInstant() =
    Instant.now().minusNanos(TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime()))

data class AndroidBootReason(
    val reason: String,
    val subreason: String? = null,
    val details: List<String>? = null
) {
    companion object {
        /**
         * Parses the "sys.boot.reason" system property.
         * See https://source.android.com/devices/bootloader/boot-reason
         */
        fun parse(bootReason: String?): AndroidBootReason =
            (bootReason ?: FALLBACK_SYS_BOOT_REASON_VALUE).split(",").let {
                AndroidBootReason(
                    reason = it[0],
                    subreason = it.getOrNull(1),
                    details = if (it.size > 2) it.drop(2) else null
                )
            }

        const val SYS_BOOT_REASON_KEY = "sys.boot.reason"

        const val FALLBACK_SYS_BOOT_REASON_VALUE = "reboot,bort_unknown"
    }
}

fun readLinuxBootId(): String =
    File("/proc/sys/kernel/random/boot_id").readText().trim()

internal class RebootEventUploader(
    val ingressService: IngressService,
    val deviceInfo: DeviceInfo,
    val androidSysBootReason: String?,
    val tokenBucketStore: TokenBucketStore,
    private val getLinuxBootId: () -> String
) {
    private fun createAndUploadCurrentRebootEvent(bootCount: Int) {
        val allowedByRateLimit = tokenBucketStore.edit { map ->
            map.upsertBucket("reboot-event")?.take() ?: false
        }

        if (!allowedByRateLimit) {
            // TODO: maybe add a metric for dropped reboot events
            return
        }

        val rebootEvent = RebootEvent(
            capturedDate = getBootInstant(),
            deviceInfo = deviceInfo,
            eventInfo = RebootEventInfo.fromAndroidBootReason(
                bootCount = bootCount,
                linuxBootId = getLinuxBootId(),
                androidBootReason = AndroidBootReason.parse(androidSysBootReason)
            )
        )
        ingressService.uploadRebootEvents(
            listOf(rebootEvent)
        ).execute()
    }

    fun handleUntrackedBootCount(bootCount: Int) = createAndUploadCurrentRebootEvent(bootCount)
}
