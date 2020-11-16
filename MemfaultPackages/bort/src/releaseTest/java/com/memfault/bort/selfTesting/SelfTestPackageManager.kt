package com.memfault.bort.selfTesting

import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toErrorIf
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.shared.APPLICATION_ID_MEMFAULT_USAGE_REPORTER
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PackageManagerCommand
import com.memfault.bort.shared.result.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SelfTestPackageManager(
    val reporterServiceConnector: ReporterServiceConnector
) : SelfTester.Case {
    override suspend fun test(): Boolean {
        data class PackageManagerTestCase(val cmd: PackageManagerCommand, val expectation: String)

        val testCases = listOf(
            PackageManagerTestCase(PackageManagerCommand(help = true), "Package manager dump options:"),
            PackageManagerTestCase(PackageManagerCommand(checkin = true), "vers,"),
            PackageManagerTestCase(
                PackageManagerCommand(cmdOrAppId = PackageManagerCommand.CMD_PACKAGES),
                "Packages:"
            ),
            PackageManagerTestCase(
                PackageManagerCommand(cmdOrAppId = APPLICATION_ID_MEMFAULT_USAGE_REPORTER),
                "Packages:"
            ),
        )
        return reporterServiceConnector.connect { getClient ->
            testCases.map { testCase ->
                getClient().packageManagerRun(testCase.cmd) { invocation ->
                    invocation.awaitInputStream().map { stream ->
                        withContext(Dispatchers.IO) {
                            stream.bufferedReader().readText()
                        }
                    }.toErrorIf({ !it.contains(testCase.expectation) }) {
                        Exception("\"Package manager text for $testCase:\\n$it\"").also { Logger.e("$it") }
                    }.andThen {
                        invocation.awaitResponse().toErrorIf({ it.exitCode != 0 }) {
                            Exception("Remote error while running package manager! result=$it").also { Logger.e("$it") }
                        }
                    }
                }
            }.all { it.isSuccess }
        }
    }
}
