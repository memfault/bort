package com.memfault.bort.logcat

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.logcat.Logs2MetricsRuleType.CountMatching
import com.memfault.bort.logcat.Logs2MetricsRuleType.Unknown
import com.memfault.bort.shared.BortSharedJson
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.LogcatPriority.DEBUG
import com.memfault.bort.shared.LogcatPriority.WARN
import kotlinx.serialization.json.JsonObject
import org.junit.Test

class Logs2MetricsConfigTest {
    @Test
    fun deserialization() {
        val serialized = """
            {
                "rules": [
                    {
                        "type": "count_matching",
                        "filter": {
                            "tag": "bort",
                            "priority": "W"
                        },
                        "pattern": "(.*): Scheduled restart job, restart counter is at",
                        "metric_name": "systemd_restarts_$1"
                    },
                    {
                        "type": "count_matching",
                        "filter": {
                            "tag": "bort-ota",
                            "priority": "D"
                        },
                        "pattern": "Out of memory: Killed process \\d+ \\((.*)\\)",
                        "metric_name": "oomkill_$1"
                    }
                ]
            }
        """.trimIndent()
        val json = BortSharedJson.decodeFromString<JsonObject>(serialized)
        val config = Logs2MetricsConfig.fromJson(json)
        assertThat(config).isEqualTo(
            Logs2MetricsConfig(
                listOf(
                    Logs2MetricsRule(
                        type = CountMatching,
                        filter = LogcatFilterSpec(tag = "bort", priority = WARN),
                        pattern = "(.*): Scheduled restart job, restart counter is at",
                        metricName = "systemd_restarts_$1",
                    ),
                    Logs2MetricsRule(
                        type = CountMatching,
                        filter = LogcatFilterSpec(tag = "bort-ota", priority = DEBUG),
                        pattern = "Out of memory: Killed process \\d+ \\((.*)\\)",
                        metricName = "oomkill_\$1",
                    ),
                ),
            ),
        )
    }

    @Test
    fun empty() {
        val serialized = """
            {
                "rules": [
                ]
            }
        """.trimIndent()
        val json = BortSharedJson.decodeFromString<JsonObject>(serialized)
        val config = Logs2MetricsConfig.fromJson(json)
        assertThat(config).isEqualTo(
            Logs2MetricsConfig(
                listOf(),
            ),
        )
    }

    @Test
    fun badRuleTypeIsUnknown() {
        val serialized = """
            {
                "rules": [
                    {
                        "type": "bad_rule_type",
                        "filter": {
                            "tag": "bort",
                            "priority": "W"
                        },
                        "pattern": "(.*): Scheduled restart job, restart counter is at",
                        "metric_name": "systemd_restarts_${'$'}1"
                    }
                ]
            }
        """.trimIndent()
        val json = BortSharedJson.decodeFromString<JsonObject>(serialized)
        val config = Logs2MetricsConfig.fromJson(json)
        assertThat(config).isEqualTo(
            Logs2MetricsConfig(
                listOf(
                    Logs2MetricsRule(
                        type = Unknown,
                        filter = LogcatFilterSpec(tag = "bort", priority = WARN),
                        pattern = "(.*): Scheduled restart job, restart counter is at",
                        metricName = "systemd_restarts_$1",
                    ),
                ),
            ),
        )
    }
}
