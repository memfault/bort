package com.memfault.bort.uploader

import com.memfault.bort.*
import com.memfault.bort.shared.Logger
import com.memfault.bort.ingress.IngressService
import com.memfault.bort.ingress.SdkEvent
import com.memfault.bort.ingress.SdkEventCollection

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
