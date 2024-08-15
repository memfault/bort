package com.memfault.bort.parsers

import assertk.assertThat
import assertk.assertions.containsOnly
import com.memfault.bort.metrics.BatteryStatsAgg.BatteryLevelAggregator
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class BatteryLevelAggregatorTest {

    @Test fun `simple no change`() {
        val agg = BatteryLevelAggregator()

        agg.addValue(0.minutes.inWholeMilliseconds, JsonPrimitive(100))

        val results = agg.finish(60.minutes.inWholeMilliseconds)

        assertThat(results).containsOnly(
            "battery_level_pct_avg" to JsonPrimitive(100.0),
        )
    }

    @Test fun `simple one drop one hour`() {
        val agg = BatteryLevelAggregator()

        agg.addValue(0.minutes.inWholeMilliseconds, JsonPrimitive(100))
        agg.addValue(60.minutes.inWholeMilliseconds, JsonPrimitive(50))

        val results = agg.finish(60.minutes.inWholeMilliseconds)

        assertThat(results).containsOnly(
            "battery_level_pct_avg" to JsonPrimitive(75.0),
            "battery_soc_pct_drop" to JsonPrimitive(50.0),
        )
    }

    @Test fun `simple one drop two hours`() {
        val agg = BatteryLevelAggregator()

        agg.addValue(0.minutes.inWholeMilliseconds, JsonPrimitive(100))
        agg.addValue(120.minutes.inWholeMilliseconds, JsonPrimitive(50))

        val results = agg.finish(120.minutes.inWholeMilliseconds)

        assertThat(results).containsOnly(
            "battery_level_pct_avg" to JsonPrimitive(75.0),
            "battery_soc_pct_drop" to JsonPrimitive(50.0),
        )
    }

    @Test fun `multiple drops over minutes`() {
        val agg = BatteryLevelAggregator()

        agg.addValue(0.minutes.inWholeMilliseconds, JsonPrimitive(100))
        agg.addValue(10.minutes.inWholeMilliseconds, JsonPrimitive(90))
        agg.addValue(20.minutes.inWholeMilliseconds, JsonPrimitive(80))

        val results = agg.finish(20.minutes.inWholeMilliseconds)

        assertThat(results).containsOnly(
            "battery_level_pct_avg" to JsonPrimitive(90.0),
            "battery_soc_pct_drop" to JsonPrimitive(20.0),
        )
    }

    @Test fun `charge under 80 rate`() {
        val agg = BatteryLevelAggregator()

        agg.addValue(0.minutes.inWholeMilliseconds, JsonPrimitive(60))
        agg.addValue(10.minutes.inWholeMilliseconds, JsonPrimitive(70))
        agg.addValue(20.minutes.inWholeMilliseconds, JsonPrimitive(80))

        val results = agg.finish(20.minutes.inWholeMilliseconds)

        assertThat(results).containsOnly(
            "battery_level_pct_avg" to JsonPrimitive(70.0),
            "battery_soc_pct_rise" to JsonPrimitive(20.0),
            "battery_charge_rate_first_80_percent_pct_per_hour_avg" to JsonPrimitive(60.0),
        )
    }

    @Test fun `charge under 80 rate ignores above 80`() {
        val agg = BatteryLevelAggregator()

        agg.addValue(0.minutes.inWholeMilliseconds, JsonPrimitive(80))
        agg.addValue(10.minutes.inWholeMilliseconds, JsonPrimitive(90))
        agg.addValue(20.minutes.inWholeMilliseconds, JsonPrimitive(100))

        val results = agg.finish(20.minutes.inWholeMilliseconds)

        assertThat(results).containsOnly(
            "battery_level_pct_avg" to JsonPrimitive(90.0),
            "battery_soc_pct_rise" to JsonPrimitive(20.0),
        )
    }

    @Test fun `average battery level`() {
        val agg = BatteryLevelAggregator()

        agg.addValue(0.minutes.inWholeMilliseconds, JsonPrimitive(100))
        agg.addValue(10.minutes.inWholeMilliseconds, JsonPrimitive(90))
        agg.addValue(20.minutes.inWholeMilliseconds, JsonPrimitive(70))

        val results = agg.finish(20.minutes.inWholeMilliseconds)

        val timeWeightedAverageBatteryLevel =
            ((95.0 * 10.minutes.inWholeMilliseconds) + (80.0 * 10.minutes.inWholeMilliseconds)) /
                (20.minutes.inWholeMilliseconds)

        assertThat(results).containsOnly(
            "battery_level_pct_avg" to JsonPrimitive(timeWeightedAverageBatteryLevel),
            "battery_soc_pct_drop" to JsonPrimitive(30.0),
        )
    }
}
