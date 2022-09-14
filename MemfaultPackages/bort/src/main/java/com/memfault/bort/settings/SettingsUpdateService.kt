package com.memfault.bort.settings

import com.memfault.bort.SOFTWARE_TYPE
import com.memfault.bort.http.ProjectKeyAuthenticated
import com.memfault.bort.kotlinxJsonConverterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

interface SettingsUpdateService {
    @GET("/api/v0/sdk-settings")
    @ProjectKeyAuthenticated
    suspend fun settings(
        @Query(QUERY_PARAM_DEVICE_SERIAL) deviceSerial: String,
        @Query(QUERY_PARAM_SOFTWARE_VERSION) softwareVersion: String,
        @Query(QUERY_PARAM_HARDWARE_VERSION) hardwareVersion: String,
        @Query(QUERY_PARAM_SOFTWARE_TYPE) softwareType: String = SOFTWARE_TYPE,
    ): FetchedSettings.FetchedSettingsContainer

    companion object {
        private const val QUERY_PARAM_DEVICE_SERIAL = "device_serial"
        private const val QUERY_PARAM_SOFTWARE_VERSION = "software_version"
        private const val QUERY_PARAM_HARDWARE_VERSION = "hardware_version"
        private const val QUERY_PARAM_SOFTWARE_TYPE = "software_type"

        fun create(
            okHttpClient: OkHttpClient,
            deviceBaseUrl: String,
        ): SettingsUpdateService =
            Retrofit.Builder()
                .client(okHttpClient)
                .addConverterFactory(kotlinxJsonConverterFactory())
                .baseUrl(deviceBaseUrl)
                .build()
                .create(SettingsUpdateService::class.java)
    }
}
