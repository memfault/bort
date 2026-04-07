package com.memfault.bort.metrics

import java.io.BufferedReader
import java.io.Reader

/**
 * GNSS KPI and power metrics parsed from dumpsys location.
 * All fields are nullable because they may be absent depending on
 * hardware specifics.
 */
data class GnssKpiMetrics(
    /** Total GPS fix reports since KPI logging start. */
    val locationReportCount: Long?,
    /** Fraction of fix attempts that failed (0.0–1.0). High values indicate poor sky view or interference. */
    val locationFailurePct: Double?,
    /** Number of Time-to-First-Fix samples recorded. */
    val ttffReportCount: Long?,
    /** Mean TTFF in seconds. Values > 30s suggest cold-start or poor signal. */
    val ttffMeanSec: Double?,
    /** Standard deviation of TTFF in seconds. High stddev = inconsistent acquisition. */
    val ttffStddevSec: Double?,
    /** Number of horizontal accuracy samples. */
    val positionAccuracyReportCount: Long?,
    /** Mean horizontal position accuracy in metres. */
    val positionAccuracyMeanM: Double?,
    /** Standard deviation of horizontal accuracy in metres. */
    val positionAccuracyStddevM: Double?,
    /** Number of CN0 (Carrier-to-Noise Density) samples recorded. */
    val cn0ReportCount: Long?,
    /**
     * Mean CN0 of the top-4 satellites in dB-Hz. Direct measure of signal strength.
     * < 20 = poor (high battery drain, unreliable fix); ≥ 35 = good.
     */
    val cn0Top4MeanDbHz: Double?,
    /** Standard deviation of top-4 CN0. High stddev = intermittent or obstructed signal. */
    val cn0Top4StddevDbHz: Double?,
    /** Total satellite status messages processed (proxy for GNSS engine activity). */
    val svTotalCount: Long?,
    /** Total L5-band satellite status messages processed. */
    val svL5TotalCount: Long?,
    /** Satellite status messages where the SV was actually used in a position fix. */
    val svUsedInFixCount: Long?,
    /** L5-band SVs used in fix. Higher ratio vs total = better multi-frequency performance. */
    val svL5UsedInFixCount: Long?,
    /** Number of L5-band CN0 samples. */
    val l5Cn0ReportCount: Long?,
    /** Mean CN0 of top-4 L5-band satellites in dB-Hz. L5 is more resistant to multipath. */
    val l5Cn0Top4MeanDbHz: Double?,
    /** Standard deviation of top-4 L5 CN0. */
    val l5Cn0Top4StddevDbHz: Double?,
    // --- Power Metrics block ---
    /** Total time on battery in minutes (cumulative since KPI logging start). */
    val timeOnBatteryMin: Double?,
    /**
     * Minutes spent with top-4 CN0 > 20 dB-Hz while on battery.
     * GPS drains significantly more battery when signal is weak (CN0 ≤ 20).
     */
    val cn0AboveThresholdTimeMin: Double?,
    /** Minutes spent with top-4 CN0 ≤ 20 dB-Hz while on battery (high-drain weak-signal time). */
    val cn0BelowThresholdTimeMin: Double?,
    /** Energy consumed by GNSS hardware while on battery in mAh (may be 0.0 if HAL doesn't report it). */
    val energyConsumedMah: Double?,
)

sealed interface LocationDumpsysResult {
    data class Success(val metrics: GnssKpiMetrics) : LocationDumpsysResult
    data object NoMetricsFound : LocationDumpsysResult
}

// Dumpsys block markers
private const val KPI_BLOCK_START = "GNSS_KPI_START"
private const val KPI_BLOCK_END = "GNSS_KPI_END"
private const val POWER_BLOCK_START = "Power Metrics"

// KPI block keys
private const val KEY_LOCATION_REPORT_COUNT = "Number of location reports"
private const val KEY_LOCATION_FAILURE_PCT = "Percentage location failure"
private const val KEY_TTFF_REPORT_COUNT = "Number of TTFF reports"
private const val KEY_TTFF_MEAN_SEC = "TTFF mean (sec)"
private const val KEY_TTFF_STDDEV_SEC = "TTFF standard deviation (sec)"
private const val KEY_POSITION_ACCURACY_REPORT_COUNT = "Number of position accuracy reports"
private const val KEY_POSITION_ACCURACY_MEAN_M = "Position accuracy mean (m)"
private const val KEY_POSITION_ACCURACY_STDDEV_M = "Position accuracy standard deviation (m)"
private const val KEY_CN0_REPORT_COUNT = "Number of CN0 reports"
private const val KEY_CN0_TOP4_MEAN_DBHZ = "Top 4 Avg CN0 mean (dB-Hz)"
private const val KEY_CN0_TOP4_STDDEV_DBHZ = "Top 4 Avg CN0 standard deviation (dB-Hz)"
private const val KEY_SV_TOTAL_COUNT = "Total number of sv status messages processed"
private const val KEY_SV_L5_TOTAL_COUNT = "Total number of L5 sv status messages processed"
private const val KEY_SV_USED_IN_FIX_COUNT =
    "Total number of sv status messages processed, where sv is used in fix"
private const val KEY_SV_L5_USED_IN_FIX_COUNT =
    "Total number of L5 sv status messages processed, where sv is used in fix"
private const val KEY_L5_CN0_REPORT_COUNT = "Number of L5 CN0 reports"
private const val KEY_L5_CN0_TOP4_MEAN_DBHZ = "L5 Top 4 Avg CN0 mean (dB-Hz)"
private const val KEY_L5_CN0_TOP4_STDDEV_DBHZ = "L5 Top 4 Avg CN0 standard deviation (dB-Hz)"

