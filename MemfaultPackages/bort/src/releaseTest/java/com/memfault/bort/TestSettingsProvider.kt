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

    /**
     * Whether to use test override settings. This should be set during E2E tests.
     */
    val useTestSettingOverrides = object : PreferenceKeyProvider<Boolean>(
        sharedPreferences = sharedPreferences,
        defaultValue = false,
        preferenceKey = "test-use-setting-overrides"
    ) {}
}

@ContributesBinding(SingletonComponent::class, replaces = [DynamicSettingsProvider::class])
class TestSettingsProvider @Inject constructor(
    private val settings: DynamicSettingsProvider,
    private val testOverrides: TestOverrideSettings,
) : SettingsProvider by settings {
    // Kotlin does not allow delegation based on mutable state, so every call must check whether to override settings
    // with test overrides or not. Note: every override below must use get() and check this value.
    fun override() = testOverrides.useTestSettingOverrides.getValue()

    override val httpApiSettings = object : HttpApiSettings by settings.httpApiSettings {
        override val projectKey: String
            get() = if (override()) testOverrides.projectKeyProvider.getValue() else settings.httpApiSettings.projectKey
        override val useMarUpload: Boolean
            get() = if (override()) testOverrides.useMarUpload.getValue() else settings.httpApiSettings.useMarUpload
        override val batchMarUploads: Boolean
            get() = if (override()) false else settings.httpApiSettings.batchMarUploads
    }

    // TODO: review this, the backend will override settings through dynamic settings update
    //  and lower the log level from TEST to whatever is the default (usually verbose) and
    //  it makes some tests fail
    override val minLogcatLevel get() = if (override()) LogLevel.TEST else settings.minLogcatLevel

    // Backend might return this as disabled but e2e tests require it
    override val dropBoxSettings = object : DropBoxSettings by settings.dropBoxSettings {
        override val dataSourceEnabled get() = if (override()) true else settings.dropBoxSettings.dataSourceEnabled
    }

    // Include bort-test tags for logcat collector
    override val logcatSettings = object : LogcatSettings by settings.logcatSettings {
        override val filterSpecs: List<LogcatFilterSpec>
            get() = if (override()) {
                listOf(
                    LogcatFilterSpec("*", LogcatPriority.WARN),
                    LogcatFilterSpec("bort", LogcatPriority.VERBOSE),
                    LogcatFilterSpec("bort-test", LogcatPriority.VERBOSE),
                )
            } else settings.logcatSettings.filterSpecs

        override val continuousLogDumpThresholdBytes: Int
            get() = if (override()) 16 * 1024
            else settings.logcatSettings.continuousLogDumpThresholdBytes
    }

    // Include data scrubbing rules when testing
    override val dataScrubbingSettings = object : DataScrubbingSettings by settings.dataScrubbingSettings {
        override val rules: List<DataScrubbingRule>
            get() = if (override()) listOf(
                EmailScrubbingRule,
                CredentialScrubbingRule,
            ) else settings.dataScrubbingSettings.rules
    }
}
