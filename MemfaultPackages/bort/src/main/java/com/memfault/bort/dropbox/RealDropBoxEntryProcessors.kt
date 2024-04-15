package com.memfault.bort.dropbox

import com.memfault.bort.dagger.InjectSet
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

class DropBoxEntryProcessors @Inject constructor(
    processors: InjectSet<EntryProcessor>,
) {
    val map = processors.flatMap { it.tagPairs() }.toMap()
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
        tombstone: UploadingEntryProcessor<JavaExceptionUploadingEntryProcessorDelegate>,
    ): EntryProcessor

    @Binds @IntoSet
    abstract fun anr(tombstone: UploadingEntryProcessor<AnrUploadingEntryProcessorDelegate>): EntryProcessor

    @Binds @IntoSet
    abstract fun kmsg(tombstone: UploadingEntryProcessor<KmsgUploadingEntryProcessorDelegate>): EntryProcessor
}
