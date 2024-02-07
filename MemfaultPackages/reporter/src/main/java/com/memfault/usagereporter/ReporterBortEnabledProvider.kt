package com.memfault.usagereporter

import com.memfault.bort.settings.BortEnabledProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ContributesBinding(SingletonComponent::class)
class ReporterBortEnabledProvider
@Inject constructor(
    private val reporterSettings: ReporterSettings,
) : BortEnabledProvider {
    override fun setEnabled(isOptedIn: Boolean) =
        error("Cannot set Bort enabled state from UsageReporter")

    override fun isEnabled(): Boolean = reporterSettings.settings.value.bortEnabled

    override fun isEnabledFlow(): Flow<Boolean> = reporterSettings.settings.map { it.bortEnabled }

    override fun requiresRuntimeEnable(): Boolean =
        error("Cannot check for Bort runtime enabled from UsageReporter")
}
