package com.memfault.bort.settings

import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Emits settings updates. Note that what we emit is an essentially-mutable [SettingsProvider], so it's not guaranteed
 * to contain the correct values if there are updates in quick succession (but that is unlikely). This is the same as
 * receiving a callback from [SettingsUpdateCallback].
 */
interface SettingsFlow {
    val settings: Flow<SettingsProvider>
}

@ContributesBinding(SingletonComponent::class)
@Singleton
class RealSettingsFlow @Inject constructor(
    private val settingsProvider: SettingsProvider,
    dynamicSettingsProvider: DynamicSettingsProvider,
) : SettingsFlow {
    // This cannot be done inside DynamicSettingsProvider, because SettingsProvider might be a TestSettingsProvider.
    override val settings = dynamicSettingsProvider.settingsChangedFlow.map { settingsProvider }
}
