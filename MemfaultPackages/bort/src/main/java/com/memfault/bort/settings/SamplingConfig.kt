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
    val sessionsResolution: Resolution = DEFAULT_SESSIONS,
) {
    companion object {
        fun SamplingConfig.toJson() = BortJson.encodeToString(serializer(), this)
        fun decodeFromString(json: String) = BortJson.decodeFromString(serializer(), json)

        val DEFAULT_DEBUGGING = Resolution.NORMAL
        val DEFAULT_LOGGING = Resolution.OFF
        val DEFAULT_MONITORING = Resolution.NORMAL
        val DEFAULT_SESSIONS = Resolution.NORMAL
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
    NOT_APPLICABLE("na"),

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

enum class CollectionDecision {
    NONE,

    /** Generate or parse the data to derive other data from it, then discard it without storing or uploading. */
    TRANSIENT,

    FULL,
}

enum class CollectedData {
    /** Device attributes and software version, so that a device stays identifiable at every level. */
    DEVICE_PROPERTIES,

    /** Everything which feeds the heartbeat like collectors, custom metrics, batterystats, usage stats. */
    METRICS,

    SESSION,

    /** Raw HRT payloads. */
    HIGH_RES_TELEMETRY,

    CRASH_ARTIFACT,

    /** Traces, custom data recordings, bug reports and SELinux violations. */
    DEBUGGING_ARTIFACT,

    /** Capturing logcat to the holding area, either for continuous upload or to attach to a trace. */
    LOGCAT_CAPTURE,

    /** Uploading logs which never overlapped an event of interest. */
    CONTINUOUS_LOGS,
}

fun SamplingConfig.shouldCollect(data: CollectedData): CollectionDecision = when (data) {
    CollectedData.DEVICE_PROPERTIES -> CollectionDecision.FULL

    CollectedData.METRICS -> if (monitoringResolution.isMonitoringActive()) {
        CollectionDecision.FULL
    } else {
        CollectionDecision.NONE
    }

    CollectedData.SESSION -> sessionsResolution.asDecision()

    CollectedData.HIGH_RES_TELEMETRY,
    CollectedData.DEBUGGING_ARTIFACT,
    -> debuggingResolution.asDecision()

    CollectedData.CONTINUOUS_LOGS -> loggingResolution.asDecision()

    CollectedData.LOGCAT_CAPTURE ->
        if (debuggingResolution.isActive() || loggingResolution.isActive()) {
            CollectionDecision.FULL
        } else {
            CollectionDecision.NONE
        }

    CollectedData.CRASH_ARTIFACT -> when {
        debuggingResolution.isActive() -> CollectionDecision.FULL
        monitoringResolution.isMonitoringActive() -> CollectionDecision.TRANSIENT
        else -> CollectionDecision.NONE
    }
}

/** Aspects other than monitoring are only ever [Resolution.OFF] or [Resolution.NORMAL]. */
fun Resolution.isActive(): Boolean = this >= Resolution.NORMAL

/** Monitoring is the one aspect sent as [Resolution.LOW]: daily heartbeats, which still need metrics. */
private fun Resolution.isMonitoringActive(): Boolean = this > Resolution.OFF

private fun Resolution.asDecision(): CollectionDecision = if (isActive()) {
    CollectionDecision.FULL
} else {
    CollectionDecision.NONE
}
