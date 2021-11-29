package com.memfault.bort.ingress

import com.memfault.bort.AndroidBootReason
import com.memfault.bort.DeviceInfo
import com.memfault.bort.SOFTWARE_TYPE
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

/**
 * TODO: use kotlinx.serialization's polymorphism support
 * When this code was written, retrofit2-kotlinx-serialization-converter did not support it yet.
 * (https://github.com/JakeWharton/retrofit2-kotlinx-serialization-converter/issues/18).
 *
 * Must use abstract vals here (rather than constructor parameters via super) because of
 * https://github.com/Kotlin/kotlinx.serialization/issues/1438.
 */
@Serializable
abstract class Event<E : EventInfo>(
    val sdk_version: String = SDK_VERSION,
    val software_type: String = SOFTWARE_TYPE,
) {
    abstract val type: String
    abstract val captured_date: String
    abstract val hardware_version: String
    abstract val device_serial: String
    abstract val software_version: String
    abstract val event_info: E
}

interface EventInfo

@Serializable
class RebootEvent(
    override val type: String = "android_reboot",
    override val captured_date: String,
    override val hardware_version: String,
    override val device_serial: String,
    override val software_version: String,
    override val event_info: RebootEventInfo,
) : Event<RebootEventInfo>() {

    companion object {
        fun create(
            capturedDate: Instant,
            deviceInfo: DeviceInfo,
            eventInfo: RebootEventInfo,
        ) = RebootEvent(
            hardware_version = deviceInfo.hardwareVersion,
            device_serial = deviceInfo.deviceSerial,
            software_version = deviceInfo.softwareVersion,
            captured_date = DateTimeFormatter.ISO_INSTANT.format(capturedDate),
            event_info = eventInfo,
        )
    }
}

@Serializable
data class RebootEventInfo(
    val boot_count: Int,
    val linux_boot_id: String,
    val reason: String,
    val subreason: String? = null,
    val details: List<String>? = null,
) : EventInfo {
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
                androidBootReason.details
            )
    }
}
