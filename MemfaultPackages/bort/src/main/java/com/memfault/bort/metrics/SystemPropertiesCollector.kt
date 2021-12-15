package com.memfault.bort.metrics

import androidx.annotation.VisibleForTesting
import com.memfault.bort.DumpsterClient
import com.memfault.bort.metrics.PropertyType.BOOL
import com.memfault.bort.metrics.PropertyType.DOUBLE
import com.memfault.bort.metrics.PropertyType.INT
import com.memfault.bort.metrics.PropertyType.STRING
import com.memfault.bort.settings.MetricsSettings
import com.memfault.bort.shared.ClientServerMode
import javax.inject.Inject

/**
 * Collects system properties of interest, and passes them on to the device properties database.
 */
class SystemPropertiesCollector @Inject constructor(
    private val devicePropertiesStore: DevicePropertiesStore,
    private val settings: MetricsSettings,
    private val dumpsterClient: DumpsterClient,
) {
    suspend fun updateSystemProperties() {
        val systemProperties = dumpsterClient.getprop() ?: return
        val systemPropertyTypes = dumpsterClient.getpropTypes() ?: return
        updateSystemPropertiesWith(systemProperties = systemProperties, systemPropertyTypes = systemPropertyTypes)
    }

    @VisibleForTesting
    internal suspend fun updateSystemPropertiesWith(
        systemProperties: Map<String, String>,
        systemPropertyTypes: Map<String, String>,
    ) {
        val properties = settings.systemProperties.toSet()
        systemProperties.forEach { (key, value) ->
            val internal = key in INTERNAL_PROPERTIES
            if (key in properties || internal) {
                val type = systemPropertyTypes[key]
                devicePropertiesStore.upsert(
                    name = SYSTEM_PROPERTY_PREFIX + key,
                    value = value,
                    type = type.asPropertyType(),
                    internal = internal,
                )
            }
        }
    }

    /**
     * Android system properties all have a string key and string value. But there is metadata (which we get using
     * getpropTypes()) to define typing for each property. We map those to internal types.
     *
     * https://source.android.com/devices/architecture/configuration/add-system-properties#step3-add-levels-to-system
     * Android does define other types (e.g. enum), but we will simply treat them as strings.
     */
    private fun String?.asPropertyType() = when (this) {
        "bool" -> BOOL
        "int" -> INT
        "double" -> DOUBLE
        "string" -> STRING
        else -> STRING
    }

    companion object {
        /**
         * Properties which will be reported as internal metrics.
         */
        private val INTERNAL_PROPERTIES = setOf(
            "vendor.memfault.bort.version.sdk",
            "vendor.memfault.bort.version.patch",
            ClientServerMode.SYSTEM_PROP,
        )
        private const val SYSTEM_PROPERTY_PREFIX = "sysprop."
    }
}
