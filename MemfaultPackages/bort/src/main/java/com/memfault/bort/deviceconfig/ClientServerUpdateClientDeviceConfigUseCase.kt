package com.memfault.bort.deviceconfig

import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.clientserver.CachedClientServerMode
import com.memfault.bort.clientserver.ClientDeviceInfoPreferenceProvider
import com.memfault.bort.clientserver.LinkedDeviceFileSender
import com.memfault.bort.clientserver.isServer
import com.memfault.bort.settings.DeviceConfigUpdateService
import com.memfault.bort.settings.DeviceConfigUpdateService.DeviceConfigArgs
import com.memfault.bort.settings.toJson
import com.memfault.bort.shared.CLIENT_SERVER_DEVICE_CONFIG_DROPBOX_TAG
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

interface ClientServerUpdateClientDeviceConfigUseCase {
    suspend fun update()
}

@ContributesBinding(SingletonComponent::class)
class RealClientServerUpdateClientDeviceConfigUseCase
@Inject constructor(
    private val deviceConfigUpdateService: DeviceConfigUpdateService,
    private val cachedClientServerMode: CachedClientServerMode,
    private val linkedDeviceFileSender: LinkedDeviceFileSender,
    private val temporaryFileFactory: TemporaryFileFactory,
    private val clientDeviceInfoPreferenceProvider: ClientDeviceInfoPreferenceProvider,
) : ClientServerUpdateClientDeviceConfigUseCase {
    override suspend fun update() {
        if (!cachedClientServerMode.isServer()) return

        val clientDeviceInfo = clientDeviceInfoPreferenceProvider.get()
        if (clientDeviceInfo == null) {
            Logger.d("clientDeviceConfig not set")
            return
        }

        Logger.d("Additionally fetching for client: $clientDeviceInfo")
        val clientDeviceConfig = deviceConfigUpdateService.deviceConfig(
            DeviceConfigArgs(clientDeviceInfo),
        )

        // Forward settings to client device.
        temporaryFileFactory.createTemporaryFile("deviceconfig", "json").useFile { file, preventDeletion ->
            preventDeletion()
            file.writeText(clientDeviceConfig.toJson())
            linkedDeviceFileSender.sendFileToLinkedDevice(file, CLIENT_SERVER_DEVICE_CONFIG_DROPBOX_TAG)
        }
    }
}
