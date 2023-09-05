package com.memfault.bort

import com.memfault.bort.shared.JitterDelayConfiguration
import com.memfault.bort.shared.JitterDelayProvider.ApplyJitter
import com.memfault.bort.shared.JitterDelayProvider.ApplyJitter.DO_NOT_APPLY
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent

@ContributesBinding(SingletonComponent::class)
object DebugJitterDelayConfiguration : JitterDelayConfiguration {
    override fun applyJitter(): ApplyJitter = DO_NOT_APPLY
}
