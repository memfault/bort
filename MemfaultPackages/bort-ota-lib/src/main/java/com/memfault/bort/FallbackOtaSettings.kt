package com.memfault.bort

import android.app.Application
import androidx.work.NetworkType
import androidx.work.NetworkType.CONNECTED
import androidx.work.NetworkType.UNMETERED
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.settings.FetchedSettings
import com.memfault.bort.settings.deviceInfoSettings
import com.memfault.bort.settings.readBundledSettings
import com.memfault.bort.shared.BortSharedJson
import com.memfault.bort.shared.BuildConfig
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.SoftwareUpdateSettings
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * If we fail to read settings from the Bort app, then use the bundled SDK settings as a fallback (the only other option
 * is crashing).
 */
@Singleton
class FallbackOtaSettings @Inject constructor(
    private val application: Application,
    private val dumpsterClient: DumpsterClient,
) {
    private val bundledConfig by lazy { application.resources.readBundledSettings() }
    private val bundledSdkSettings by lazy { FetchedSettings.from(bundledConfig) { BortSharedJson } }
    private val deviceInfoProvider by lazy {
        RealDeviceInfoProvider(
            deviceInfoSettings = bundledSdkSettings.deviceInfoSettings(),
            dumpsterClient = dumpsterClient,
            application = application,
            overrideSerial = NoOpOverrideSerial,
        )
    }
    private val deviceInfo by lazy { runBlocking { deviceInfoProvider.getDeviceInfo() } }

    fun fallbackOtaSettings(): SoftwareUpdateSettings {
        Logger.d("Falling back to default OTA settings")
        Reporting.report().counter(name = "ota_settings_fallback", internal = true).increment()

        return SoftwareUpdateSettings(
            deviceSerial = deviceInfo.deviceSerial,
            currentVersion = deviceInfo.softwareVersion,
            hardwareVersion = deviceInfo.hardwareVersion,
            softwareType = SOFTWARE_TYPE,
            updateCheckIntervalMs = bundledSdkSettings.otaUpdateCheckInterval.duration.inWholeMilliseconds,
            baseUrl = bundledSdkSettings.httpApiDeviceBaseUrl,
            projectApiKey = BuildConfig.MEMFAULT_PROJECT_API_KEY,
            downloadNetworkTypeConstraint = NetworkType.entries.firstOrNull {
                it.name.equals(bundledSdkSettings.otaDownloadNetworkConstraint, ignoreCase = true)
            } ?: if (bundledSdkSettings.otaDownloadNetworkConstraintAllowMeteredConnection) {
                CONNECTED
            } else {
                UNMETERED
            },
        )
    }
}
