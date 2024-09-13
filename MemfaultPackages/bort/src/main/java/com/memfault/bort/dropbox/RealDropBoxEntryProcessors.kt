package com.memfault.bort.dropbox

import com.memfault.bort.dagger.InjectSet
import com.memfault.bort.scopes.Scope
import com.memfault.bort.scopes.Scoped
import com.memfault.bort.scopes.coroutineScope
import com.memfault.bort.settings.SettingsFlow
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface DropBoxEntryProcessors {
    val map: Map<String, EntryProcessor>
}

@Singleton
@ContributesMultibinding(SingletonComponent::class, boundType = Scoped::class)
@ContributesBinding(SingletonComponent::class, boundType = DropBoxEntryProcessors::class)
class RealDropBoxEntryProcessors @Inject constructor(
    private val processors: InjectSet<EntryProcessor>,
    private val settingsFlow: SettingsFlow,
) : Scoped, DropBoxEntryProcessors {

    private fun processorsMap(): Map<String, EntryProcessor> =
        processors.flatMap { processor -> processor.tagPairs() }.toMap()

    private val _mapCache = MutableStateFlow(processorsMap())

    override val map: Map<String, EntryProcessor>
        get() = _mapCache.value

    override fun onEnterScope(scope: Scope) {
        scope.coroutineScope()
            .launch {
                settingsFlow.settings
                    .map { settings ->
                        settings.dropBoxSettings.otherTags
                    }
                    .distinctUntilChanged()
                    .collectLatest {
                        // Recalculate the processors map whenever the "other" tags change. The map doesn't need to be
                        // immediately up-to-date, but this allows us to avoid an app restart to re-generate the cached
                        // processors map.
                        _mapCache.update { processorsMap() }
                    }
            }
    }

    override fun onExitScope() = Unit
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DropboxModule {
    @Binds @IntoSet
    abstract fun tombstone(
        tombstone: UploadingEntryProcessor<TombstoneUploadingEntryProcessorDelegate>,
    ): EntryProcessor

    @Binds @IntoSet
    abstract fun java(
        java: UploadingEntryProcessor<JavaExceptionUploadingEntryProcessorDelegate>,
    ): EntryProcessor

    @Binds @IntoSet
    abstract fun anr(anr: UploadingEntryProcessor<AnrUploadingEntryProcessorDelegate>): EntryProcessor

    @Binds @IntoSet
    abstract fun kmsg(kmsg: UploadingEntryProcessor<KmsgUploadingEntryProcessorDelegate>): EntryProcessor

    @Binds @IntoSet
    abstract fun other(other: UploadingEntryProcessor<OtherDropBoxEntryUploadingEntryProcessorDelegate>): EntryProcessor
}
