package com.memfault.bort.ota

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.memfault.bort.BasicCommandTimout
import com.memfault.bort.ota.lib.ABUpdateActionHandler
import com.memfault.bort.ota.lib.DEFAULT_STATE_PREFERENCE_FILE
import com.memfault.bort.ota.lib.IsAbDevice
import com.memfault.bort.ota.lib.RecoveryBasedUpdateActionHandler
import com.memfault.bort.ota.lib.SoftwareUpdateSettingsProvider
import com.memfault.bort.ota.lib.UpdateActionHandler
import com.memfault.bort.shared.BASIC_COMMAND_TIMEOUT_MS
import com.memfault.cloud.sdk.MemfaultCloud
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module
class OtaAppModule {
    @Provides
    fun updaterSharedPreferences(application: Application): SharedPreferences =
        application.getSharedPreferences(DEFAULT_STATE_PREFERENCE_FILE, Context.MODE_PRIVATE)

    @Provides
    @BasicCommandTimout
    fun basicTimeout(): Long = BASIC_COMMAND_TIMEOUT_MS

    @Provides
    fun createDefaultActionHandlerFactory(
        isAbDevice: IsAbDevice,
        recoveryBasedUpdateActionHandler: Lazy<RecoveryBasedUpdateActionHandler>,
        abUpdateActionHandler: Lazy<ABUpdateActionHandler>,
    ): UpdateActionHandler =
        if (isAbDevice()) abUpdateActionHandler.get()
        else recoveryBasedUpdateActionHandler.get()

    @Provides
    fun memfaultCloud(settings: SoftwareUpdateSettingsProvider) = MemfaultCloud.Builder().apply {
        setApiKey(apiKey = settings.get().projectApiKey)
        baseApiUrl = settings.get().baseUrl
    }.build()
}
