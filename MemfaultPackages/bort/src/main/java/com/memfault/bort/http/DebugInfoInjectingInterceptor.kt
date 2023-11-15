package com.memfault.bort.http

import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.InstallationIdProvider
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.shared.SdkVersionInfo
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.UUID
import javax.inject.Inject

internal const val QUERY_PARAM_UPSTREAM_VERSION_NAME = "upstreamVersionName"
internal const val QUERY_PARAM_UPSTREAM_VERSION_CODE = "upstreamVersionCode"
internal const val QUERY_PARAM_UPSTREAM_GIT_SHA = "upstreamGitSha"
internal const val QUERY_PARAM_VERSION_NAME = "versionName"
internal const val QUERY_PARAM_VERSION_CODE = "versionCode"
internal const val QUERY_PARAM_DEVICE_ID = "deviceId"
internal const val QUERY_PARAM_DEVICE_SERIAL = "deviceSerial"
internal const val X_REQUEST_ID = "X-Request-ID"

/** Inject debug information for requests to memfault or to a local api server */
@ContributesMultibinding(SingletonComponent::class)
class DebugInfoInjectingInterceptor @Inject constructor(
    private val sdkVersionInfo: SdkVersionInfo,
    private val installationIdProvider: InstallationIdProvider,
    private val bortEnabledProvider: BortEnabledProvider,
    private val deviceInfoProvider: DeviceInfoProvider,
) : Interceptor {

    fun transformRequest(request: Request): Request {
        val url = request.url
        when {
            url.host in setOf("127.0.0.1", "localhost") && url.encodedPathSegments.firstOrNull() == "api" -> {
                // Fall through
            }
            url.topPrivateDomain().equals("memfault.com", ignoreCase = true) -> {
                // Fall through
            }
            else -> return request
        }
        val xRequestId = UUID.randomUUID().toString()
        val deviceSerial =
            if (bortEnabledProvider.isEnabled()) {
                runBlocking { deviceInfoProvider.getDeviceInfo().deviceSerial }
            } else {
                ""
            }
        val transformedUrl = url.newBuilder().apply {
            linkedMapOf(
                QUERY_PARAM_DEVICE_SERIAL to deviceSerial,
                QUERY_PARAM_UPSTREAM_VERSION_NAME to sdkVersionInfo.upstreamVersionName,
                QUERY_PARAM_UPSTREAM_VERSION_CODE to sdkVersionInfo.upstreamVersionCode
                    .toString(),
                QUERY_PARAM_UPSTREAM_GIT_SHA to sdkVersionInfo.upstreamGitSha,
                QUERY_PARAM_VERSION_NAME to sdkVersionInfo.appVersionName,
                QUERY_PARAM_VERSION_CODE to sdkVersionInfo.appVersionCode.toString(),
                QUERY_PARAM_DEVICE_ID to installationIdProvider.id(),
                X_REQUEST_ID to xRequestId,
            ).forEach { key, value -> addQueryParameter(key, value) }
        }.build()
        return request.newBuilder()
            .addHeader(X_REQUEST_ID, xRequestId)
            .url(transformedUrl)
            .build()
    }

    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            transformRequest(chain.request()),
        )
}
