package com.memfault.bort.boot

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class AndroidBootReasonParsingTest {

    enum class TestCase(
        val input: String?,
        val output: AndroidBootReason,
    ) {
        UNKNOWN(null, AndroidBootReason("reboot", "bort_unknown")),
        EMPTY("", AndroidBootReason("")),
        REBOOT("reboot", AndroidBootReason("reboot")),
        REBOOT_REASON("reboot,userrequested", AndroidBootReason("reboot", "userrequested")),
        THREE_REASONS(
            "shutdown,battery,thermal",
            AndroidBootReason(
                "shutdown",
                "battery",
                listOf("thermal"),
            ),
        ),
        FOUR_REASONS(
            "shutdown,battery,thermal,50C",
            AndroidBootReason(
                "shutdown",
                "battery",
                listOf("thermal", "50C"),
            ),
        ),
    }

    @Test
    fun parsing(@TestParameter testCase: TestCase) {
        assertThat(AndroidBootReason.parse(testCase.input)).isEqualTo(testCase.output)
    }
}
