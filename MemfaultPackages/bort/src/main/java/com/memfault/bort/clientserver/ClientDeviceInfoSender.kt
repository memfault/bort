package com.memfault.bort.clientserver

import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.settings.asDeviceConfigInfo
import com.memfault.bort.settings.toJson
import com.memfault.bort.shared.CLIENT_SERVER_DEVICE_INFO_DROPBOX_TAG
import javax.inject.Inject

class ClientDeviceInfoSender @Inject constructor(
    private val cachedClientServerMode: CachedClientServerMode,
    private val linkedDeviceFileSender: LinkedDeviceFileSender,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val temporaryFileFactory: TemporaryFileFactory,
) {
    suspend fun maybeSendDeviceInfoToServer() {
        if (!cachedClientServerMode.isClient()) return

        val deviceInfo = deviceInfoProvider.getDeviceInfo().asDeviceConfigInfo()
        temporaryFileFactory.createTemporaryFile("deviceconfig", "json").useFile { file, preventDeletion ->
            preventDeletion()
            file.writeText(deviceInfo.toJson())
            linkedDeviceFileSender.sendFileToLinkedDevice(file, CLIENT_SERVER_DEVICE_INFO_DROPBOX_TAG)
        }
    }
}
