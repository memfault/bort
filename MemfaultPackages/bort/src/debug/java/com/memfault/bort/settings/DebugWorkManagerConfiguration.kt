package com.memfault.bort.settings

import android.util.Log
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent

@ContributesBinding(SingletonComponent::class)
object DebugWorkManagerConfiguration : WorkManagerConfiguration {
    override val logLevel = Log.VERBOSE
}
