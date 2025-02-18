package com.memfault.bort.parsers

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class BatteryStatsParserTest {
    @Test
    fun happyPath() {
        assertThat(
            BatteryStatsParser(
                """
                   9,h,123:TIME:123456
                   NEXT: 1234567890
                """.trimIndent().byteInputStream(),
            ).parse(),
        ).isEqualTo(
            BatteryStatsReport(next = 1234567890, hasTime = true),
        )
    }

    @Test
    fun happyPathReset() {
        assertThat(
            BatteryStatsParser(
                """
                   9,h,123:RESET:TIME:123456
                   NEXT: 1234567890
                """.trimIndent().byteInputStream(),
            ).parse(),
        ).isEqualTo(
            BatteryStatsReport(next = 1234567890, hasTime = true),
        )
    }

    @Test
    fun happyPathNoTime() {
        assertThat(
            BatteryStatsParser(
                "NEXT: 1234567890".byteInputStream(),
            ).parse(),
        ).isEqualTo(
            BatteryStatsReport(next = 1234567890, hasTime = false),
        )
    }

    @Test
    fun noNext() {
        assertThat(
            BatteryStatsParser(
                "".byteInputStream(),
            ).parse(),
        ).isEqualTo(
            BatteryStatsReport(next = null, hasTime = false),
        )
    }

    @Test
    fun badValue() {
        assertThat(
            BatteryStatsParser(
                "NEXT: NEXT: ".byteInputStream(),
            ).parse(),
        ).isEqualTo(
            BatteryStatsReport(next = null, hasTime = false),
        )
    }
}
