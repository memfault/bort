package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import org.junit.Test
import java.io.StringReader

class LocationDumpsysParserTest {

    private fun parse(output: String): LocationDumpsysResult =
        parseLocationDumpsys(StringReader(output))

    private fun parseSuccess(output: String): GnssKpiMetrics =
        (parse(output) as LocationDumpsysResult.Success).metrics

    @Test
    fun parseFullOutput() {
        // Excerpt from a production dumpsys location output
        val output = """
            gps provider:
              service: ProviderRequest[OFF]
              GNSS_KPI_START
                KPI logging start time: +50s950ms
                KPI logging end time: +5d22h18m54s883ms
                Number of location reports: 10877
                Percentage location failure: 0.2298427875333272
                Number of TTFF reports: 162
                TTFF mean (sec): 3.7441172839506147
                TTFF standard deviation (sec): 5.874462503792729
                Number of position accuracy reports: 10852
                Position accuracy mean (m): 7.2572016037349725
                Position accuracy standard deviation (m): 18.187447263757953
                Number of CN0 reports: 10891
                Top 4 Avg CN0 mean (dB-Hz): 36.77721282619493
                Top 4 Avg CN0 standard deviation (dB-Hz): 4.933772465738726
                Total number of sv status messages processed: 507167
                Total number of L5 sv status messages processed: 165465
                Total number of sv status messages processed, where sv is used in fix: 400491
                Total number of L5 sv status messages processed, where sv is used in fix: 129897
                Number of L5 CN0 reports: 10479
                L5 Top 4 Avg CN0 mean (dB-Hz): 32.16112939352889
                L5 Top 4 Avg CN0 standard deviation (dB-Hz): 4.253711834951508
                Used-in-fix constellation types: GPS GLONASS BEIDOU GALILEO
              GNSS_KPI_END
              Power Metrics
                Time on battery (min): 630.2295
                Amount of time (while on battery) Top 4 Avg CN0 > 20.0 dB-Hz (min): 3.9220166666666665
                Amount of time (while on battery) Top 4 Avg CN0 <= 20.0 dB-Hz (min): 0.0
                Energy consumed while on battery (mAh): 0.0
              Hardware Version: 13
        """.trimIndent()

        val result = parseSuccess(output)

        assertThat(result.locationReportCount).isEqualTo(10877L)
        assertThat(result.locationFailurePct).isEqualTo(0.2298427875333272)
        assertThat(result.ttffReportCount).isEqualTo(162L)
        assertThat(result.ttffMeanSec).isEqualTo(3.7441172839506147)
        assertThat(result.ttffStddevSec).isEqualTo(5.874462503792729)
        assertThat(result.positionAccuracyReportCount).isEqualTo(10852L)
        assertThat(result.positionAccuracyMeanM).isEqualTo(7.2572016037349725)
        assertThat(result.positionAccuracyStddevM).isEqualTo(18.187447263757953)
        assertThat(result.cn0ReportCount).isEqualTo(10891L)
        assertThat(result.cn0Top4MeanDbHz).isEqualTo(36.77721282619493)
        assertThat(result.cn0Top4StddevDbHz).isEqualTo(4.933772465738726)
        assertThat(result.svTotalCount).isEqualTo(507167L)
        assertThat(result.svL5TotalCount).isEqualTo(165465L)
        assertThat(result.svUsedInFixCount).isEqualTo(400491L)
        assertThat(result.svL5UsedInFixCount).isEqualTo(129897L)
        assertThat(result.l5Cn0ReportCount).isEqualTo(10479L)
        assertThat(result.l5Cn0Top4MeanDbHz).isEqualTo(32.16112939352889)
        assertThat(result.l5Cn0Top4StddevDbHz).isEqualTo(4.253711834951508)
        assertThat(result.timeOnBatteryMin).isEqualTo(630.2295)
        assertThat(result.cn0AboveThresholdTimeMin).isEqualTo(3.9220166666666665)
        assertThat(result.cn0BelowThresholdTimeMin).isEqualTo(0.0)
        assertThat(result.energyConsumedMah).isEqualTo(0.0)
    }

    @Test
    fun missingGnssKpiStartReturnsNoMetricsFound() {
        val output = """
            Location Manager State:
              User Info:
                running users: u[0]
              Location Settings:
                Location Setting: [u0] true
        """.trimIndent()

        assertThat(parse(output)).isInstanceOf<LocationDumpsysResult.NoMetricsFound>()
    }

    @Test
    fun onlyPowerMetricsPresent() {
        val output = """
            gps provider:
              Power Metrics
                Time on battery (min): 100.5
                Amount of time (while on battery) Top 4 Avg CN0 > 20.0 dB-Hz (min): 80.0
                Amount of time (while on battery) Top 4 Avg CN0 <= 20.0 dB-Hz (min): 5.0
                Energy consumed while on battery (mAh): 2.5
        """.trimIndent()

        val result = parseSuccess(output)

        assertThat(result.timeOnBatteryMin).isEqualTo(100.5)
        assertThat(result.cn0AboveThresholdTimeMin).isEqualTo(80.0)
        assertThat(result.cn0BelowThresholdTimeMin).isEqualTo(5.0)
        assertThat(result.energyConsumedMah).isEqualTo(2.5)
        assertThat(result.locationReportCount).isNull()
        assertThat(result.cn0Top4MeanDbHz).isNull()
    }

    @Test
    fun malformedNumericValuesAreNull() {
        val output = """
            GNSS_KPI_START
              Number of location reports: not-a-number
              Percentage location failure: 0.5
              TTFF mean (sec): BAD
            GNSS_KPI_END
        """.trimIndent()

        val result = parseSuccess(output)

        assertThat(result.locationReportCount).isNull()
        assertThat(result.locationFailurePct).isEqualTo(0.5)
        assertThat(result.ttffMeanSec).isNull()
    }

    @Test
    fun emptyOutputReturnsNoMetricsFound() {
        assertThat(parse("")).isInstanceOf<LocationDumpsysResult.NoMetricsFound>()
        assertThat(parse("   \n   ")).isInstanceOf<LocationDumpsysResult.NoMetricsFound>()
    }
}
