package com.memfault.bort.uploader

import com.memfault.bort.DeviceIdProvider
import com.memfault.bort.ingress.IngressService
import com.memfault.bort.ingress.SdkEvent
import com.memfault.bort.ingress.SdkEventCollection
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.SdkVersionInfo

fun sendSdkEnabledEvent(
    ingressService: IngressService,
    isEnabled: Boolean,
    deviceIdProvider: DeviceIdProvider,
    sdkVersionInfo: SdkVersionInfo
) {
    val eventName = if (isEnabled) {
        "Bort SDK Enabled"
    } else {
        "Bort SDK Disabled"
    }

    Logger.logEvent("sdkevent", eventName)

    val sdkEventCollection = SdkEventCollection(
        opaque_device_id = deviceIdProvider.deviceId(),
        sdk_version = sdkVersionInfo.appVersionName,
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
