package com.memfault.bort.uploader

import com.memfault.bort.*
import com.memfault.bort.Logger


const val SDK_EVENT_WORK_TAG = "com.memfault.bort.work.tag.UPLOAD_SDK_EVENT"

fun sendSdkEnabledEvent(
    ingressService: IngressService,
    isEnabled: Boolean,
    deviceIdProvider: DeviceIdProvider,
    settingsProvider: SettingsProvider
) {
    val eventName = if (isEnabled) {
        "Bort SDK Enabled"
    } else {
        "Bort SDK Disabled"
    }

    Logger.logEvent("sdkevent", eventName)

    val sdkEventCollection = SdkEventCollection(
        opaque_device_id = deviceIdProvider.deviceId(),
        sdk_version = settingsProvider.appVersionName(),
        sdk_events = listOf(
            SdkEvent(
                timestamp = System.nanoTime(),
                name = eventName
            )
        )
    )

    ingressService.uploadCollection(
        sdkEventCollection
    ).execute()
}
