package com.memfault.bort.selfTesting

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.Logger

fun Boolean.toTestResult(): String =
    when (this) {
        true -> "success"
        false -> "failed"
    }

class SelfTester(
    val reporterServiceConnector: ReporterServiceConnector,
    val settingsProvider: SettingsProvider
) {
    interface Case {
        suspend fun test(): Boolean
    }

    suspend fun run(): Boolean =
        listOf(
            SelfTestReporterServiceTimeouts(reporterServiceConnector),
            SelfTestDumpster(settingsProvider.deviceInfoSettings),
            SelfTestBatteryStats(reporterServiceConnector, settingsProvider.batteryStatsSettings.commandTimeout),
            SelfTestLogcatFilterSpecs(reporterServiceConnector, settingsProvider.logcatSettings.commandTimeout),
            SelfTestLogcatFormat(reporterServiceConnector, settingsProvider.logcatSettings.commandTimeout),
            SelfTestLogcatCommandSerialization(),
            SelfTestPackageManager(reporterServiceConnector, settingsProvider.packageManagerSettings.commandTimeout),
            SelfTestReporterServiceConnect(reporterServiceConnector),
        ).map { case ->
            try {
                Logger.test("Running $case...")
                case.test()
            } catch (e: Exception) {
                Logger.e("Test $case raised an exception", e)
                false
            }.also { result ->
                Logger.test("Done $case: ${result.toTestResult()}")
            }
        }.all({ it })
}

class SelfTestWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
    val reporterServiceConnector: ReporterServiceConnector,
    val settingsProvider: SettingsProvider
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val tester = SelfTester(
            reporterServiceConnector = reporterServiceConnector,
            settingsProvider = settingsProvider
        )
        Logger.test("Bort self test: ${tester.run().toTestResult()}")
        return Result.success()
    }
}
