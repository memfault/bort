package com.memfault.bort.ingress

import com.memfault.bort.HttpApiSettings
import com.memfault.bort.http.ProjectKeyAuthenticated
import com.memfault.bort.kotlinxJsonConverterFactory
import com.memfault.bort.uploader.HttpTaskCallFactory
import com.memfault.bort.uploader.HttpTaskOptions
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Tag

internal const val SDK_VERSION = "0.5.0"

internal const val SDK_EVENT_WORK_TAG = "com.memfault.bort.work.tag.UPLOAD_SDK_EVENT"
internal const val REBOOT_EVENT_WORK_TAG = "com.memfault.bort.work.tag.UPLOAD_REBOOT_EVENT"

interface IngressService {
    @POST("/api/v0/sdk-events")
    @ProjectKeyAuthenticated
    fun uploadCollection(
        @Body sdkEventCollection: SdkEventCollection,
        @Tag httpTaskOptions: HttpTaskOptions = HttpTaskOptions(
            taskTags = listOf(SDK_EVENT_WORK_TAG)
        )
    ): Call<Unit>

    @POST("/api/v0/events")
    @ProjectKeyAuthenticated
    fun uploadRebootEvents(
        @Body events: List<RebootEvent>,
        @Tag httpTaskOptions: HttpTaskOptions = HttpTaskOptions(
            taskTags = listOf(REBOOT_EVENT_WORK_TAG)
        )
    ): Call<Unit>

    companion object {
        fun create(
            httpApiSettings: HttpApiSettings,
            httpTaskCallFactory: HttpTaskCallFactory
        ): IngressService =
            Retrofit.Builder()
                .baseUrl(httpApiSettings.ingressBaseUrl.toHttpUrl())
                .callFactory(httpTaskCallFactory)
                .addConverterFactory(
                    kotlinxJsonConverterFactory()
                )
                .build()
                .create(IngressService::class.java)
    }
}
