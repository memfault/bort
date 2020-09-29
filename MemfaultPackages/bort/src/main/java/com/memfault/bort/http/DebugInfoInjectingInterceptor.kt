package com.memfault.bort.http

import com.memfault.bort.DeviceIdProvider
import com.memfault.bort.SettingsProvider
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.*

internal const val QUERY_PARAM_UPSTREAM_VERSION_NAME = "upstreamVersionName"
internal const val QUERY_PARAM_UPSTREAM_VERSION_CODE = "upstreamVersionCode"
internal const val QUERY_PARAM_UPSTREAM_GIT_SHA = "upstreamGitSha"
internal const val QUERY_PARAM_VERSION_NAME = "versionName"
internal const val QUERY_PARAM_VERSION_CODE = "versionCode"
internal const val QUERY_PARAM_DEVICE_ID = "deviceId"
internal const val X_REQUEST_ID = "X-Request-ID"

/** Inject debug information for requests to memfault or to a local api server */
class DebugInfoInjectingInterceptor(
    private val settingsProvider: SettingsProvider,
    private val deviceIdProvider: DeviceIdProvider
) : Interceptor {

    fun transformRequest(request: Request): Request {
        val url = request.url()
        when {
            url.host() in setOf("127.0.0.1", "localhost") && url.encodedPathSegments().firstOrNull() == "api" -> {
                // Fall through
            }
            url.topPrivateDomain().equals("memfault.com", ignoreCase = true) -> {
                // Fall through
            }
            else -> return request
        }
        val transformedUrl = url.newBuilder().apply {
            linkedMapOf(
                QUERY_PARAM_UPSTREAM_VERSION_NAME to settingsProvider.upstreamVersionName(),
                QUERY_PARAM_UPSTREAM_VERSION_CODE to settingsProvider.upstreamVersionCode()
                    .toString(),
                QUERY_PARAM_UPSTREAM_GIT_SHA to settingsProvider.upstreamGitSha(),
                QUERY_PARAM_VERSION_NAME to settingsProvider.appVersionName(),
                QUERY_PARAM_VERSION_CODE to settingsProvider.appVersionCode().toString(),
                QUERY_PARAM_DEVICE_ID to deviceIdProvider.deviceId()
            ).forEach { key, value -> addQueryParameter(key, value) }
        }.build()
        return request.newBuilder()
            .addHeader(X_REQUEST_ID, UUID.randomUUID().toString())
            .url(transformedUrl)
            .build()
    }

    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            transformRequest(chain.request())
        )
}
