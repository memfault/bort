package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.metrics.HighResTelemetry.DataType.DoubleType
import com.memfault.bort.metrics.HighResTelemetry.MetricType.Gauge
import com.memfault.bort.metrics.HighResTelemetry.Rollup
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import com.memfault.bort.parsers.BatteryStatsSummaryParser
import com.memfault.bort.parsers.BatteryStatsSummaryParser.BatteryState
import com.memfault.bort.parsers.BatteryStatsSummaryParser.BatteryStatsSummary
import com.memfault.bort.parsers.BatteryStatsSummaryParser.DischargeData
import com.memfault.bort.parsers.BatteryStatsSummaryParser.PowerUseItemData
import com.memfault.bort.parsers.BatteryStatsSummaryParser.PowerUseSummary
import com.memfault.bort.settings.BatteryStatsSettings
import com.memfault.bort.test.util.TestTemporaryFileFactory
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class BatterystatsSummaryCollectorTest {
    private val timeMs: Long = 123456789
    private val parser: BatteryStatsSummaryParser = mockk {
        coEvery { parse(any()) } answers { batteryStatsSummary }
    }
    private val settings = object : BatteryStatsSettings {
        override val dataSourceEnabled: Boolean = true
        override val commandTimeout: Duration = 10.seconds
        override val useHighResTelemetry: Boolean = true
        override val collectSummary: Boolean = true
        override val componentMetrics: MutableList<String> = mutableListOf()
    }
    private var lastSummary: BatteryStatsSummary? = null
    private val provider = object : BatteryStatsSummaryProvider {
        override fun get(): BatteryStatsSummary? {
            return lastSummary
        }

        override fun set(summary: BatteryStatsSummary) {
            lastSummary = summary
        }
    }
    private val significantAppsProvider = object : SignificantAppsProvider {
        val internalApps = mutableListOf<SignificantApp>()
        val externalApps = mutableListOf<SignificantApp>()

        override fun internalApps(): List<SignificantApp> = internalApps
        override fun externalApps(): List<SignificantApp> = externalApps
    }
    private var batteryStatsSummary: BatteryStatsSummary? = null
    private val runBatteryStats: RunBatteryStats = mockk(relaxed = true)
    private val summaryCollector = BatterystatsSummaryCollector(
        temporaryFileFactory = TestTemporaryFileFactory,
        runBatteryStats = runBatteryStats,
        settings = settings,
        batteryStatsSummaryParser = parser,
        batteryStatsSummaryProvider = provider,
        significantAppsProvider = significantAppsProvider,
    )

    private val CHECKIN_1_DISCHARGING = BatteryStatsSummary(
        batteryState = BatteryState(
            batteryRealtimeMs = 27768496,
            startClockTimeMs = 1681397881665,
            estimatedBatteryCapacity = 3777.0,
            screenOffRealtimeMs = 13884248,
        ),
        dischargeData = DischargeData(totalMaH = 1128, totalMaHScreenOff = 611),
        powerUseItemData = setOf(
            PowerUseItemData(name = "android", totalPowerMaH = 217.05899999999997),
            PowerUseItemData(name = "unknown", totalPowerMaH = 33.0),
            PowerUseItemData(name = "com.google.android.apps.maps", totalPowerMaH = 114.0),
        ),
        timestampMs = timeMs,
        powerUseSummary = PowerUseSummary(
            originalBatteryCapacity = 3900.0,
            computedCapacityMah = 3700.0,
            minCapacityMah = 3600.0,
            maxCapacityMah = 3800.0,
        ),
    )

    private val CHECKIN_2_DISCHARGING = BatteryStatsSummary(
        batteryState = BatteryState(
            batteryRealtimeMs = 30145036, // 2376540 diff from 1st
            startClockTimeMs = 1681397881665,
            estimatedBatteryCapacity = 3777.0,
            screenOffRealtimeMs = 14478383, // 594135 diff from 1st
        ),
        dischargeData = DischargeData(totalMaH = 1233, totalMaHScreenOff = 686),
        powerUseItemData = setOf(
            PowerUseItemData(name = "screen", totalPowerMaH = 202.0),
            PowerUseItemData(name = "android", totalPowerMaH = 247.08),
            PowerUseItemData(name = "com.google.android.youtube", totalPowerMaH = 58.5),
        ),
        timestampMs = timeMs,
        powerUseSummary = PowerUseSummary(
            originalBatteryCapacity = 3900.0,
            computedCapacityMah = 3300.0,
            minCapacityMah = 3200.0,
            maxCapacityMah = 3700.0,
        ),
    )

    @Test
    fun initialRunNotCompared() {
        runTest {
            Locale.setDefault(Locale("fr", "FR"))
            lastSummary = null
            batteryStatsSummary = CHECKIN_1_DISCHARGING
            val result = summaryCollector.collectSummaryCheckin()
            // period = 27768496
            // 27768496/3600000=7.713471111111111
            // screen on = 0.5 screen off = 0.5
            // capacity = 3777
            assertEquals(
                BatteryStatsResult(
                    batteryStatsFileToUpload = null,
                    batteryStatsHrt = setOf(
                        // 611/3777*100 / 7.713471111111111 / 0.5 = 4.194443645079644
                        createRollup(name = "screen_off_battery_drain_%/hour", value = 4.19),
                        // (1128-611=517)/3777*100 / 7.713471111111111 / 0.5 = 3.5491446227597
                        createRollup(name = "screen_on_battery_drain_%/hour", value = 3.55),
                        createRollup(name = "estimated_battery_capacity_mah", value = 3777f),
                        createRollup(name = "original_battery_capacity_mah", value = 3900f),
                        createRollup(
                            name = "computed_battery_capacity_mah",
                            value = 3700f,
                            internal = true,
                        ),
                        createRollup(name = "min_battery_capacity_mah", value = 3600f),
                        createRollup(name = "max_battery_capacity_mah", value = 3800f),
                        createRollup(name = "battery_state_of_health_%", value = 96.84615),
                        // (217.059 / 3777 * 100) / 7.713471111111111 = 0.745042343009282
                        createRollup(name = "battery_use_%/hour_android", value = 0.75),
                        // (33 / 3777 * 100) / 7.713471111111111 = 0.113270573066799
                        createRollup(name = "battery_use_%/hour_unknown", value = 0.11),
                        // (114 / 3777 * 100) / 7.713471111111111 = 0.391298343321669
                        createRollup(name = "battery_use_%/hour_com.google.android.apps.maps", value = 0.39),
                        // 27768496 - 13884248 = 13884248
                        createRollup(name = "battery_screen_on_discharge_duration_ms", value = 13884248),
                        // (1128 - 611) * 100 / 3777 = 13.688112258406142
                        createRollup(name = "battery_screen_on_soc_pct_drop", value = 13.69),
                        createRollup(name = "battery_screen_off_discharge_duration_ms", value = 13884248),
                        // 611 * 100 / 3777 = 16.176859941752714
                        createRollup(name = "battery_screen_off_soc_pct_drop", value = 16.18),
                    ),
                    aggregatedMetrics = mapOf(
                        "screen_off_battery_drain_%/hour" to JsonPrimitive(4.19),
                        "screen_on_battery_drain_%/hour" to JsonPrimitive(3.55),
                        "estimated_battery_capacity_mah" to JsonPrimitive(3777f),
                        "original_battery_capacity_mah" to JsonPrimitive(3900f),
                        "min_battery_capacity_mah" to JsonPrimitive(3600f),
                        "max_battery_capacity_mah" to JsonPrimitive(3800f),
                        "battery_state_of_health_%" to JsonPrimitive(96.84615),
                        "battery_screen_on_discharge_duration_ms" to JsonPrimitive(13884248),
                        "battery_screen_on_soc_pct_drop" to JsonPrimitive(13.69),
                        "battery_screen_off_discharge_duration_ms" to JsonPrimitive(13884248),
                        "battery_screen_off_soc_pct_drop" to JsonPrimitive(16.18),
                    ),
                    internalAggregatedMetrics = emptyMap(),
                ),
                result,
            )
        }
    }

    @Test
    fun subsequentRunComparedToPrevious() = runTest {
        Locale.setDefault(Locale("fr", "FR"))
        lastSummary = CHECKIN_1_DISCHARGING
        batteryStatsSummary = CHECKIN_2_DISCHARGING
        val result = summaryCollector.collectSummaryCheckin() // .filterComponents()
        // period = 30145036 - 27768496 = 2376540 (~40 minutes)
        // 2376540/3600000=0.66015
        // screen on = 0.75 screen off = 0.25
        // capacity = 3777
        assertEquals(
            BatteryStatsResult(
                batteryStatsFileToUpload = null,
                batteryStatsHrt = setOf(
                    // (686-611=75)/3777*100 / 0.66015 / 0.25 = 12.031828759162916
                    createRollup(name = "screen_off_battery_drain_%/hour", value = 12.03),
                    // (1233-1128-(686-611)=30)/3777*100 / 0.66015 / 0.75 = 1.604243834555055
                    createRollup(name = "screen_on_battery_drain_%/hour", value = 1.60),

                    createRollup(name = "estimated_battery_capacity_mah", value = 3777f),
                    createRollup(name = "original_battery_capacity_mah", value = 3900f),
                    createRollup(
                        value = 3300f,
                        RollupMetadata(
                            stringKey = "computed_battery_capacity_mah",
                            metricType = Gauge,
                            dataType = DoubleType,
                            internal = true,
                        ),
                    ),
                    createRollup(name = "min_battery_capacity_mah", value = 3200f),
                    createRollup(name = "max_battery_capacity_mah", value = 3700f),
                    createRollup(name = "battery_state_of_health_%", value = 96.84615),
                    createRollup(name = "estimated_battery_capacity_mah", value = 3777f),
                    // ((247.08 - 217.05899999999997=30.021) / 3777 * 100) / 0.66015 = 1.204025103929433
                    createRollup(name = "battery_use_%/hour_android", value = 1.20),
                    // ((58.5 - 0) / 3777 * 100) / 0.66015 = 2.346206608036768
                    createRollup(name = "battery_use_%/hour_com.google.android.youtube", value = 2.35),
                    // ((202 - 0) / 3777 * 100) / 0.66015 = 8.101431364503029
                    createRollup(name = "battery_use_%/hour_screen", value = 8.10),
                    createRollup(name = "battery_screen_on_discharge_duration_ms", value = 1782405),
                    createRollup(name = "battery_screen_on_soc_pct_drop", value = 0.79),
                    createRollup(name = "battery_screen_off_discharge_duration_ms", value = 594135),
                    createRollup(name = "battery_screen_off_soc_pct_drop", value = 1.99),
                ),
                aggregatedMetrics = mapOf(
                    "screen_off_battery_drain_%/hour" to JsonPrimitive(12.03),
                    "screen_on_battery_drain_%/hour" to JsonPrimitive(1.60),
                    "estimated_battery_capacity_mah" to JsonPrimitive(3777f),
                    "original_battery_capacity_mah" to JsonPrimitive(3900f),
                    "min_battery_capacity_mah" to JsonPrimitive(3200f),
                    "max_battery_capacity_mah" to JsonPrimitive(3700f),
                    "battery_state_of_health_%" to JsonPrimitive(96.84615),
                    "battery_screen_on_discharge_duration_ms" to JsonPrimitive(1782405),
                    "battery_screen_on_soc_pct_drop" to JsonPrimitive(0.79),
                    "battery_screen_off_discharge_duration_ms" to JsonPrimitive(594135),
                    "battery_screen_off_soc_pct_drop" to JsonPrimitive(1.99),
                ),
                internalAggregatedMetrics = emptyMap(),
            ),
            result,
        )
    }

    private val CHECKIN_NO_BATTERY = BatteryStatsSummary(
        batteryState = BatteryState(
            batteryRealtimeMs = 0,
            startClockTimeMs = 1683752599879,
            estimatedBatteryCapacity = 0.0,
            screenOffRealtimeMs = 0,
        ),
        dischargeData = DischargeData(totalMaH = 0, totalMaHScreenOff = 0),
        powerUseItemData = setOf(),
        timestampMs = timeMs,
        powerUseSummary = PowerUseSummary(
            originalBatteryCapacity = 3900.0,
            computedCapacityMah = 3700.0,
            minCapacityMah = 3600.0,
            maxCapacityMah = 3800.0,
        ),
    )

    @Test
    fun noBattery() = runTest {
        lastSummary = null
        batteryStatsSummary = CHECKIN_NO_BATTERY
        val result = summaryCollector.collectSummaryCheckin()
        assertEquals(BatteryStatsResult.EMPTY, result)
    }

    @Test
    fun noBatterySubsequentRun() = runTest {
        lastSummary = CHECKIN_NO_BATTERY
        batteryStatsSummary = CHECKIN_NO_BATTERY
        val result = summaryCollector.collectSummaryCheckin()
        assertEquals(BatteryStatsResult.EMPTY, result)
    }

    private val CHECKIN_CHARGING = BatteryStatsSummary(
        batteryState = BatteryState(
            batteryRealtimeMs = 3600000,
            startClockTimeMs = 1683752599879,
            estimatedBatteryCapacity = 1000.0,
            screenOffRealtimeMs = 0,
        ),
        dischargeData = DischargeData(totalMaH = 0, totalMaHScreenOff = 0),
        powerUseItemData = setOf(),
        timestampMs = timeMs,
        powerUseSummary = PowerUseSummary(
            originalBatteryCapacity = 0.0,
            computedCapacityMah = 0.0,
            minCapacityMah = 0.0,
            maxCapacityMah = 0.0,
        ),
    )

    @Test
    fun charging() = runTest {
        lastSummary = CHECKIN_CHARGING
        batteryStatsSummary = CHECKIN_CHARGING
        val result = summaryCollector.collectSummaryCheckin()
        assertEquals(
            BatteryStatsResult(
                batteryStatsFileToUpload = null,
                batteryStatsHrt = setOf(
                    createRollup(name = "estimated_battery_capacity_mah", value = 1000f),
                    createRollup(name = "battery_screen_on_discharge_duration_ms", value = 0),
                    createRollup(name = "battery_screen_on_soc_pct_drop", value = 0.0),
                    createRollup(name = "battery_screen_off_discharge_duration_ms", value = 0),
                    createRollup(name = "battery_screen_off_soc_pct_drop", value = 0.0),
                ),
                aggregatedMetrics = mapOf(
                    "estimated_battery_capacity_mah" to JsonPrimitive(1000f),
                    "battery_screen_on_discharge_duration_ms" to JsonPrimitive(0),
                    "battery_screen_on_soc_pct_drop" to JsonPrimitive(0.0),
                    "battery_screen_off_discharge_duration_ms" to JsonPrimitive(0),
                    "battery_screen_off_soc_pct_drop" to JsonPrimitive(0.0),
                ),
                internalAggregatedMetrics = emptyMap(),
            ),
            result,
        )
    }

    private val CHECKIN_DISCHARGING = BatteryStatsSummary(
        batteryState = BatteryState(
            batteryRealtimeMs = 3600001,
            startClockTimeMs = CHECKIN_CHARGING.batteryState.startClockTimeMs + 1000,
            estimatedBatteryCapacity = 1000.0,
            screenOffRealtimeMs = 0,
        ),
        dischargeData = DischargeData(totalMaH = 100, totalMaHScreenOff = 60),
        powerUseItemData = setOf(),
        timestampMs = timeMs,
        powerUseSummary = PowerUseSummary(
            originalBatteryCapacity = 0.0,
            computedCapacityMah = 0.0,
            minCapacityMah = 0.0,
            maxCapacityMah = 0.0,
        ),
    )

    @Test
    fun newChargeCycle() = runTest {
        Locale.setDefault(Locale("fr", "FR"))
        lastSummary = CHECKIN_CHARGING
        batteryStatsSummary = CHECKIN_DISCHARGING
        val result = summaryCollector.collectSummaryCheckin()
        assertEquals(
            BatteryStatsResult(
                batteryStatsFileToUpload = null,
                batteryStatsHrt = setOf(
                    createRollup(name = "screen_on_battery_drain_%/hour", value = 4.0),
                    createRollup(name = "estimated_battery_capacity_mah", value = 1000f),
                    createRollup(name = "battery_screen_on_discharge_duration_ms", value = 3600001),
                    createRollup(name = "battery_screen_on_soc_pct_drop", value = 4.0),
                    createRollup(name = "battery_screen_off_discharge_duration_ms", value = 0),
                    createRollup(name = "battery_screen_off_soc_pct_drop", value = 6.0),
                ),
                aggregatedMetrics = mapOf(
                    "screen_on_battery_drain_%/hour" to JsonPrimitive(4.0),
                    "estimated_battery_capacity_mah" to JsonPrimitive(1000f),
                    "battery_screen_on_discharge_duration_ms" to JsonPrimitive(3600001),
                    "battery_screen_on_soc_pct_drop" to JsonPrimitive(4.0),
                    "battery_screen_off_discharge_duration_ms" to JsonPrimitive(0),
                    "battery_screen_off_soc_pct_drop" to JsonPrimitive(6.0),
                ),
                internalAggregatedMetrics = emptyMap(),
            ),
            result,
        )
    }

    private val CHECKIN_LOW_USAGE = BatteryStatsSummary(
        batteryState = BatteryState(
            batteryRealtimeMs = 3600000,
            startClockTimeMs = 0,
            estimatedBatteryCapacity = 1000.0,
            screenOffRealtimeMs = 1800000,
        ),
        dischargeData = DischargeData(totalMaH = 100, totalMaHScreenOff = 50),
        powerUseItemData = setOf(
            PowerUseItemData(name = "android", totalPowerMaH = 0.04),
        ),
        timestampMs = timeMs,
        powerUseSummary = PowerUseSummary(
            originalBatteryCapacity = 3900.0,
            computedCapacityMah = 3700.0,
            minCapacityMah = 3600.0,
            maxCapacityMah = 3800.0,
        ),
    )

    @Test
    fun lowComponentUsageNotReported() {
        // "android" usage is too low (would report 0%, so is not included in output).
        runTest {
            Locale.setDefault(Locale("fr", "FR"))
            lastSummary = null
            batteryStatsSummary = CHECKIN_LOW_USAGE
            val result = summaryCollector.collectSummaryCheckin()
            assertEquals(
                BatteryStatsResult(
                    batteryStatsFileToUpload = null,
                    batteryStatsHrt = setOf(
                        createRollup(name = "screen_off_battery_drain_%/hour", value = 10.0),
                        createRollup(name = "screen_on_battery_drain_%/hour", value = 10.0),
                        createRollup(name = "estimated_battery_capacity_mah", value = 1000f),
                        createRollup(name = "original_battery_capacity_mah", value = 3900f),
                        createRollup(
                            value = 3700f,
                            RollupMetadata(
                                stringKey = "computed_battery_capacity_mah",
                                metricType = Gauge,
                                dataType = DoubleType,
                                internal = true,
                            ),
                        ),
                        createRollup(name = "min_battery_capacity_mah", value = 3600f),
                        createRollup(name = "max_battery_capacity_mah", value = 3800f),
                        createRollup(name = "battery_state_of_health_%", value = 25.641027),
                        createRollup(name = "battery_screen_on_discharge_duration_ms", value = 1800000),
                        createRollup(name = "battery_screen_on_soc_pct_drop", value = 5.0),
                        createRollup(name = "battery_screen_off_discharge_duration_ms", value = 1800000),
                        createRollup(name = "battery_screen_off_soc_pct_drop", value = 5.0),
                    ),
                    aggregatedMetrics = mapOf(
                        "screen_off_battery_drain_%/hour" to JsonPrimitive(10.0),
                        "screen_on_battery_drain_%/hour" to JsonPrimitive(10.0),
                        "estimated_battery_capacity_mah" to JsonPrimitive(1000f),
                        "original_battery_capacity_mah" to JsonPrimitive(3900f),
                        "min_battery_capacity_mah" to JsonPrimitive(3600f),
                        "max_battery_capacity_mah" to JsonPrimitive(3800f),
                        "battery_state_of_health_%" to JsonPrimitive(25.641027),
                        "battery_screen_on_discharge_duration_ms" to JsonPrimitive(1800000),
                        "battery_screen_on_soc_pct_drop" to JsonPrimitive(5.0),
                        "battery_screen_off_discharge_duration_ms" to JsonPrimitive(1800000),
                        "battery_screen_off_soc_pct_drop" to JsonPrimitive(5.0),
                    ),
                    internalAggregatedMetrics = emptyMap(),
                ),
                result,
            )
        }
    }

    private val CHECKIN_SIGNIFICANT_POWERUSEITEM = BatteryStatsSummary(
        batteryState = BatteryState(
            batteryRealtimeMs = 27768496,
            startClockTimeMs = 1681397881665,
            estimatedBatteryCapacity = 3777.0,
            screenOffRealtimeMs = 13884248,
        ),
        dischargeData = DischargeData(totalMaH = 1128, totalMaHScreenOff = 611),
        powerUseItemData = setOf(
            PowerUseItemData(name = "screen", totalPowerMaH = 217.05899999999997),
            PowerUseItemData(name = "com.memfault.bort.smartfridge", totalPowerMaH = 33.0),
            PowerUseItemData(name = "com.memfault.bort.ota", totalPowerMaH = 33.0),
            PowerUseItemData(name = "com.google.android.apps.maps", totalPowerMaH = 114.0),
        ),
        timestampMs = timeMs,
        powerUseSummary = PowerUseSummary(
            originalBatteryCapacity = 0.0,
            computedCapacityMah = 0.0,
            minCapacityMah = 0.0,
            maxCapacityMah = 0.0,
        ),
    )

    @Test
    fun significantApps_powerUse() = runTest {
        lastSummary = null
        batteryStatsSummary = CHECKIN_SIGNIFICANT_POWERUSEITEM

        settings.componentMetrics.add("screen")
        significantAppsProvider.internalApps.addAll(
            listOf(
                SignificantApp(
                    packageName = "com.memfault.bort.smartfridge",
                    identifier = "bort",
                    internal = true,
                ),
            ),
        )
        significantAppsProvider.externalApps.addAll(
            listOf(
                SignificantApp(
                    packageName = "com.google.android.apps.maps",
                    identifier = "gmaps",
                    internal = false,
                ),
            ),
        )

        val result = summaryCollector.collectSummaryCheckin()
        assertThat(result)
            .isEqualTo(
                BatteryStatsResult(
                    batteryStatsFileToUpload = null,
                    batteryStatsHrt = setOf(
                        // 611/3777*100 / 7.713471111111111 / 0.5 = 4.194443645079644
                        createRollup(name = "screen_off_battery_drain_%/hour", value = 4.19),
                        // (1128-611=517)/3777*100 / 7.713471111111111 / 0.5 = 3.5491446227597
                        createRollup(name = "screen_on_battery_drain_%/hour", value = 3.55),
                        createRollup(name = "estimated_battery_capacity_mah", value = 3777f),
                        // (217.059 / 3777 * 100) / 7.713471111111111 = 0.745042343009282
                        createRollup(name = "battery_use_%/hour_screen", value = 0.75),
                        // (33 / 3777 * 100) / 7.713471111111111 = 0.113270573066799
                        createRollup(name = "battery_use_%/hour_com.memfault.bort.smartfridge", value = 0.11),
                        createRollup(name = "battery_use_%/hour_com.memfault.bort.ota", value = 0.11),
                        // (114 / 3777 * 100) / 7.713471111111111 = 0.391298343321669
                        createRollup(name = "battery_use_%/hour_com.google.android.apps.maps", value = 0.39),
                        // 27768496 - 13884248 = 13884248
                        createRollup(name = "battery_screen_on_discharge_duration_ms", value = 13884248),
                        // (1128 - 611) * 100 / 3777 = 16.176859941752714
                        createRollup(name = "battery_screen_on_soc_pct_drop", value = 13.69),
                        createRollup(name = "battery_screen_off_discharge_duration_ms", value = 13884248),
                        // 611 * 100 / 3777 = 16.176859941752714
                        createRollup(name = "battery_screen_off_soc_pct_drop", value = 16.18),
                    ),
                    aggregatedMetrics = mapOf(
                        "screen_off_battery_drain_%/hour" to JsonPrimitive(4.19),
                        "screen_on_battery_drain_%/hour" to JsonPrimitive(3.55),
                        "estimated_battery_capacity_mah" to JsonPrimitive(3777f),
                        "battery_use_%/hour_screen" to JsonPrimitive(0.75),
                        "battery_use_%/hour_gmaps" to JsonPrimitive(0.39),
                        "battery_screen_on_discharge_duration_ms" to JsonPrimitive(13884248),
                        "battery_screen_on_soc_pct_drop" to JsonPrimitive(13.69),
                        "battery_screen_off_discharge_duration_ms" to JsonPrimitive(13884248),
                        "battery_screen_off_soc_pct_drop" to JsonPrimitive(16.18),
                    ),
                    internalAggregatedMetrics = mapOf(
                        "battery_use_%/hour_bort" to JsonPrimitive(0.11),
                    ),
                ),
            )
    }

    private fun createRollup(name: String, value: Number, internal: Boolean = false) = Rollup(
        metadata = RollupMetadata(
            stringKey = name,
            metricType = HighResTelemetry.MetricType.Gauge,
            dataType = HighResTelemetry.DataType.DoubleType,
            internal = internal,
        ),
        data = listOf(
            HighResTelemetry.Datum(
                t = timeMs,
                value = JsonPrimitive(value),
            ),
        ),
    )

    private fun createRollup(value: Number, metadata: RollupMetadata) = Rollup(
        metadata = metadata,
        data = listOf(
            HighResTelemetry.Datum(
                t = timeMs,
                value = JsonPrimitive(value),
            ),
        ),
    )
}
