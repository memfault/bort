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
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Note when changing this: consider that it is serialized + persisted. Only add new fields if they have default values.
 */
@Serializable
data class Ota(
    val url: String,
    val version: String,
    val releaseNotes: String,
    @SerialName("metadata")
    val artifactMetadata: Map<String, String> = emptyMap(),
    val releaseMetadata: Map<String, String> = emptyMap(),
    val isForced: Boolean? = null,
    val isDelta: Boolean? = null,
    val size: Long? = null,
)

interface SoftwareUpdateChecker {
    suspend fun getLatestRelease(): Ota?
}

@OptIn(ExperimentalCoroutinesApi::class)
@ContributesBinding(SingletonComponent::class)
class MemfaultSoftwareUpdateChecker @Inject constructor(
    private val memfault: MemfaultCloud,
    private val settings: SoftwareUpdateSettingsProvider,
    private val metricLogger: MetricLogger,
) : SoftwareUpdateChecker {
    override suspend fun getLatestRelease(): Ota? =
        memfault.getLatestRelease(settings.get().toDeviceInfo())
            ?.toGenericOta()

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
    releaseMetadata = this.releaseExtraInfo,
    artifactMetadata = this.artifactExtraInfo,
    isForced = this.isForced,
    isDelta = this.isDelta,
    size = this.size,
)
