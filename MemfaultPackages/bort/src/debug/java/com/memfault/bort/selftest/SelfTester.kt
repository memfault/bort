package com.memfault.bort.selftest

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.memfault.bort.DumpsterClient
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.diagnostics.BortJobReporter
import com.memfault.bort.receivers.INTENT_EXTRA_BORT_LITE
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.runAndTrackExceptions
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import javax.inject.Inject

fun Boolean.toTestResult(): String =
    when (this) {
        true -> "success"
        false -> "failed"
    }

class SelfTester @Inject constructor(
    private val reporterServiceConnector: ReporterServiceConnector,
    private val settingsProvider: SettingsProvider,
    private val dumpsterClient: DumpsterClient,
    private val selfTestBatteryStats: SelfTestBatteryStats,
    private val selfTestPackageManager: SelfTestPackageManager,
) {
    interface Case {
        suspend fun test(isBortLite: Boolean): Boolean
    }

    suspend fun run(isBortLite: Boolean): Boolean =
        listOf(
            SelfTestReporterServiceTimeouts(reporterServiceConnector),
            SelfTestDumpster(settingsProvider.deviceInfoSettings, dumpsterClient),
            selfTestBatteryStats,
            SelfTestLogcatFilterSpecs(reporterServiceConnector, settingsProvider.logcatSettings.commandTimeout),
            SelfTestLogcatFormat(reporterServiceConnector, settingsProvider.logcatSettings.commandTimeout),
            SelfTestLogcatCommandSerialization(),
            selfTestPackageManager,
            SelfTestReporterServiceConnect(reporterServiceConnector),
        ).map { case ->
            try {
                Logger.test("Running $case...")
                case.test(isBortLite)
            } catch (e: Exception) {
                Logger.e("Test $case raised an exception", e)
                false
            }.also { result ->
                Logger.test("Done $case: ${result.toTestResult()}")
            }
        }.all({ it })
}

@HiltWorker
class SelfTestWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    val reporterServiceConnector: ReporterServiceConnector,
    val settingsProvider: SettingsProvider,
    private val selfTester: SelfTester,
    private val bortJobReporter: BortJobReporter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val isBortLite = inputData.getBoolean(INTENT_EXTRA_BORT_LITE, false)
        return runAndTrackExceptions(jobName = "SelfTestWorker", bortJobReporter) {
            Logger.test("Bort self test: ${selfTester.run(isBortLite).toTestResult()}")
            Result.success()
        }
    }
}
