package com.memfault.bort.ingress

import com.memfault.bort.AndroidBootReason
import com.memfault.bort.DeviceInfo
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.format.DateTimeFormatter


/**
 * Note: not using kotlinx.serialization's polymorphism support, because
 * retrofit2-kotlinx-serialization-converter does not support it
 * (https://github.com/JakeWharton/retrofit2-kotlinx-serialization-converter/issues/18).
 */
@Serializable
abstract class Event <E : EventInfo>(
    val type: String,
    val sdk_version: String = SDK_VERSION,
    val captured_date: String,
    val hardware_version: String,
    val device_serial: String,
    val software_version: String,
    val event_info: E,
    val software_type: String = SOFTWARE_TYPE
) {
    constructor(
        type: String,
        capturedDate: Instant,
        deviceInfo: DeviceInfo,
        eventInfo: E
    ) : this(
        type = type,
        captured_date = DateTimeFormatter.ISO_INSTANT.format(capturedDate),
        hardware_version = deviceInfo.hardwareVersion,
        device_serial = deviceInfo.deviceSerial,
        software_version = deviceInfo.softwareVersion,
        event_info = eventInfo
    )
}

interface EventInfo

@Serializable
class RebootEvent : Event<RebootEventInfo> {
    constructor(
        capturedDate: Instant,
        deviceInfo: DeviceInfo,
        eventInfo: RebootEventInfo
    ) : super(
        "android_reboot",
        capturedDate,
        deviceInfo,
        eventInfo
    )
}

@Serializable
data class RebootEventInfo (
    val boot_count: Int,
    val linux_boot_id: String,
    val reason: String,
    val subreason: String? = null,
    val details: List<String>? = null
) : EventInfo {
    companion object {
        fun fromAndroidBootReason(bootCount: Int, linuxBootId: String, androidBootReason: AndroidBootReason) =
            RebootEventInfo(
                bootCount,
                linuxBootId,
                androidBootReason.reason,
                androidBootReason.subreason,
                androidBootReason.details
            )
    }
}
