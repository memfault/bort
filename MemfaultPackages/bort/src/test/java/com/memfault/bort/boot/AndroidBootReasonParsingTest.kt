package com.memfault.bort.boot

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

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

    @ParameterizedTest
    @EnumSource
    fun parsing(testCase: TestCase) {
        assertThat(AndroidBootReason.parse(testCase.input)).isEqualTo(testCase.output)
    }
}
