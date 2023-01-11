package com.memfault.bort.settings

import com.memfault.bort.BortJson
import com.memfault.bort.DeviceInfo
import com.memfault.bort.SOFTWARE_TYPE
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
        @SerialName("software_version")
        val softwareVersion: String,
        @SerialName("software_type")
        val softwareType: String,
    ) {
        companion object {
            fun from(input: String): DeviceInfo = BortJson.decodeFromString(serializer(), input)
        }
    }
}

fun DeviceConfigUpdateService.DeviceInfo.toJson() =
    BortJson.encodeToString(DeviceConfigUpdateService.DeviceInfo.serializer(), this)

fun DeviceInfo.asDeviceConfigInfo(): DeviceConfigUpdateService.DeviceInfo = DeviceConfigUpdateService.DeviceInfo(
    deviceSerial = deviceSerial,
    hardwareVersion = hardwareVersion,
    softwareVersion = softwareVersion,
    softwareType = SOFTWARE_TYPE,
)
