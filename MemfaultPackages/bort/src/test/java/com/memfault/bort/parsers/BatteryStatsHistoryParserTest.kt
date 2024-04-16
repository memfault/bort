package com.memfault.bort.parsers

import com.memfault.bort.metrics.HighResTelemetry.DataType.DoubleType
import com.memfault.bort.metrics.HighResTelemetry.DataType.StringType
import com.memfault.bort.metrics.HighResTelemetry.Datum
import com.memfault.bort.metrics.HighResTelemetry.MetricType.Event
import com.memfault.bort.metrics.HighResTelemetry.MetricType.Gauge
import com.memfault.bort.metrics.HighResTelemetry.MetricType.Property
import com.memfault.bort.metrics.HighResTelemetry.Rollup
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import com.memfault.bort.parsers.BatteryStatsConstants.ALARM
import com.memfault.bort.parsers.BatteryStatsConstants.AUDIO
import com.memfault.bort.parsers.BatteryStatsConstants.BATTERY_HEALTH
import com.memfault.bort.parsers.BatteryStatsConstants.BATTERY_LEVEL
import com.memfault.bort.parsers.BatteryStatsConstants.BATTERY_STATUS
import com.memfault.bort.parsers.BatteryStatsConstants.BATTERY_TEMP
import com.memfault.bort.parsers.BatteryStatsConstants.BLUETOOTH_LE_SCANNING
import com.memfault.bort.parsers.BatteryStatsConstants.BOOL_VALUE_FALSE
import com.memfault.bort.parsers.BatteryStatsConstants.BOOL_VALUE_TRUE
import com.memfault.bort.parsers.BatteryStatsConstants.CPU_RUNNING
import com.memfault.bort.parsers.BatteryStatsConstants.DOZE
import com.memfault.bort.parsers.BatteryStatsConstants.FOREGROUND
import com.memfault.bort.parsers.BatteryStatsConstants.GPS_ON
import com.memfault.bort.parsers.BatteryStatsConstants.LONGWAKE
import com.memfault.bort.parsers.BatteryStatsConstants.PHONE_RADIO
import com.memfault.bort.parsers.BatteryStatsConstants.PHONE_SCANNING
import com.memfault.bort.parsers.BatteryStatsConstants.SCREEN_BRIGHTNESS
import com.memfault.bort.parsers.BatteryStatsConstants.SCREEN_ON
import com.memfault.bort.parsers.BatteryStatsConstants.START
import com.memfault.bort.parsers.BatteryStatsConstants.TOP_APP
import com.memfault.bort.parsers.BatteryStatsConstants.WIFI_ON
import com.memfault.bort.parsers.BatteryStatsConstants.WIFI_RADIO
import com.memfault.bort.parsers.BatteryStatsConstants.WIFI_RUNNING
import com.memfault.bort.parsers.BatteryStatsConstants.WIFI_SCAN
import com.memfault.bort.parsers.BatteryStatsConstants.WIFI_SIGNAL_STRENGTH
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class BatteryStatsHistoryParserTest {
    private fun createFile(content: String): File {
        val file = File.createTempFile("batterystats", ".txt")
        file.writeText(content)
        return file
    }

    private val BATTERYSTATS_FILE = """
        9,hsp,1,0,"Abort:Pending Wakeup Sources: 200f000.qcom,spmi:qcom,pm660@0:qpnp,fg battery qcom-step-chg "
        9,hsp,70,10103,"com.android.launcher3"
        9,hsp,71,10104,"com.memfault.bort"
        9,h,123:TIME:1000000
        9,h,1:START
        9,h,0,+r,wr=1,Bl=100,Bs=d,+S,Sb=0,+W,+Wr,+Ws,+Ww,Wss=0,-g,+bles,+Pr,+Psc,+a,Bh=g,di=light,Bt=213,+Efg=71,+Elw=70
        9,h,200000,-r,-S,Sb=3,-W,-Wr,-Ws,-Ww,Wss=2,+g,-bles,-Pr,-Psc,-a,Bh=f,di=full,Bt=263,+Etp=70,+Efg=70,+Eal=70
        9,h,3,-Efg=71,-Etp=71,wr=,Bl=x,+W
        9,h,0:SHUTDOWN
        9,h,123:RESET:TIME:2000000
        9,h,800000,Bl=90,Bt=250,-Efg=70,-Etp=70,-Elw=70
    """.trimIndent()

    private val EXPECTED_HRT = setOf(
        Rollup(
            RollupMetadata(stringKey = CPU_RUNNING, metricType = Property, dataType = StringType, internal = false),
            listOf(Datum(t = 1000001, BOOL_VALUE_TRUE), Datum(t = 1200001, BOOL_VALUE_FALSE)),
        ),
        Rollup(
            RollupMetadata(stringKey = BATTERY_LEVEL, metricType = Gauge, dataType = DoubleType, internal = false),
            listOf(Datum(t = 1000001, JsonPrimitive(100)), Datum(t = 2800000, JsonPrimitive(90))),
        ),
        Rollup(
            RollupMetadata(stringKey = BATTERY_STATUS, metricType = Property, dataType = StringType, internal = false),
            listOf(Datum(t = 1000001, JsonPrimitive("Discharging"))),
        ),
        Rollup(
            RollupMetadata(stringKey = BATTERY_TEMP, metricType = Gauge, dataType = DoubleType, internal = false),
            listOf(
                Datum(t = 1000001, JsonPrimitive(213)),
                Datum(t = 1200001, JsonPrimitive(263)),
                Datum(t = 2800000, JsonPrimitive(250)),
            ),
        ),
        Rollup(
            RollupMetadata(
                stringKey = BATTERY_HEALTH,
                metricType = Property,
                dataType = StringType,
                internal = false,
            ),
            listOf(Datum(t = 1000001, JsonPrimitive("Good")), Datum(t = 1200001, JsonPrimitive("Failure"))),
        ),
        Rollup(
            RollupMetadata(stringKey = AUDIO, metricType = Property, dataType = StringType, internal = false),
            listOf(Datum(t = 1000001, BOOL_VALUE_TRUE), Datum(t = 1200001, BOOL_VALUE_FALSE)),
        ),
        Rollup(
            RollupMetadata(stringKey = GPS_ON, metricType = Property, dataType = StringType, internal = false),
            listOf(Datum(t = 1000001, BOOL_VALUE_FALSE), Datum(t = 1200001, BOOL_VALUE_TRUE)),
        ),
        Rollup(
            RollupMetadata(stringKey = SCREEN_ON, metricType = Property, dataType = StringType, internal = false),
            listOf(Datum(t = 1000001, BOOL_VALUE_TRUE), Datum(t = 1200001, BOOL_VALUE_FALSE)),
        ),
        Rollup(
            RollupMetadata(
                stringKey = SCREEN_BRIGHTNESS,
                metricType = Property,
                dataType = StringType,
                internal = false,
            ),
            listOf(Datum(t = 1000001, JsonPrimitive("Dark")), Datum(t = 1200001, JsonPrimitive("Light"))),
        ),
        Rollup(
            RollupMetadata(stringKey = WIFI_ON, metricType = Property, dataType = StringType, internal = false),
            listOf(
                Datum(t = 1000001, BOOL_VALUE_TRUE),
                Datum(t = 1200001, BOOL_VALUE_FALSE),
                Datum(t = 1200004, BOOL_VALUE_TRUE),
            ),
        ),
        Rollup(
            RollupMetadata(stringKey = WIFI_SCAN, metricType = Property, dataType = StringType, internal = false),
            listOf(Datum(t = 1000001, BOOL_VALUE_TRUE), Datum(t = 1200001, BOOL_VALUE_FALSE)),
        ),
        Rollup(
            RollupMetadata(stringKey = WIFI_RADIO, metricType = Property, dataType = StringType, internal = false),
            listOf(Datum(t = 1000001, BOOL_VALUE_TRUE), Datum(t = 1200001, BOOL_VALUE_FALSE)),
        ),
        Rollup(
            RollupMetadata(stringKey = WIFI_RUNNING, metricType = Property, dataType = StringType, internal = false),
            listOf(Datum(t = 1000001, BOOL_VALUE_TRUE), Datum(t = 1200001, BOOL_VALUE_FALSE)),
        ),
        Rollup(
            RollupMetadata(
                stringKey = WIFI_SIGNAL_STRENGTH,
                metricType = Property,
                dataType = StringType,
                internal = false,
            ),
            listOf(Datum(t = 1000001, JsonPrimitive("VeryPoor")), Datum(t = 1200001, JsonPrimitive("Moderate"))),
        ),
        Rollup(
            RollupMetadata(stringKey = DOZE, metricType = Property, dataType = StringType, internal = false),
            listOf(Datum(t = 1000001, JsonPrimitive("Light")), Datum(t = 1200001, JsonPrimitive("Full"))),
        ),
        Rollup(
            RollupMetadata(
                stringKey = BLUETOOTH_LE_SCANNING,
                metricType = Property,
                dataType = StringType,
                internal = false,
            ),
            listOf(Datum(t = 1000001, BOOL_VALUE_TRUE), Datum(t = 1200001, BOOL_VALUE_FALSE)),
        ),
        Rollup(
            RollupMetadata(stringKey = PHONE_RADIO, metricType = Property, dataType = StringType, internal = false),
            listOf(Datum(t = 1000001, BOOL_VALUE_TRUE), Datum(t = 1200001, BOOL_VALUE_FALSE)),
        ),
        Rollup(
            RollupMetadata(
                stringKey = PHONE_SCANNING,
                metricType = Property,
                dataType = StringType,
                internal = false,
            ),
            listOf(Datum(t = 1000001, BOOL_VALUE_TRUE), Datum(t = 1200001, BOOL_VALUE_FALSE)),
        ),
        // -Etp=71 ignored, because 71 was not top app
        Rollup(
            RollupMetadata(stringKey = TOP_APP, metricType = Property, dataType = StringType, internal = false),
            listOf(
                Datum(t = 1200001, JsonPrimitive("com.android.launcher3")),
                Datum(t = 2800000, JsonPrimitive(null as String?)),
            ),
        ),
        // -Efg=71 ignored, because 71 was not foreground
        Rollup(
            RollupMetadata(stringKey = FOREGROUND, metricType = Property, dataType = StringType, internal = false),
            listOf(
                Datum(t = 1000001, JsonPrimitive("com.memfault.bort")),
                Datum(t = 1200001, JsonPrimitive("com.android.launcher3")),
                Datum(t = 2800000, JsonPrimitive(null as String?)),
            ),
        ),
        Rollup(
            RollupMetadata(stringKey = LONGWAKE, metricType = Property, dataType = StringType, internal = false),
            listOf(
                Datum(t = 1000001, JsonPrimitive("com.android.launcher3")),
                Datum(t = 2800000, JsonPrimitive(null as String?)),
            ),
        ),
        Rollup(
            RollupMetadata(stringKey = ALARM, metricType = Property, dataType = StringType, internal = false),
            listOf(Datum(t = 1200001, JsonPrimitive("com.android.launcher3"))),
        ),
        Rollup(
            RollupMetadata(stringKey = START, metricType = Event, dataType = StringType, internal = false),
            listOf(Datum(t = 1000001, JsonPrimitive("Start")), Datum(t = 1200004, JsonPrimitive("Shutdown"))),
        ),
        Rollup(
            RollupMetadata(
                stringKey = "battery_discharge_duration_ms",
                metricType = Gauge,
                dataType = DoubleType,
                internal = false,
            ),
            listOf(Datum(t = 2800000, JsonPrimitive(1000126.0))),
        ),
        Rollup(
            RollupMetadata(
                stringKey = "battery_soc_pct_drop",
                metricType = Gauge,
                dataType = DoubleType,
                internal = false,
            ),
            listOf(Datum(t = 2800000, JsonPrimitive(10.0))),
        ),
    )

    @Test
    fun testParser() {
        val parser = BatteryStatsHistoryParser(createFile(BATTERYSTATS_FILE))
        runTest {
            val result = parser.parseToCustomMetrics()
            assertEquals(EXPECTED_HRT, result.batteryStatsHrt)
        }
    }

    // Copied from test_batterystats_history_aggregator.py, so that we match the aggregate output of the backend.
    private val AGGREGATE_FILE = """
        9,hsp,1,0,"Abort:Pending Wakeup Sources: 200f000.qcom,spmi:qcom,pm660@0:qpnp,fg battery qcom-step-chg "
        9,h,123:TIME:1000000
        9,h,0,+r,wr=1,Bl=100,Bs=d,+S,Sb=0,+W,+Wr,+Ws,+Ww,Wss=0,+g,+bles,+Pr,+Psc,+a,Bh=g,di=light,Bt=213
        9,h,200000,-r,-S,Sb=3,-W,-Wr,-Ws,-Ww,Wss=2,-g,-bles,-Pr,-Psc,-a,Bh=f,di=full,Bt=263
        9,h,800000,Bl=90,Bt=250
    """.trimIndent()

    private val EXPECTED_AGGREGATES = mapOf(
        "audio_on_ratio" to JsonPrimitive(0.2),
//        "battery_charge_rate_pct_per_hour_avg" to JsonPrimitive(null as Double?), // Absent = correct
//        "battery_charge_rate_first_80_percent_pct_per_hour_avg" to JsonPrimitive(null as Double?), // Absent = correct
        "battery_discharge_rate_pct_per_hour_avg" to JsonPrimitive(-36.0),
        "battery_health_not_good_ratio" to JsonPrimitive(0.8),
        "battery_level_pct_avg" to JsonPrimitive(95.0),
        "cpu_resume_count_per_hour" to JsonPrimitive(3.6),
        "cpu_suspend_count_per_hour" to JsonPrimitive(3.6),
        "cpu_running_ratio" to JsonPrimitive(0.2),
        "bluetooth_scan_ratio" to JsonPrimitive(0.2),
        "doze_full_ratio" to JsonPrimitive(0.8),
        "doze_ratio" to JsonPrimitive(1.0),
        "gps_on_ratio" to JsonPrimitive(0.2),
        "max_battery_temp" to JsonPrimitive(263.0),
        "phone_radio_active_ratio" to JsonPrimitive(0.2),
        "phone_scanning_ratio" to JsonPrimitive(0.2),
        "phone_signal_strength_none_ratio" to JsonPrimitive(0.0),
        "phone_signal_strength_poor_ratio" to JsonPrimitive(0.0),
        "screen_brightness_light_or_bright_ratio" to JsonPrimitive(0.8),
        "screen_on_ratio" to JsonPrimitive(0.2),
        "wifi_on_ratio" to JsonPrimitive(0.2),
        "wifi_radio_active_ratio" to JsonPrimitive(0.2),
        "wifi_running_ratio" to JsonPrimitive(0.2),
        "wifi_scan_ratio" to JsonPrimitive(0.2),
        "wifi_signal_strength_poor_or_very_poor_ratio" to JsonPrimitive(0.2),
        "battery_discharge_duration_ms" to JsonPrimitive(1000000.0),
        "battery_soc_pct_drop" to JsonPrimitive(10.0),
    )

    @Test
    fun testAggregatesMatchBackend() {
        val parser = BatteryStatsHistoryParser(createFile(AGGREGATE_FILE))
        runTest {
            val result = parser.parseToCustomMetrics()
            assertEquals(EXPECTED_AGGREGATES, result.aggregatedMetrics)
        }
    }

    private val SOC_FILE = """
        9,hsp,1,0,"Abort:Pending Wakeup Sources: 200f000.qcom,spmi:qcom,pm660@0:qpnp,fg battery qcom-step-chg "
        9,h,123:TIME:1000000
        9,h,0,Bl=100,Bs=d
        9,h,10000,-r
        9,h,20000,Bl=90,Bs=c
        9,h,25000,Bl=97,Bs=d
        9,h,50000,Bl=65
    """.trimIndent()

    @Test
    fun testSocAggregates() {
        val parser = BatteryStatsHistoryParser(createFile(SOC_FILE))
        runTest {
            val result = parser.parseToCustomMetrics()
            assertEquals(JsonPrimitive(80000.0), result.aggregatedMetrics["battery_discharge_duration_ms"])
            assertEquals(JsonPrimitive(42.0), result.aggregatedMetrics["battery_soc_pct_drop"])
        }
    }

    private val SOC_FILE_NO_DISCHARGE = """
        9,hsp,1,0,"Abort:Pending Wakeup Sources: 200f000.qcom,spmi:qcom,pm660@0:qpnp,fg battery qcom-step-chg "
        9,h,123:TIME:1000000
        9,h,0,Bl=60,Bs=c
        9,h,50000,Bl=75
    """.trimIndent()

    @Test
    fun testNoSocAggregates() {
        val parser = BatteryStatsHistoryParser(createFile(SOC_FILE_NO_DISCHARGE))
        runTest {
            val result = parser.parseToCustomMetrics()
            assertFalse(result.aggregatedMetrics.containsKey("battery_discharge_duration_ms"))
            assertFalse(result.aggregatedMetrics.containsKey("battery_soc_pct_drop"))
        }
    }

    private val SOC_FILE_DISCHARGE_NO_DROP = """
        9,hsp,1,0,"Abort:Pending Wakeup Sources: 200f000.qcom,spmi:qcom,pm660@0:qpnp,fg battery qcom-step-chg "
        9,h,123:TIME:1000000
        9,h,0,Bl=60,Bs=d
        9,h,50000,-W
    """.trimIndent()

    @Test
    fun testDischargeNoDrop() {
        val parser = BatteryStatsHistoryParser(createFile(SOC_FILE_DISCHARGE_NO_DROP))
        runTest {
            val result = parser.parseToCustomMetrics()
            assertEquals(JsonPrimitive(50000.0), result.aggregatedMetrics["battery_discharge_duration_ms"])
            assertEquals(JsonPrimitive(0.0), result.aggregatedMetrics["battery_soc_pct_drop"])
        }
    }

    private val TRAILING_COMMAS_EMPTY_EVENTS = """
9,hsp,0,1000,"android"
9,h,0:RESET:TIME:1712915952498
9,h,0,Bl=100
9,h,0,Dpst=13940,13680,-48280,1940,890,-25366700,
9,0,i,dsd,1506595,87,,p-,
9,h,1,,Bl=99
    """.trimIndent()

    @Test
    fun handlesEmptyEvents() {
        val parser = BatteryStatsHistoryParser(createFile(TRAILING_COMMAS_EMPTY_EVENTS))
        runTest {
            val result = parser.parseToCustomMetrics()
            assertEquals(1, result.batteryStatsHrt.size)
            val batteryLevelHrt = result.batteryStatsHrt.first()
            val expectedHrt = Rollup(
                RollupMetadata(
                    stringKey = "battery_level",
                    metricType = Gauge,
                    dataType = DoubleType,
                    internal = false,
                ),
                listOf(
                    Datum(t = 1712915952498, value = JsonPrimitive(100)),
                    Datum(t = 1712915952499, value = JsonPrimitive(99)),
                ),
            )
            assertEquals(expectedHrt, batteryLevelHrt)
        }
    }
}
