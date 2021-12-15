package com.memfault.bort

import com.memfault.bort.shared.JitterDelayProvider
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent

/**
 * Bindings which are applicable only to the "releaseTest" build. These replace those defined in ReleaseTestModule in
 * the "release" build
 */
@Module
@ContributesTo(SingletonComponent::class, replaces = [ReleaseModule::class])
class ReleaseTestModule {
    companion object {
        @Provides
        fun applyJitter() = JitterDelayProvider.ApplyJitter.DO_NOT_APPLY
    }
}
