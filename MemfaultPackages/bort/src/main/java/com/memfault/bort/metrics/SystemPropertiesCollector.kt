package com.memfault.bort.metrics

import android.app.Application
import android.telephony.TelephonyManager
import com.memfault.bort.DumpsterClient
import com.memfault.bort.metrics.SystemPropertiesCollector.TypedSyspropVal.BoolVal
import com.memfault.bort.metrics.SystemPropertiesCollector.TypedSyspropVal.DoubleVal
import com.memfault.bort.metrics.SystemPropertiesCollector.TypedSyspropVal.LongVal
import com.memfault.bort.metrics.SystemPropertiesCollector.TypedSyspropVal.StringVal
import com.memfault.bort.settings.MetricsSettings
import com.memfault.bort.shared.ClientServerMode
import com.memfault.bort.shared.Logger
import javax.inject.Inject

data class DeviceSystemProperties(
    val properties: Map<String, String>,
    val propertyTypes: Map<String, String>,
)

/**
 * Collects system properties of interest, and passes them on to the device properties database.
 */
class SystemPropertiesCollector @Inject constructor(
    private val settings: MetricsSettings,
    private val dumpsterClient: DumpsterClient,
    private val application: Application,
) {
    suspend fun collect(): DeviceSystemProperties? {
        val systemProperties = dumpsterClient.getprop() ?: return null
        val systemPropertyTypes = dumpsterClient.getpropTypes() ?: return null
        return DeviceSystemProperties(
            properties = systemProperties,
            propertyTypes = systemPropertyTypes,
        )
    }

    fun record(deviceSystemProperties: DeviceSystemProperties, devicePropertiesStore: DevicePropertiesStore) {
        updateSystemPropertiesWith(
            systemProperties = deviceSystemProperties.properties,
            systemPropertyTypes = deviceSystemProperties.propertyTypes,
            devicePropertiesStore = devicePropertiesStore,
        )
        if (settings.recordImei) {
            try {
                application.getSystemService(TelephonyManager::class.java)?.let { telephony ->
                    telephony.imei?.let { imei ->
                        devicePropertiesStore.upsert(IMEI_METRIC, imei)
                    }
                }
            } catch (e: SecurityException) {
                Logger.d("Error fetching imei")
            }
        }
    }

    private fun updateSystemPropertiesWith(
        systemProperties: Map<String, String>,
        systemPropertyTypes: Map<String, String>,
        devicePropertiesStore: DevicePropertiesStore,
    ) {
        val properties = settings.systemProperties.toSet()
        systemProperties.forEach { (key, value) ->
            val internal = key in INTERNAL_PROPERTIES
            if (key in properties || internal) {
                val type = systemPropertyTypes[key]

                /**
                 * Android system properties all have a string key and string value. But there is metadata (which we get using
                 * getpropTypes()) to define typing for each property. We map those to internal types.
                 *
                 * https://source.android.com/devices/architecture/configuration/add-system-properties#step3-add-levels-to-system
                 * Android does define other types (e.g. enum), but we will simply treat them as strings.
                 */
                val typedValue: TypedSyspropVal = when (type) {
                    "bool" -> value.toBooleanStrictOrNull()?.let { BoolVal(it) }
                    "int" -> value.toLongOrNull()?.let { LongVal(it) }
                    "double" -> value.toDoubleOrNull()?.let { DoubleVal(it) }
                    "string" -> StringVal(value)
                    else -> StringVal(value)
                } ?: StringVal(value)
                when (typedValue) {
                    is BoolVal -> devicePropertiesStore.upsert(
                        name = SYSTEM_PROPERTY_PREFIX + key,
                        value = typedValue.value,
                        internal = internal,
                    )
                    is DoubleVal -> devicePropertiesStore.upsert(
                        name = SYSTEM_PROPERTY_PREFIX + key,
                        value = typedValue.value,
                        internal = internal,
                    )
                    is LongVal -> devicePropertiesStore.upsert(
                        name = SYSTEM_PROPERTY_PREFIX + key,
                        value = typedValue.value,
                        internal = internal,
                    )
                    is StringVal -> devicePropertiesStore.upsert(
                        name = SYSTEM_PROPERTY_PREFIX + key,
                        value = typedValue.value,
                        internal = internal,
                    )
                }
            }
        }
    }

    private sealed class TypedSyspropVal {
        class BoolVal(val value: Boolean) : TypedSyspropVal()
        class LongVal(val value: Long) : TypedSyspropVal()
        class DoubleVal(val value: Double) : TypedSyspropVal()
        class StringVal(val value: String) : TypedSyspropVal()
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
        const val IMEI_METRIC = "phone.imei"
    }
}
