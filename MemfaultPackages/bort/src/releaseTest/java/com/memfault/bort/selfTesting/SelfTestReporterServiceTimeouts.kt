package com.memfault.bort.selfTesting

import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.toErrorIf
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.RunCommandResponse
import com.memfault.bort.shared.result.isSuccess
import kotlin.time.Duration.Companion.seconds

class SelfTestReporterServiceTimeouts(
    val reporterServiceConnector: ReporterServiceConnector
) : SelfTester.Case {
    override suspend fun test(): Boolean {
        data class TimeoutTestCase(val delaySeconds: Int, val expectedResponse: RunCommandResponse)

        val testCases = listOf(
            TimeoutTestCase(
                delaySeconds = 0,
                expectedResponse = RunCommandResponse(exitCode = 0, didTimeout = false)
            ),
            TimeoutTestCase(
                delaySeconds = 2,
                expectedResponse = RunCommandResponse(exitCode = 143, didTimeout = true)
            ),
        )
        return reporterServiceConnector.connect { getClient ->
            testCases.map { testCase ->
                getClient().sleep(delaySeconds = testCase.delaySeconds, timeout = 1.seconds) { invocation ->
                    invocation.awaitInputStream().onFailure {
                        Logger.d("Sleep stream was null")
                    }.map {
                        it.bufferedReader().readText()
                    }.andThen {
                        invocation.awaitResponse().toErrorIf({ testCase.expectedResponse != it }) {
                            Exception("Expected error not observed: ${testCase.expectedResponse} vs $it").also {
                                Logger.e("$it")
                            }
                        }
                    }
                }
            }.all { it.isSuccess }
        }
    }
}
