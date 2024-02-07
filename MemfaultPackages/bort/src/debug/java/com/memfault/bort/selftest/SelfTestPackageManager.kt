package com.memfault.bort.selftest

import com.github.michaelbull.result.map
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.process.ProcessExecutor
import com.memfault.bort.shared.APPLICATION_ID_MEMFAULT_USAGE_REPORTER
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PackageManagerCommand
import javax.inject.Inject

class SelfTestPackageManager @Inject constructor(
    private val temporaryFileFactory: TemporaryFileFactory,
    private val processExecutor: ProcessExecutor,
) : SelfTester.Case {
    override suspend fun test(isBortLite: Boolean): Boolean {
        data class PackageManagerTestCase(
            val cmd: PackageManagerCommand,
            val expectation: String,
        )

        val testCases = mutableListOf(
            PackageManagerTestCase(PackageManagerCommand(help = true), "Package manager dump options:"),
            PackageManagerTestCase(PackageManagerCommand(checkin = true), "vers,"),
            PackageManagerTestCase(
                PackageManagerCommand(cmdOrAppId = PackageManagerCommand.CMD_PACKAGES),
                "Packages:",
            ),
        )
        if (!isBortLite) {
            testCases.add(
                PackageManagerTestCase(
                    PackageManagerCommand(cmdOrAppId = APPLICATION_ID_MEMFAULT_USAGE_REPORTER),
                    "Packages:",
                ),
            )
        }
        return testCases.map { testCase ->
            temporaryFileFactory.createTemporaryFile("batterystats", suffix = ".txt").useFile { file, _ ->
                file.outputStream().use { outputStream ->
                    processExecutor.execute(testCase.cmd.toList()) {
                        it.copyTo(outputStream)
                    }
                    val output = file.readText()
                    output.contains(testCase.expectation).apply {
                        if (!this) Logger.d("failed: expectation = '${testCase.expectation}' output = '$output'")
                    }
                }
            }
        }.all { it }
    }
}
