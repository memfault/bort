package com.memfault.bort.logcat

import android.app.Application
import com.memfault.bort.DevMode
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.tokenbucket.RealTokenBucketFactory
import com.memfault.bort.tokenbucket.RealTokenBucketStorage
import com.memfault.bort.tokenbucket.RealTokenBucketStore
import com.memfault.bort.tokenbucket.SelinuxViolations
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@SelinuxViolations
@ContributesMultibinding(
    scope = SingletonComponent::class,
    boundType = TokenBucketStore::class,
    ignoreQualifier = true,
)
@ContributesBinding(
    scope = SingletonComponent::class,
    boundType = TokenBucketStore::class,
)
class SelinuxViolationTokenBucketStore
@Inject constructor(
    application: Application,
    settingsProvider: SettingsProvider,
    metrics: BuiltinMetricsStore,
    devMode: DevMode,
) : TokenBucketStore by RealTokenBucketStore(
    storage = RealTokenBucketStorage.createFor(application, "selinux_violation_events"),
    getMaxBuckets = {
        settingsProvider.selinuxViolationSettings.rateLimitingSettings.maxBuckets
    },
    getTokenBucketFactory = {
        RealTokenBucketFactory.from(settingsProvider.selinuxViolationSettings.rateLimitingSettings, metrics)
    },
    devMode = devMode,
)
