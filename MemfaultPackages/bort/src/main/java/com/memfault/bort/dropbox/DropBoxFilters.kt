package com.memfault.bort.dropbox

import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.settings.DropBoxSettings
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

interface DropBoxFilters {
    fun tagFilter(): List<String>
}

@ContributesBinding(SingletonComponent::class)
class RealDropBoxFilters @Inject constructor(
    private val entryProcessors: DropBoxEntryProcessors,
    private val settings: DropBoxSettings,
    private val bortEnabledProvider: BortEnabledProvider,
) : DropBoxFilters {
    override fun tagFilter(): List<String> = if (bortEnabledProvider.isEnabled()) {
        entryProcessors.map.keys.subtract(settings.excludedTags).toList()
    } else {
        emptyList()
    }
}
