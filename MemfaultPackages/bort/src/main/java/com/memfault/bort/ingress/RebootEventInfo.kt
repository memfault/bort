package com.memfault.bort.ingress

import com.memfault.bort.boot.AndroidBootReason
import kotlinx.serialization.Serializable

@Serializable
data class RebootEventInfo(
    val boot_count: Int,
    val linux_boot_id: String,
    val reason: String,
    val subreason: String? = null,
    val details: List<String>? = null,
) {
    companion object {
        fun fromAndroidBootReason(
            bootCount: Int,
            linuxBootId: String,
            androidBootReason: AndroidBootReason,
        ) =
            RebootEventInfo(
                bootCount,
                linuxBootId,
                androidBootReason.reason,
                androidBootReason.subreason,
                androidBootReason.details,
            )
    }
}
