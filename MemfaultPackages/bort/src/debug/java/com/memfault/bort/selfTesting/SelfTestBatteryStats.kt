package com.memfault.bort.selfTesting

import android.os.Build
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toErrorIf
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.shared.BatteryStatsCommand
import com.memfault.bort.shared.BatteryStatsOption
import com.memfault.bort.shared.BatteryStatsOptionEnablement
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.result.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration

class SelfTestBatteryStats(
    val reporterServiceConnector: ReporterServiceConnector,
    val timeout: Duration,
) : SelfTester.Case {
    override suspend fun test(): Boolean {
        data class BatteryStatsTestCase(val cmd: BatteryStatsCommand, val expectation: String)
        val android9OrUp = Build.VERSION.SDK_INT >= 28
        val testCases = listOf(
            BatteryStatsTestCase(BatteryStatsCommand(help = true), "batterystats"),
            BatteryStatsTestCase(
                BatteryStatsCommand(c = true, historyStart = 0),
                // MFLT-2307: Should work, but fails in Android 8 E2E testing:
                if (android9OrUp) "NEXT" else ",",
            ),
            BatteryStatsTestCase(
                BatteryStatsCommand(proto = true),
                if (android9OrUp) "com.android" else "Unknown option: --proto",
            ),
            BatteryStatsTestCase(
                BatteryStatsCommand(cpu = true),
                if (android9OrUp) "Per UID CPU" else "Unknown option: --cpu",
            ),
            BatteryStatsTestCase(
                BatteryStatsCommand(
                    optionEnablement = BatteryStatsOptionEnablement(true, BatteryStatsOption.FULL_HISTORY),
                ),
                "Enabled: full-history",
            ),
            BatteryStatsTestCase(
                BatteryStatsCommand(
                    optionEnablement = BatteryStatsOptionEnablement(false, BatteryStatsOption.FULL_HISTORY),
                ),
                "Disabled: full-history",
            ),
        ).toList()
        return reporterServiceConnector.connect { getClient ->
            testCases.map { testCase ->
                getClient().batteryStatsRun(testCase.cmd, timeout) { invocation ->
                    invocation.awaitInputStream().map { stream ->
                        stream.use {
                            withContext(Dispatchers.IO) {
                                stream.bufferedReader().readText()
                            }
                        }
                    }.toErrorIf({ !it.contains(testCase.expectation) }) {
                        Exception("\"Battery history text for $testCase:\\n$it\"").also { Logger.e("$it") }
                    }.andThen {
                        invocation.awaitResponse().toErrorIf({ it.exitCode != 0 }) {
                            Exception("Remote error while running battery stats! result=$it").also { Logger.e("$it") }
                        }
                    }
                }
            }.all { it.isSuccess }
        }
    }
}
