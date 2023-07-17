package com.memfault.bort

import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@ContributesBinding(SingletonComponent::class)
class NoOpConfigureStrictMode @Inject constructor() : ConfigureStrictMode {
    override fun configure() = Unit
}
