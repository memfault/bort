package com.memfault.bort.selftest

import android.os.Build
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.metrics.RunBatteryStats
import com.memfault.bort.shared.BatteryStatsCommand
import com.memfault.bort.shared.BatteryStatsOption
import com.memfault.bort.shared.BatteryStatsOptionEnablement
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

class SelfTestBatteryStats @Inject constructor(
    val runBatteryStats: RunBatteryStats,
    val temporaryFileFactory: TemporaryFileFactory,
) : SelfTester.Case {
    override suspend fun test(isBortLite: Boolean): Boolean {
        data class BatteryStatsTestCase(
            val cmd: BatteryStatsCommand,
            val expectation: String,
        )

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
        return testCases.map { testCase ->
            temporaryFileFactory.createTemporaryFile("batterystats", suffix = ".txt").useFile { file, _ ->
                file.outputStream().use { outputStream ->
                    runBatteryStats.runBatteryStats(outputStream, testCase.cmd, 1.minutes)
                    val output = file.readText()
                    output.contains(testCase.expectation)
                }
            }
        }.all { it }
    }
}
