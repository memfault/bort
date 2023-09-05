package com.memfault.bort

import android.app.Application
import com.memfault.bort.metrics.DevicePropertiesStore
import com.memfault.bort.shared.Logger
import javax.inject.Inject
import kotlinx.serialization.json.JsonPrimitive

class IntegrationChecker @Inject constructor(
    private val application: Application,
) {
    fun checkIntegrationAndReport(devicePropertiesStore: DevicePropertiesStore): Map<String, JsonPrimitive> {
        val valid = isSeContextValid()
        devicePropertiesStore.upsert(name = METRIC_NAME, value = valid, internal = true)
        return mapOf(METRIC_NAME to JsonPrimitive(valid))
    }

    private fun isSeContextValid(): Boolean {
        try {
            val lsOutput = Runtime.getRuntime().exec("ls -lZd ${application.dataDir}")
                .inputStream.bufferedReader()
                .use { it.readText() }
            return lsOutput.contains("u:object_r:bort_app_data_file:s0").also { valid ->
                if (!valid) {
                    Logger.e("Bort secontext invalid: $lsOutput")
                }
            }
        } catch (e: Exception) {
            Logger.w("Error checking Bort secontext", e)
            return false
        }
    }

    companion object {
        private const val METRIC_NAME = "se_context_valid"
    }
}
