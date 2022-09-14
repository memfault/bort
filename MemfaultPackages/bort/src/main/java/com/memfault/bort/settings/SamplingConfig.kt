package com.memfault.bort.settings

import com.memfault.bort.BortJson
import com.memfault.bort.clientserver.MarManifest
import kotlinx.serialization.Serializable

@Serializable
data class SamplingConfig(
    val revision: Int = -1,
    val debuggingResolution: Resolution = DEFAULT_DEBUGGING,
    val loggingResolution: Resolution = DEFAULT_LOGGING,
    val monitoringResolution: Resolution = DEFAULT_MONITORING,
) {
    companion object {
        fun SamplingConfig.toJson() = BortJson.encodeToString(serializer(), this)
        fun decodeFromString(json: String) = BortJson.decodeFromString(serializer(), json)

        val DEFAULT_DEBUGGING = Resolution.NORMAL
        val DEFAULT_LOGGING = Resolution.OFF
        val DEFAULT_MONITORING = Resolution.NORMAL
    }
}

enum class Resolution(
    val value: String,
) {
    /**
     * For use in mar manifest, for files which should always be uploaded regardless of the current [SamplingConfig].
     * Also expected in [SamplingConfig] for "disabled" aspects.
     */
    OFF("off"),
    LOW("low"),
    NORMAL("normal"),
    HIGH("high"),

    /**
     * For use in mar manifest, for files which are not applicable to the given aspect (i.e. should never be uploaded
     * because of this aspect). Never expected in the [SamplingConfig] for any aspect.
     */
    NOT_APPLICABLE("na")
    ;

    companion object {
        private val DEFAULT = NORMAL

        private val map = values().associateBy(Resolution::value)
        fun fromString(type: String) = map[type] ?: DEFAULT
    }
}

fun SamplingConfig.shouldUpload(mar: MarManifest): Boolean =
    debuggingResolution >= mar.debuggingResolution ||
        loggingResolution >= mar.loggingResolution ||
        monitoringResolution >= mar.monitoringResolution
