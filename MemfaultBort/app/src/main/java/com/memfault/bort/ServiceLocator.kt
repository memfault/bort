package com.memfault.bort

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

// A simple holder for application components
// This example uses a service locator pattern over a DI framework for simplicity and ease of use;
// if you would prefer to see a DI framework being used, please let us know!
interface ServiceLocator {
    fun settingsProvider(): SettingsProvider

    fun okHttpClient(): OkHttpClient

    fun retrofitClient(): Retrofit
}

internal data class SimpleServiceLocator constructor(
    private val settingsProvider: SettingsProvider,
    private val okHttpClient: OkHttpClient,
    private val retrofit: Retrofit
): ServiceLocator {
    override fun settingsProvider(): SettingsProvider = settingsProvider

    override fun okHttpClient(): OkHttpClient = okHttpClient

    override fun retrofitClient(): Retrofit = retrofit

    companion object {
        internal val retrofitConverterFactory by lazy {
            Json(JsonConfiguration.Stable)
                .asConverterFactory(MediaType.get("application/json"))
        }

        internal fun from(settingsProvider: SettingsProvider): SimpleServiceLocator {
            val okHttpClient = OkHttpClient.Builder()
                .build()
            val retrofit = Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(HttpUrl.get(BuildConfig.MEMFAULT_FILES_BASE_URL))
                .addConverterFactory(
                    retrofitConverterFactory
                )
                .build()
            return SimpleServiceLocator(
                settingsProvider,
                okHttpClient,
                retrofit
            )
        }
    }
}
