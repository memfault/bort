package com.memfault.bort.metrics

import com.memfault.bort.PackageManagerClient
import com.memfault.bort.parsers.Package
import com.memfault.bort.regex.toGlobRegex
import com.memfault.bort.settings.MetricsSettings
import javax.inject.Inject

data class AppVersions(
    val packages: List<Package>,
)

/**
 * Collects system properties of interest, and passes them on to the device properties database.
 */
class AppVersionsCollector @Inject constructor(
    private val metricsSettings: MetricsSettings,
    private val packageManagerClient: PackageManagerClient,
) {
    suspend fun collect(): AppVersions? {
        val packages = metricsSettings.appVersions
        if (packages.isEmpty()) return null

        val appVersions = packageManagerClient.getPackageManagerReport()
        val packageRegexes = packages.map { it.toGlobRegex() }
        return AppVersions(
            packages = appVersions.packages
                .filter { pkg -> packageRegexes.any { it.matches(pkg.id) } }
                .take(metricsSettings.maxNumAppVersions.coerceAtLeast(0)),
        )
    }

    fun record(
        appVersions: AppVersions,
        devicePropertiesStore: DevicePropertiesStore,
    ) {
        appVersions.packages.forEach { pkg ->
            pkg.versionName?.let { versionName ->
                devicePropertiesStore.upsert(
                    name = VERSION_PREFIX + pkg.id,
                    value = versionName,
                    internal = false,
                )
            }
        }
    }

    companion object {
        private const val VERSION_PREFIX = "version."
    }
}
