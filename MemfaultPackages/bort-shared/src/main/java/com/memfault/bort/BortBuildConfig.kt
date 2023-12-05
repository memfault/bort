package com.memfault.bort

import com.memfault.bort.shared.BuildConfig
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent

/**
 * Provides access to BuildConfig properties.
 *
 * Interface allows for testing.
 */
interface BortBuildConfig {
    val bortAppId: String
    val otaAppId: String?
}

@ContributesBinding(SingletonComponent::class)
object RealBortBuildConfig : BortBuildConfig {
    override val bortAppId: String = BuildConfig.BORT_APPLICATION_ID
    override val otaAppId: String? = BuildConfig.BORT_OTA_APPLICATION_ID.takeIf { it.isNotBlank() }
}
