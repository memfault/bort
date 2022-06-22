package com.memfault.bort.settings

import com.memfault.bort.BortJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject

/**
 * We use a custom serializer here, because the structure of the response is not strictly defined - it contains a set of
 * namespaces, one of which ("memfault") is given special treatment here. Other namespaces are simply contained in the
 * [others] object.
 */
@Serializable(with = DecodedDeviceConfigSerializer::class)
data class DecodedDeviceConfig(
    val revision: Long,
    val memfault: FetchedDeviceConfigContainer.Memfault?,
    val others: JsonObject,
)

class DecodedDeviceConfigSerializer : KSerializer<DecodedDeviceConfig> {
    companion object {
        private const val KEY_MEMFAULT = "memfault"
    }

    private val delegateSerializer = FetchedDeviceConfigContainer.serializer()

    override fun deserialize(decoder: Decoder): DecodedDeviceConfig {
        val container = decoder.decodeSerializableValue(delegateSerializer)
        val memfault = container.data.config[KEY_MEMFAULT]?.let {
            BortJson.decodeFromJsonElement(FetchedDeviceConfigContainer.Memfault.serializer(), it)
        }
        // Exclude the memfault entry from the "others" map
        val others = JsonObject(container.data.config.filterNot { it.key == KEY_MEMFAULT })
        return DecodedDeviceConfig(
            revision = container.data.revision,
            memfault = memfault,
            others = others,
        )
    }

    override val descriptor = SerialDescriptor("DecodedDeviceConfigSerializer", delegateSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: DecodedDeviceConfig) {
        val map = value.others.toMutableMap()
        value.memfault?.let {
            val memfault = BortJson.encodeToJsonElement(FetchedDeviceConfigContainer.Memfault.serializer(), it)
            map.put(KEY_MEMFAULT, memfault)
        }
        val container = FetchedDeviceConfigContainer(
            data = FetchedDeviceConfigContainer.FetchedDeviceConfig(
                config = JsonObject(map.toMap()),
                revision = value.revision,
            )
        )
        encoder.encodeSerializableValue(delegateSerializer, container)
    }
}

@Serializable
data class FetchedDeviceConfigContainer(
    @SerialName("data")
    val data: FetchedDeviceConfig,
) {
    @Serializable
    data class FetchedDeviceConfig(
        @SerialName("config")
        val config: JsonObject,
        @SerialName("revision")
        val revision: Long,
    )

    @Serializable
    data class Memfault(
        @SerialName("bort")
        val bort: Bort,
        @SerialName("sampling")
        val sampling: Sampling,
    )

    @Serializable
    data class Bort(
        @SerialName("sdk-settings")
        val sdkSettings: FetchedSettings,
    )

    @Serializable
    data class Sampling(
        @SerialName("attributes.resolution")
        val attributesResolution: String,
        @SerialName("debugging.resolution")
        val debuggingResolution: String,
        @SerialName("logging.resolution")
        val loggingResolution: String,
        @SerialName("timeseries.resolution")
        val timeseriesResolution: String,
    )
}
