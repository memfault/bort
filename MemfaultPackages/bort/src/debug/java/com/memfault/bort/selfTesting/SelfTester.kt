package com.memfault.bort.selfTesting

import android.app.Application
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.memfault.bort.DumpsterClient
import com.memfault.bort.IndividualWorkerFactory
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.runAndTrackExceptions
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.components.SingletonComponent

fun Boolean.toTestResult(): String =
    when (this) {
        true -> "success"
        false -> "failed"
    }

class SelfTester(
    val reporterServiceConnector: ReporterServiceConnector,
    val settingsProvider: SettingsProvider,
    private val dumpsterClient: DumpsterClient,
) {
    interface Case {
        suspend fun test(): Boolean
    }

    suspend fun run(): Boolean =
        listOf(
            SelfTestReporterServiceTimeouts(reporterServiceConnector),
            SelfTestDumpster(settingsProvider.deviceInfoSettings, dumpsterClient),
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

@AssistedFactory
@ContributesMultibinding(SingletonComponent::class)
interface SelfTestWorkerFactory : IndividualWorkerFactory {
    override fun create(workerParameters: WorkerParameters): SelfTestWorker
    override fun type() = SelfTestWorker::class
}

class SelfTestWorker @AssistedInject constructor(
    appContext: Application,
    @Assisted workerParameters: WorkerParameters,
    val reporterServiceConnector: ReporterServiceConnector,
    val settingsProvider: SettingsProvider,
    private val dumpsterClient: DumpsterClient,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result = runAndTrackExceptions(jobName = "SelfTestWorker") {
        val tester = SelfTester(
            reporterServiceConnector = reporterServiceConnector,
            settingsProvider = settingsProvider,
            dumpsterClient = dumpsterClient,
        )
        Logger.test("Bort self test: ${tester.run().toTestResult()}")
        Result.success()
    }
}
