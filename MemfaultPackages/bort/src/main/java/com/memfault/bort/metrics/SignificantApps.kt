package com.memfault.bort.metrics

import com.memfault.bort.BortBuildConfig
import com.memfault.bort.settings.SignificantAppsSettings
import com.memfault.bort.shared.APPLICATION_ID_MEMFAULT_USAGE_REPORTER
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

data class SignificantApp(
    val packageName: String,
    val identifier: String,
    val internal: Boolean,
)

interface SignificantAppsProvider {
    fun internalApps(): List<SignificantApp>
    fun externalApps(): List<SignificantApp>

    fun apps(): List<SignificantApp> = internalApps() + externalApps()
}

@ContributesBinding(SingletonComponent::class)
class RealSignificantAppsProvider
@Inject constructor(
    private val bortBuildConfig: BortBuildConfig,
    private val significantAppsSettings: SignificantAppsSettings,
) : SignificantAppsProvider {

    override fun internalApps(): List<SignificantApp> = listOfNotNull(
        SignificantApp(
            packageName = bortBuildConfig.bortAppId,
            identifier = "bort",
            internal = true,
        ),
        bortBuildConfig.otaAppId?.let {
            SignificantApp(
                packageName = it,
                identifier = "ota",
                internal = true,
            )
        },
        SignificantApp(
            packageName = APPLICATION_ID_MEMFAULT_USAGE_REPORTER,
            identifier = "reporter",
            internal = true,
        ),
    )

    override fun externalApps(): List<SignificantApp> = significantAppsSettings.packages
        .takeIf { significantAppsSettings.collectionEnabled }
        ?.map { packageName ->
            SignificantApp(
                packageName = packageName,
                identifier = packageName,
                internal = false,
            )
        }
        ?: emptyList()
}
