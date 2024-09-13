package com.memfault.bort.dropbox

import android.app.Application
import com.memfault.bort.DevMode
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.tokenbucket.Other
import com.memfault.bort.tokenbucket.RealTokenBucketFactory
import com.memfault.bort.tokenbucket.RealTokenBucketStorage
import com.memfault.bort.tokenbucket.RealTokenBucketStore
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@Other
@ContributesMultibinding(
    scope = SingletonComponent::class,
    boundType = TokenBucketStore::class,
    ignoreQualifier = true,
)
@ContributesBinding(
    scope = SingletonComponent::class,
    boundType = TokenBucketStore::class,
)
class OtherDropBoxEntryTokenBucketStore
@Inject constructor(
    application: Application,
    settingsProvider: SettingsProvider,
    metrics: BuiltinMetricsStore,
    devMode: DevMode,
) : TokenBucketStore by RealTokenBucketStore(
    storage = RealTokenBucketStorage.createFor(application, "other_dropboxentry"),
    getMaxBuckets = {
        settingsProvider.dropBoxSettings.otherDropBoxEntryRateLimitingSettings.maxBuckets
    },
    getTokenBucketFactory = {
        RealTokenBucketFactory.from(
            settingsProvider.dropBoxSettings.otherDropBoxEntryRateLimitingSettings,
            metrics,
        )
    },
    devMode = devMode,
)
