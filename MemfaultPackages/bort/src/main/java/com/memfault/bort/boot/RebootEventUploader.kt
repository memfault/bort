package com.memfault.bort.boot

import android.os.SystemClock
import com.memfault.bort.DumpsterClient
import com.memfault.bort.clientserver.MarMetadata.RebootMarMetadata
import com.memfault.bort.ingress.RebootEventInfo
import com.memfault.bort.time.CombinedTime
import com.memfault.bort.time.boxed
import com.memfault.bort.tokenbucket.Reboots
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueUpload
import java.time.Instant
import java.util.concurrent.TimeUnit.MILLISECONDS
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

internal fun getBootInstant() =
    Instant.now().minusNanos(MILLISECONDS.toNanos(SystemClock.elapsedRealtime()))

data class AndroidBootReason(
    val reason: String,
    val subreason: String? = null,
    val details: List<String>? = null,
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
                    details = if (it.size > 2) it.drop(2) else null,
                )
            }

        const val SYS_BOOT_REASON_KEY = "sys.boot.reason"

        const val FALLBACK_SYS_BOOT_REASON_VALUE = "reboot,bort_unknown"
    }
}

class RebootEventUploader @Inject constructor(
    private val dumpsterClient: DumpsterClient,
    @Reboots val tokenBucketStore: TokenBucketStore,
    private val linuxBootId: LinuxBootId,
    private val enqueueUpload: EnqueueUpload,
) {

    private fun createAndUploadCurrentRebootEvent(
        bootCount: Int,
        androidSysBootReason: String?,
    ) {
        val allowedByRateLimit = tokenBucketStore.takeSimple(key = "reboot-event", tag = "reboot")

        if (!allowedByRateLimit) {
            return
        }

        val bootInstant = getBootInstant()
        val rebootEvent = RebootEventInfo.fromAndroidBootReason(
            bootCount = bootCount,
            linuxBootId = linuxBootId(),
            androidBootReason = AndroidBootReason.parse(androidSysBootReason),
        )
        val rebootTime = CombinedTime(
            uptime = 0.milliseconds.boxed(),
            elapsedRealtime = 0.milliseconds.boxed(),
            linuxBootId = rebootEvent.linux_boot_id,
            bootCount = rebootEvent.boot_count,
            timestamp = bootInstant,
        )
        val metadata = RebootMarMetadata(
            reason = rebootEvent.reason,
            subreason = rebootEvent.subreason,
            details = rebootEvent.details,
        )
        enqueueUpload.enqueue(file = null, metadata = metadata, collectionTime = rebootTime)
    }

    suspend fun handleUntrackedBootCount(bootCount: Int) {
        val androidSysBootReason = dumpsterClient.getprop()?.get(AndroidBootReason.SYS_BOOT_REASON_KEY)

        createAndUploadCurrentRebootEvent(bootCount, androidSysBootReason)
    }
}
