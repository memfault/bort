package com.memfault.bort

import android.content.SharedPreferences
import com.memfault.bort.settings.DataScrubbingSettings
import com.memfault.bort.settings.DropBoxSettings
import com.memfault.bort.settings.DynamicSettingsProvider
import com.memfault.bort.settings.HttpApiSettings
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.BuildConfig
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.LogcatPriority
import com.memfault.bort.shared.PreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

class TestOverrideSettings @Inject constructor(
    sharedPreferences: SharedPreferences,
) {
    val projectKeyProvider = object : PreferenceKeyProvider<String>(
        sharedPreferences = sharedPreferences,
        defaultValue = BuildConfig.MEMFAULT_PROJECT_API_KEY,
        preferenceKey = "test-project-api-key"
    ) {}

    val useMarUpload = object : PreferenceKeyProvider<Boolean>(
        sharedPreferences = sharedPreferences,
        defaultValue = false,
        preferenceKey = "test-use-mar-upload"
    ) {}
}

@ContributesBinding(SingletonComponent::class, replaces = [DynamicSettingsProvider::class])
class TestSettingsProvider @Inject constructor(
    settings: DynamicSettingsProvider,
    testOverrides: TestOverrideSettings,
) : SettingsProvider by settings {
    override val httpApiSettings = object : HttpApiSettings by settings.httpApiSettings {
        override val projectKey: String
            get() = testOverrides.projectKeyProvider.getValue()
        override val useMarUpload: Boolean
            get() = testOverrides.useMarUpload.getValue()
        override val batchMarUploads: Boolean
            get() = false
    }

    // TODO: review this, the backend will override settings through dynamic settings update
    //  and lower the log level from TEST to whatever is the default (usually verbose) and
    //  it makes some tests fail
    override val minLogcatLevel = LogLevel.TEST

    // Backend might return this as disabled but e2e tests require it
    override val dropBoxSettings = object : DropBoxSettings by settings.dropBoxSettings {
        override val dataSourceEnabled = true
    }

    // Include bort-test tags for logcat collector
    override val logcatSettings = object : LogcatSettings by settings.logcatSettings {
        override val filterSpecs: List<LogcatFilterSpec> =
            listOf(
                LogcatFilterSpec("*", LogcatPriority.WARN),
                LogcatFilterSpec("bort", LogcatPriority.VERBOSE),
                LogcatFilterSpec("bort-test", LogcatPriority.VERBOSE),
            )
    }

    // Include data scrubbing rules when testing
    override val dataScrubbingSettings = object : DataScrubbingSettings by settings.dataScrubbingSettings {
        override val rules: List<DataScrubbingRule> = listOf(
            EmailScrubbingRule,
            CredentialScrubbingRule,
        )
    }
}
