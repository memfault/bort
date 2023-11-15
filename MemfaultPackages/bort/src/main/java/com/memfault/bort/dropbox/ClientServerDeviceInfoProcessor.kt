package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.clientserver.ClientDeviceInfoPreferenceProvider
import com.memfault.bort.settings.DeviceConfigUpdateService
import com.memfault.bort.shared.CLIENT_SERVER_DEVICE_INFO_DROPBOX_TAG
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerializationException
import javax.inject.Inject

@ContributesMultibinding(SingletonComponent::class)
class ClientServerDeviceInfoProcessor @Inject constructor(
    private val clientDeviceInfoPreferenceProvider: ClientDeviceInfoPreferenceProvider,
) : EntryProcessor() {
    override val tags: List<String> = listOf(CLIENT_SERVER_DEVICE_INFO_DROPBOX_TAG)

    override suspend fun process(entry: DropBoxManager.Entry, fileTime: AbsoluteTime?) {
        val content = entry.inputStream?.use { input ->
            input.reader().use { reader ->
                reader.readText()
            }
        } ?: return
        try {
            val clientDeviceInfo = DeviceConfigUpdateService.DeviceInfo.from(content)
            Logger.test("ClientServerDeviceInfoProcessor: clientDeviceInfo = $clientDeviceInfo")
            clientDeviceInfoPreferenceProvider.set(clientDeviceInfo)
        } catch (e: SerializationException) {
            Logger.d("failed to deserialize fetched device config from client/server", e)
        }
    }
}
