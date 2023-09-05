package com.memfault.bort.metrics

import com.memfault.bort.PackageManagerClient
import com.memfault.bort.settings.MetricsSettings
import com.memfault.bort.toGlobRegex
import javax.inject.Inject

/**
 * Collects system properties of interest, and passes them on to the device properties database.
 */
class AppVersionsCollector @Inject constructor(
    private val metricsSettings: MetricsSettings,
    private val packageManagerClient: PackageManagerClient,
) {
    suspend fun updateAppVersions(devicePropertiesStore: DevicePropertiesStore) {
        val packages = metricsSettings.appVersions
        if (packages.isEmpty()) return
        val appVersions = packageManagerClient.getPackageManagerReport()
        val packageRegexes = packages.map { it.toGlobRegex() }
        var numVersionsRecorded = 0
        appVersions.packages.forEach { pkg ->
            if (packageRegexes.any { it.matches(pkg.id) }) {
                pkg.versionName?.let { versionName ->
                    // Set a limit on the number we will collect - just in case a customer enters '*'
                    if (++numVersionsRecorded > metricsSettings.maxNumAppVersions) return

                    devicePropertiesStore.upsert(
                        name = VERSION_PREFIX + pkg.id,
                        value = versionName,
                        internal = false,
                    )
                }
            }
        }
    }

    companion object {
        private const val VERSION_PREFIX = "version."
    }
}
