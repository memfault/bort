package com.memfault.bort

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.memfault.bort.shared.BatteryStatsCommand
import com.memfault.bort.shared.BatteryStatsOption
import com.memfault.bort.shared.BatteryStatsOptionEnablement
import com.memfault.bort.shared.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Exception

fun Boolean.toTestResult(): String =
    when (this) {
        true -> "success"
        false -> "failed"
    }


class SelfTester(
    val reporterServiceConnector: ReporterServiceConnector,
    val settingsProvider: SettingsProvider
) {
    private suspend fun testDumpster(): Boolean =
        DumpsterClient().getprop()?.containsKey(
            settingsProvider.androidSerialNumberKey()
        ) ?: false

    private suspend fun testBatteryStats(): Boolean {
        data class BatteryStatsTestCase(val cmd: BatteryStatsCommand, val expectation: String)
        val android9OrUp = Build.VERSION.SDK_INT >= 28
        val testCases = listOf(
            BatteryStatsTestCase(BatteryStatsCommand(help = true), "batterystats"),
            BatteryStatsTestCase(BatteryStatsCommand(c = true, historyStart = 0),
                // MFLT-2307: Should work, but fails in Android 8 E2E testing:
                if (android9OrUp) "NEXT" else ","),
            BatteryStatsTestCase(BatteryStatsCommand(proto = true),
                if (android9OrUp) "com.android" else "Unknown option: --proto"),
            BatteryStatsTestCase(BatteryStatsCommand(cpu = true),
                if (android9OrUp) "Per UID CPU" else "Unknown option: --cpu"),
            BatteryStatsTestCase(BatteryStatsCommand(
                optionEnablement = BatteryStatsOptionEnablement(true, BatteryStatsOption.FULL_HISTORY)
            ), "Enabled: full-history"),
            BatteryStatsTestCase(BatteryStatsCommand(
                optionEnablement = BatteryStatsOptionEnablement(false, BatteryStatsOption.FULL_HISTORY)
            ), "Disabled: full-history")
        ).toList()
        return reporterServiceConnector.connect { getClient ->
            testCases.map { testCase ->
                getClient().batteryStatsRun(testCase.cmd) { stream ->
                    stream ?: return@batteryStatsRun false.also {
                        Logger.e("Battery history stream was null for ${testCase}")
                    }
                    withContext(Dispatchers.IO) {
                        val history = stream.bufferedReader().readText()
                        history.contains(testCase.expectation).also { success ->
                            if (!success) {
                                Logger.d("Battery history text for ${testCase}:\n$history")
                            }
                        }
                    }
                }
            }.all { it }
        }
    }

    suspend fun run(): Boolean =
        listOf(
            this::testBatteryStats,
            this::testDumpster
        ).map { function ->
            try {
                Logger.test("Running ${function.name}...")
                function()
            } catch (e: Exception) {
                Logger.e("Test ${function.name} raised an exception", e)
                false
            }.also { result ->
                Logger.test("Done ${function.name}: ${result.toTestResult()}")
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
