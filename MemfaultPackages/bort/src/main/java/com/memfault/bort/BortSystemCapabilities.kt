package com.memfault.bort

import android.os.RemoteException
import com.memfault.dumpster.IDumpster

/**
 * Helper class to determine whether certain features are supported
 * based on the capabilities of the components of the Bort SDK that
 * are part of the system image, such as MemfaultUsageReporter,
 * MemfaultDumpster, etc.
 */
class BortSystemCapabilities(
    dumpsterClient: DumpsterClient,
    private val reporterServiceConnector: ReporterServiceConnector
) {
    private val dumpsterVersion: Int? by lazy { dumpsterClient.availableVersion() }

    private val reporterServiceVersion = CachedAsyncProperty(this::getReporterServiceVersion)

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
            else -> version >= ReporterClient.MINIMUM_VALID_VERSION
        }

    fun supportsDynamicSettings() = supportsCaliperDeviceInfo()

    fun supportsRebootEvents() = supportsCaliperDeviceInfo()

    suspend fun supportsCaliperMetrics() = supportsCaliperDeviceInfo() && hasMinimumValidReporterService()

    suspend fun supportsCaliperLogcatCollection() = supportsCaliperDeviceInfo() && hasMinimumValidReporterService()

    suspend fun supportsCaliperDropBoxTraces() = supportsCaliperDeviceInfo() && hasMinimumValidReporterService()
}

class CachedAsyncProperty<out T>(val factory: suspend () -> T) {
    private var value: CachedValue<T> = CachedValue.Absent

    suspend fun get(): T {
        return when (val cached = value) {
            is CachedValue.Value -> cached.value
            CachedValue.Absent -> factory().also { value = CachedValue.Value(it) }
        }
    }

    fun invalidate() {
        value = CachedValue.Absent
    }

    private sealed class CachedValue<out T> {
        object Absent : CachedValue<Nothing>()
        class Value<T>(val value: T) : CachedValue<T>()
    }
}
