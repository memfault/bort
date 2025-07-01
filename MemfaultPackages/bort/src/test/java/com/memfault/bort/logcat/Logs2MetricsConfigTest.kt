package com.memfault.bort.logcat

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.logcat.Logs2MetricsRuleType.CountMatching
import com.memfault.bort.logcat.Logs2MetricsRuleType.Distribution
import com.memfault.bort.logcat.Logs2MetricsRuleType.StringProperty
import com.memfault.bort.logcat.Logs2MetricsRuleType.SumMatching
import com.memfault.bort.logcat.Logs2MetricsRuleType.Unknown
import com.memfault.bort.shared.BortSharedJson
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.LogcatPriority.DEBUG
import com.memfault.bort.shared.LogcatPriority.ERROR
import com.memfault.bort.shared.LogcatPriority.INFO
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
                    },
                    {
                        "type": "sum_matching",
                        "filter": {
                            "tag": "Choreographer",
                            "priority": "I"
                        },
                        "pattern": "^Skipped (\\d+) frames!  The application may be doing too much work on its main thread.${'$'}",
                        "metric_name": "frames_dropped"
                    },
                    {
                        "type": "distribution_minmeanmax",
                        "filter": {
                            "tag": "Metrics",
                            "priority": "I"
                        },
                        "pattern": "^Batch [A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12} \\[(\\d+) bytes\\] \\(Request\\) sent successfully.",
                        "metric_name": "request_size"
                    },
                    {
                        "type": "string_property",
                        "filter": {
                            "tag": "ConnectivityService",
                            "priority": "E"
                        },
                        "pattern": "^BUG: NetworkAgentInfo\\{.*BSSID=.*BSSID=([A-Fa-f0-9]{2}-[A-Fa-f0-9]{2}-[A-Fa-f0-9]{2}-[A-Fa-f0-9]{2}-[A-Fa-f0-9]{2}-[A-Fa-f0-9]{2}).*",
                        "metric_name": "wifi_bssid"
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
                    Logs2MetricsRule(
                        type = SumMatching,
                        filter = LogcatFilterSpec(tag = "Choreographer", priority = INFO),
                        pattern = "^Skipped (\\d+) frames!  " +
                            "The application may be doing too much work on its main thread.${'$'}",
                        metricName = "frames_dropped",
                    ),
                    Logs2MetricsRule(
                        type = Distribution,
                        filter = LogcatFilterSpec(tag = "Metrics", priority = INFO),
                        pattern = "^Batch [A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}" +
                            " \\[(\\d+) bytes\\] \\(Request\\) sent successfully.",
                        metricName = "request_size",
                    ),
                    Logs2MetricsRule(
                        type = StringProperty,
                        filter = LogcatFilterSpec(tag = "ConnectivityService", priority = ERROR),
                        pattern = "^BUG: NetworkAgentInfo\\{.*BSSID=.*BSSID=([A-Fa-f0-9]{2}-[A-Fa-f0-9]{2}-" +
                            "[A-Fa-f0-9]{2}-[A-Fa-f0-9]{2}-[A-Fa-f0-9]{2}-[A-Fa-f0-9]{2}).*",
                        metricName = "wifi_bssid",
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
