package com.memfault.bort.logcat

import com.memfault.bort.logcat.Logs2MetricsRuleType.Unknown
import com.memfault.bort.shared.BortSharedJson
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.Logger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject

/**
 * Example serialized configuration:

"rules": [
{
"type": "count_matching",
"filter": {
"tag": "bort"
"priority": "W"
},
"pattern": "(.*): Scheduled restart job, restart counter is at",
"metric_name": "systemd_restarts_$1"
},
{
"type": "count_matching",
"filter": {
"tag": "bort-ota"
"priority": "D"
},
"pattern": "Out of memory: Killed process \\d+ \\((.*)\\)",
"metric_name": "oomkill_$1"
}
]

 */
@Serializable
data class Logs2MetricsConfig(
    val rules: List<Logs2MetricsRule>,
) {
    companion object {
        fun fromJson(json: JsonObject) = BortSharedJson.decodeFromJsonElement(serializer(), json)
    }
}

object RuleSerializer : KSerializer<Logs2MetricsRuleType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Logs2MetricsRuleType", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Logs2MetricsRuleType,
    ) {
        encoder.encodeString(value.key)
    }

    override fun deserialize(decoder: Decoder): Logs2MetricsRuleType {
        val key = decoder.decodeString()
        val ruleType = Logs2MetricsRuleType.getByKeyOrNull(key)
        if (ruleType == null) {
            Logger.i("Unknown rule type: $key")
            return Unknown
        }
        return ruleType
    }
}

@Serializable(with = RuleSerializer::class)
enum class Logs2MetricsRuleType(val key: String) {
    CountMatching("count_matching"),
    Unknown("unknown"),
    ;

    companion object {
        fun getByKeyOrNull(id: String) = entries.firstOrNull { it.key == id }
    }
}

/**
 * Actual rules we apply when processing logs.
 */
@Serializable
data class Logs2MetricsRule(
    val type: Logs2MetricsRuleType,
    val filter: LogcatFilterSpec,
    val pattern: String,
    @SerialName("metric_name")
    val metricName: String,
) {
    @Transient
    val regex = Regex(pattern)

    companion object {
        fun fromJson(json: JsonObject): List<Logs2MetricsRule> = try {
            Logs2MetricsConfig.fromJson(json).rules
        } catch (e: SerializationException) {
            Logger.w("Error deserializing logs2metrics rules", e)
            emptyList()
        }
    }
}
