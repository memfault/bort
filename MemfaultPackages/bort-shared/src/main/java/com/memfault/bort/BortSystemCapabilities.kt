package com.memfault.bort

import android.os.RemoteException
import com.memfault.bort.shared.BuildConfig
import com.memfault.bort.shared.MINIMUM_VALID_VERSION
import com.memfault.dumpster.IDumpster
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class to determine whether certain features are supported
 * based on the capabilities of the components of the Bort SDK that
 * are part of the system image, such as MemfaultUsageReporter,
 * MemfaultDumpster, etc.
 */
@Singleton
class BortSystemCapabilities @Inject constructor(
    dumpsterClient: DumpsterClient,
    private val reporterServiceConnector: ReporterServiceConnector,
) {
    private val dumpsterVersion: Int? by lazy { dumpsterClient.availableVersion() }

    val reporterServiceVersion = CachedAsyncProperty(this::getReporterServiceVersion)

    private suspend fun getReporterServiceVersion() =
        try {
            reporterServiceConnector.connect { getConnection ->
                getConnection().getVersion()
            }
        } catch (e: RemoteException) {
            null
        }

    private fun supportsCaliperDeviceInfo(): Boolean =
        // getprop is used to gather device info, this requires MemfaultDumpster. See RealDeviceInfoProvider.
        when (val version = dumpsterVersion) {
            null -> false
            else -> version >= IDumpster.VERSION_INITIAL
        }

    private suspend fun hasMinimumValidReporterService(): Boolean =
        when (val version = reporterServiceVersion.get()) {
            null -> false
            else -> version >= MINIMUM_VALID_VERSION
        }

    suspend fun supportsCaliperMetrics() = supportsCaliperDeviceInfo() && hasMinimumValidReporterService()

    fun isStructuredLogDInstalled(): Boolean = File("/system/bin/MemfaultStructuredLogd").exists()

    fun useBortMetricsDb(): Boolean = BuildConfig.INTERNAL_METRICS_DB && !isStructuredLogDInstalled()
}
