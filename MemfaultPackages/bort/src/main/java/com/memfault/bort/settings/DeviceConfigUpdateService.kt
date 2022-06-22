package com.memfault.bort.settings

import com.memfault.bort.http.ProjectKeyAuthenticated
import com.memfault.bort.kotlinxJsonConverterFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST

interface DeviceConfigUpdateService {
    @POST("/api/v0/device-config")
    @ProjectKeyAuthenticated
    suspend fun deviceConfig(
        @Body device: DeviceConfigArgs,
    ): DecodedDeviceConfig

    companion object {
        fun create(
            okHttpClient: OkHttpClient,
            deviceBaseUrl: String,
        ): DeviceConfigUpdateService =
            Retrofit.Builder()
                .client(okHttpClient)
                .addConverterFactory(kotlinxJsonConverterFactory())
                .baseUrl(deviceBaseUrl)
                .build()
                .create(DeviceConfigUpdateService::class.java)
    }

    @Serializable
    data class DeviceConfigArgs(
        @SerialName("device")
        val device: DeviceInfo,
    )

    @Serializable
    data class DeviceInfo(
        @SerialName("device_serial")
        val deviceSerial: String,
        @SerialName("hardware_version")
        val hardwareVersion: String,
    )
}
