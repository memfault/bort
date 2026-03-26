package com.memfault.bort.metrics

import com.memfault.bort.DumpsterClient
import com.memfault.bort.reporting.NumericAgg.MAX
import com.memfault.bort.reporting.NumericAgg.MEAN
import com.memfault.bort.reporting.NumericAgg.MIN
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.settings.MetricsSettings
import com.memfault.bort.settings.SettingsFlow
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ContributesMultibinding(scope = SingletonComponent::class)
class SysfsThermalMetricsCollector @Inject constructor(
    private val dumpsterClient: DumpsterClient,
    private val metricsSettings: MetricsSettings,
    private val settingsFlow: SettingsFlow,
) : MetricCollector {

    override fun onChanged(): Flow<Unit> = settingsFlow.settings
        .map { settings ->
            settings.metricsSettings.sysfsThermalEnabled to
                settings.metricsSettings.sysfsThermalAllowlist
        }
        .distinctUntilChanged()
        .map { }

    override suspend fun collect() {
        if (!metricsSettings.sysfsThermalEnabled) return
        try {
            val output = dumpsterClient.getSysfsThermalZones() ?: return
            val allowlist = metricsSettings.sysfsThermalAllowlist
            val report = Reporting.report()
            output.lines()
                .filter { it.isNotBlank() }
                .forEach { line ->
                    val parts = line.split("\t", limit = 2)
                    if (parts.size != 2) {
                        Logger.w("sysfs thermal: unexpected line: $line")
                        return@forEach
                    }
                    // zoneType is the raw value from sysfs (e.g. "INT3400 Thermal").
                    // The allowlist must use this raw, non-sanitized name.
                    val zoneType = parts[0]
                    val tempMillidegrees = parts[1].toLongOrNull() ?: run {
                        Logger.w("sysfs thermal: non-numeric temp in line: $line")
                        return@forEach
                    }
                    if (allowlist.isNotEmpty() && zoneType !in allowlist) return@forEach
                    val sanitisedName = sanitise(zoneType)
                    val tempCelsius = tempMillidegrees / 1000.0
                    report.distribution(
                        name = "sysfs_thermal_${sanitisedName}_c",
                        aggregations = listOf(MIN, MAX, MEAN),
                    ).record(tempCelsius)
                }
        } catch (e: Exception) {
            Logger.w("Error collecting sysfs thermal metrics", e)
        }
    }

    companion object {
        private val SANITISE_REGEX = Regex("[^A-Za-z0-9_./%\\-]")
        fun sanitise(name: String): String = SANITISE_REGEX.replace(name, "-")
    }
}
