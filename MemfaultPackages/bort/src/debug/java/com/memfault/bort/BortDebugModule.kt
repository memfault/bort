package com.memfault.bort

import com.memfault.bort.shared.JitterDelayProvider
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent

@Module
@ContributesTo(SingletonComponent::class, replaces = [BortReleaseModule::class])
class BortDebugModule {
    companion object {
        @Provides
        fun applyJitter() = JitterDelayProvider.ApplyJitter.DO_NOT_APPLY
    }
}