// Power block keys
private const val KEY_TIME_ON_BATTERY_MIN = "Time on battery (min)"
private const val KEY_CN0_ABOVE_THRESHOLD_TIME_MIN =
    "Amount of time (while on battery) Top 4 Avg CN0 > 20.0 dB-Hz (min)"
private const val KEY_CN0_BELOW_THRESHOLD_TIME_MIN =
    "Amount of time (while on battery) Top 4 Avg CN0 <= 20.0 dB-Hz (min)"
private const val KEY_ENERGY_CONSUMED_MAH = "Energy consumed while on battery (mAh)"

private sealed interface ParseState {
    data object None : ParseState
    data object KpiBlock : ParseState
    data class PowerBlock(val indent: Int) : ParseState
}

/**
 * Parses the output of dumpsys location and extracts GNSS KPI and power metrics.
 *
 * Returns null if neither the GNSS_KPI_START block nor the Power Metrics block is found.
 */
internal fun parseLocationDumpsys(reader: Reader): LocationDumpsysResult {
    val kpiValues = mutableMapOf<String, String>()
    val powerValues = mutableMapOf<String, String>()

    var state: ParseState = ParseState.None

    for (line in BufferedReader(reader).lineSequence()) {
        val trimmed = line.trim()
        when {
            trimmed == KPI_BLOCK_START -> state = ParseState.KpiBlock
            trimmed == KPI_BLOCK_END -> state = ParseState.None
            trimmed == POWER_BLOCK_START -> state = ParseState.PowerBlock(line.length - line.trimStart().length)
            state == ParseState.KpiBlock -> {
                parseKeyValue(trimmed)?.let { (k, v) -> kpiValues[k] = v }
            }
            state is ParseState.PowerBlock -> {
                if (trimmed.isEmpty()) continue
                val indent = line.length - line.trimStart().length
                if (indent <= state.indent) {
                    state = ParseState.None
                } else {
                    parseKeyValue(trimmed)?.let { (k, v) -> powerValues[k] = v }
                }
            }
        }
    }

    if (kpiValues.isEmpty() && powerValues.isEmpty()) return LocationDumpsysResult.NoMetricsFound

    return LocationDumpsysResult.Success(
        GnssKpiMetrics(
            locationReportCount = kpiValues[KEY_LOCATION_REPORT_COUNT]?.toLongOrNull(),
            locationFailurePct = kpiValues[KEY_LOCATION_FAILURE_PCT]?.toDoubleOrNull(),
            ttffReportCount = kpiValues[KEY_TTFF_REPORT_COUNT]?.toLongOrNull(),
            ttffMeanSec = kpiValues[KEY_TTFF_MEAN_SEC]?.toDoubleOrNull(),
            ttffStddevSec = kpiValues[KEY_TTFF_STDDEV_SEC]?.toDoubleOrNull(),
            positionAccuracyReportCount = kpiValues[KEY_POSITION_ACCURACY_REPORT_COUNT]?.toLongOrNull(),
            positionAccuracyMeanM = kpiValues[KEY_POSITION_ACCURACY_MEAN_M]?.toDoubleOrNull(),
            positionAccuracyStddevM = kpiValues[KEY_POSITION_ACCURACY_STDDEV_M]?.toDoubleOrNull(),
            cn0ReportCount = kpiValues[KEY_CN0_REPORT_COUNT]?.toLongOrNull(),
            cn0Top4MeanDbHz = kpiValues[KEY_CN0_TOP4_MEAN_DBHZ]?.toDoubleOrNull(),
            cn0Top4StddevDbHz = kpiValues[KEY_CN0_TOP4_STDDEV_DBHZ]?.toDoubleOrNull(),
            svTotalCount = kpiValues[KEY_SV_TOTAL_COUNT]?.toLongOrNull(),
            svL5TotalCount = kpiValues[KEY_SV_L5_TOTAL_COUNT]?.toLongOrNull(),
            svUsedInFixCount = kpiValues[KEY_SV_USED_IN_FIX_COUNT]?.toLongOrNull(),
            svL5UsedInFixCount = kpiValues[KEY_SV_L5_USED_IN_FIX_COUNT]?.toLongOrNull(),
            l5Cn0ReportCount = kpiValues[KEY_L5_CN0_REPORT_COUNT]?.toLongOrNull(),
            l5Cn0Top4MeanDbHz = kpiValues[KEY_L5_CN0_TOP4_MEAN_DBHZ]?.toDoubleOrNull(),
            l5Cn0Top4StddevDbHz = kpiValues[KEY_L5_CN0_TOP4_STDDEV_DBHZ]?.toDoubleOrNull(),
            timeOnBatteryMin = powerValues[KEY_TIME_ON_BATTERY_MIN]?.toDoubleOrNull(),
            cn0AboveThresholdTimeMin = powerValues[KEY_CN0_ABOVE_THRESHOLD_TIME_MIN]?.toDoubleOrNull(),
            cn0BelowThresholdTimeMin = powerValues[KEY_CN0_BELOW_THRESHOLD_TIME_MIN]?.toDoubleOrNull(),
            energyConsumedMah = powerValues[KEY_ENERGY_CONSUMED_MAH]?.toDoubleOrNull(),
        ),
    )
}

private fun parseKeyValue(line: String): Pair<String, String>? {
    val idx = line.indexOf(": ")
    if (idx < 0) return null
    return line.substring(0, idx).trim() to line.substring(idx + 2).trim()
}
