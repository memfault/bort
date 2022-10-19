package com.memfault.bort

import android.content.Context
import com.memfault.bort.metrics.DevicePropertiesStore
import com.memfault.bort.shared.Logger
import javax.inject.Inject

class IntegrationChecker @Inject constructor(
    private val context: Context,
    private val devicePropertiesStore: DevicePropertiesStore,
) {
    suspend fun checkIntegrationAndReport() {
        val valid = isSeContextValid()
        devicePropertiesStore.upsert(name = "se_context_valid", value = valid, internal = true)
    }

    private fun isSeContextValid(): Boolean {
        try {
            val lsOutput = Runtime.getRuntime().exec("ls -lZd ${context.dataDir}").inputStream.bufferedReader().use {
                it.readText()
            }
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
}
