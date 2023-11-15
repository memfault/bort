package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.settings.CurrentSamplingConfig
import com.memfault.bort.settings.DecodedDeviceConfig
import com.memfault.bort.settings.SettingsUpdateHandler
import com.memfault.bort.settings.handleUpdate
import com.memfault.bort.shared.CLIENT_SERVER_DEVICE_CONFIG_DROPBOX_TAG
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.Lazy
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.SerializationException
import javax.inject.Inject

@ContributesMultibinding(SingletonComponent::class)
class ClientServerDeviceConfigProcessor @Inject constructor(
    private val settingsUpdateHandler: Lazy<SettingsUpdateHandler>,
    private val samplingConfig: CurrentSamplingConfig,
) : EntryProcessor() {
    override val tags: List<String> = listOf(CLIENT_SERVER_DEVICE_CONFIG_DROPBOX_TAG)

    override suspend fun process(entry: DropBoxManager.Entry, fileTime: AbsoluteTime?) {
        val content = entry.inputStream?.use { input ->
            input.reader().use { reader ->
                reader.readText()
            }
        } ?: return
        try {
            val newDeviceConfig = DecodedDeviceConfig.from(content)
            Logger.test("ClientServerDeviceConfigProcessor: newDeviceConfig = $newDeviceConfig")
            newDeviceConfig.handleUpdate(settingsUpdateHandler.get(), samplingConfig)
        } catch (e: SerializationException) {
            Logger.d("failed to deserialize fetched device config from client/server", e)
        }
    }
}
