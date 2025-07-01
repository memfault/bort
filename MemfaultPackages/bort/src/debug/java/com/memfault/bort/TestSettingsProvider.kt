package com.memfault.bort

import android.content.SharedPreferences
import com.memfault.bort.settings.DataScrubbingSettings
import com.memfault.bort.settings.DropBoxSettings
import com.memfault.bort.settings.DynamicSettingsProvider
import com.memfault.bort.settings.HttpApiSettings
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.settings.MetricsSettings
import com.memfault.bort.settings.SettingsProvider
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
    /**
     * Whether to use test override settings. This should be set during E2E tests.
     */
    val useTestSettingOverrides = object : PreferenceKeyProvider<Boolean>(
        sharedPreferences = sharedPreferences,
        defaultValue = false,
        preferenceKey = "test-use-setting-overrides",
        // We kill the app right after setting this; use commit so that the write is not lost.
        commit = true,
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
        // Specifically for Bort Lite tests, where the apk is targeting prod:
        override val deviceBaseUrl: String
            get() = "http://app.memfault.test:8000"
        override val filesBaseUrl: String
            get() = "http://app.memfault.test:8000"
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
                    // e2e-helper-test is the tag used to generate random bytes
                    // for the continuous logcat bytes-threshold test
                    LogcatFilterSpec("e2e-helper-test", LogcatPriority.VERBOSE),
                    // answer(42) tag for the events binary buffer
                    LogcatFilterSpec("answer", LogcatPriority.INFO),
                )
            } else {
                settings.logcatSettings.filterSpecs
            }
    }

    // Include data scrubbing rules when testing
    override val dataScrubbingSettings = object : DataScrubbingSettings by settings.dataScrubbingSettings {
        override val rules: List<DataScrubbingRule>
            get() = if (override()) {
                listOf(
                    EmailScrubbingRule,
                    CredentialScrubbingRule,
                )
            } else {
                settings.dataScrubbingSettings.rules
            }
    }

    override val metricsSettings = object : MetricsSettings by settings.metricsSettings {
        // Low threshold for cpu reporting because tests are generally idle
        // We could set this via sdk setting but the settings are not in the backend yet
        override val cpuProcessReportingThreshold: Int
            get() = 0

        // During tests create metrics for all processes so that we can assert whether
        // they reach the backend
        override val alwaysCreateCpuProcessMetrics: Boolean
            get() = true
    }
}
