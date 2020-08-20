package com.memfault.bort

import com.memfault.bort.http.ProjectKeyAuthenticated
import com.memfault.bort.uploader.HttpTaskCallFactory
import com.memfault.bort.uploader.HttpTaskOptions
import com.memfault.bort.uploader.SDK_EVENT_WORK_TAG
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Tag

@Serializable
data class SdkEvent(
    val timestamp: Long,
    val name: String
)

@Serializable
data class SdkEventCollection(
    val opaque_device_id: String,
    val sdk_version: String,
    val sdk_events: List<SdkEvent>
)

interface IngressService {
    @POST("/api/v0/sdk-events")
    @ProjectKeyAuthenticated
    fun uploadCollection(
        @Body sdkEventCollection: SdkEventCollection,
        @Tag httpTaskOptions: HttpTaskOptions = HttpTaskOptions(
            maxAttempts = 3,
            taskTags = listOf(SDK_EVENT_WORK_TAG)
        )
    ): Call<Unit>
}

fun makeIngressService(
    settingsProvider: SettingsProvider,
    httpTaskCallFactory: HttpTaskCallFactory
): IngressService =
    Retrofit.Builder()
        .baseUrl(HttpUrl.get(settingsProvider.ingressBaseUrl()))
        .callFactory(httpTaskCallFactory)
        .addConverterFactory(
            kotlinxJsonConverterFactory()
        )
        .build()
        .create(IngressService::class.java)
