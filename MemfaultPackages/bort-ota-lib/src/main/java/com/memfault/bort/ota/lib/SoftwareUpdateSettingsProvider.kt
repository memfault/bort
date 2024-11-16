package com.memfault.bort.ota.lib

import com.memfault.bort.FallbackOtaSettings
import com.memfault.bort.shared.SoftwareUpdateSettings
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

interface SoftwareUpdateSettingsProvider {
    fun get(): SoftwareUpdateSettings
    fun update()
}

@Singleton
@ContributesBinding(SingletonComponent::class)
class RealSoftwareUpdateSettingsProvider @Inject constructor(
    private val settingsProvider: BortSoftwareUpdateSettingsFetcher,
    private val fallbackOtaSettings: FallbackOtaSettings,
) : SoftwareUpdateSettingsProvider {
    private var settings: SoftwareUpdateSettings = fetchSettings()

    override fun update() {
        settings = fetchSettings()
    }

    private fun fetchSettings(): SoftwareUpdateSettings =
        settingsProvider.settings() ?: fallbackOtaSettings.fallbackOtaSettings()

    override fun get(): SoftwareUpdateSettings = settings
}
