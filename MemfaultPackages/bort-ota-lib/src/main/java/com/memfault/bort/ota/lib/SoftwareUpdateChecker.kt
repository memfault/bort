package com.memfault.bort.ota.lib

import com.memfault.bort.shared.InternalMetric
import com.memfault.bort.shared.InternalMetric.Companion.OTA_CHECK_ERROR
import com.memfault.bort.shared.InternalMetric.Companion.OTA_CHECK_FOUND_UPDATE
import com.memfault.bort.shared.InternalMetric.Companion.OTA_CHECK_NO_UPDATE_AVAILABLE
import com.memfault.bort.shared.SoftwareUpdateSettings
import com.memfault.cloud.sdk.GetLatestReleaseCallback
import com.memfault.cloud.sdk.MemfaultCloud
import com.memfault.cloud.sdk.MemfaultDeviceInfo
import com.memfault.cloud.sdk.MemfaultOtaPackage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable

@Serializable
data class Ota(
    val url: String,
    val version: String,
    val releaseNotes: String,
    val metadata: Map<String, String> = emptyMap(),
    val isForced: Boolean?,
)

interface SoftwareUpdateChecker {
    suspend fun getLatestRelease(): Ota?
}

class MemfaultSoftwareUpdateChecker private constructor(
    private val memfault: MemfaultCloud,
    private val settings: () -> SoftwareUpdateSettings,
    private val metricLogger: MetricLogger,
) : SoftwareUpdateChecker {
    override suspend fun getLatestRelease(): Ota? =
        memfault
            .getLatestRelease(settings().toDeviceInfo())
            ?.toGenericOta()

    companion object {
        fun create(
            settings: () -> SoftwareUpdateSettings,
            metricLogger: MetricLogger,
        ): SoftwareUpdateChecker {
            val cloud = MemfaultCloud.Builder().apply {
                setApiKey(apiKey = settings().projectApiKey)
                baseApiUrl = settings().baseUrl
            }.build()
            return MemfaultSoftwareUpdateChecker(cloud, settings, metricLogger)
        }
    }

    suspend fun MemfaultCloud.getLatestRelease(deviceInfo: MemfaultDeviceInfo): MemfaultOtaPackage? =
        suspendCancellableCoroutine { cont ->
            getLatestRelease(
                deviceInfo,
                object : GetLatestReleaseCallback {
                    override fun onError(e: Exception) {
                        metricLogger.addMetric(InternalMetric(OTA_CHECK_ERROR))
                        cont.resume(null) {}
                    }

                    override fun onUpToDate() {
                        metricLogger.addMetric(InternalMetric(OTA_CHECK_NO_UPDATE_AVAILABLE))
                        cont.resume(null) {}
                    }

                    override fun onUpdateAvailable(otaPackage: MemfaultOtaPackage) {
                        metricLogger.addMetric(InternalMetric(OTA_CHECK_FOUND_UPDATE))
                        cont.resume(otaPackage) {}
                    }
                }
            )
        }
}

private fun SoftwareUpdateSettings.toDeviceInfo(): MemfaultDeviceInfo =
    MemfaultDeviceInfo(
        deviceSerial = deviceSerial,
        currentVersion = currentVersion,
        hardwareVersion = hardwareVersion,
        softwareType = softwareType
    )

private fun MemfaultOtaPackage.toGenericOta(): Ota = Ota(
    url = this.location,
    version = this.appVersion,
    releaseNotes = this.releaseNotes,
    metadata = this.extraInfo,
    isForced = this.isForced,
)

fun realSoftwareUpdateChecker(
    settings: () -> SoftwareUpdateSettings,
    metricLogger: MetricLogger,
): SoftwareUpdateChecker = MemfaultSoftwareUpdateChecker.create(settings, metricLogger)
