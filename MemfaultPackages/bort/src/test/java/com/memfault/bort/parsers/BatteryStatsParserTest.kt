package com.memfault.bort.parsers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BatteryStatsParserTest {
    @Test
    fun happyPath() {
        assertEquals(
            BatteryStatsReport(next = 1234567890, hasTime = true),
            BatteryStatsParser(
                """
                   9,h,123:TIME:123456
                   NEXT: 1234567890
                """.trimIndent().byteInputStream()
            ).parse()
        )
    }

    @Test
    fun happyPathReset() {
        assertEquals(
            BatteryStatsReport(next = 1234567890, hasTime = true),
            BatteryStatsParser(
                """
                   9,h,123:RESET:TIME:123456
                   NEXT: 1234567890
                """.trimIndent().byteInputStream()
            ).parse()
        )
    }

    @Test
    fun happyPathNoTime() {
        assertEquals(
            BatteryStatsReport(next = 1234567890, hasTime = false),
            BatteryStatsParser(
                "NEXT: 1234567890".byteInputStream()
            ).parse()
        )
    }

    @Test
    fun noNext() {
        assertEquals(
            BatteryStatsReport(next = null, hasTime = false),
            BatteryStatsParser(
                "".byteInputStream()
            ).parse()
        )
    }

    @Test
    fun badValue() {
        assertEquals(
            BatteryStatsReport(next = null, hasTime = false),
            BatteryStatsParser(
                "NEXT: NEXT: ".byteInputStream()
            ).parse()
        )
    }
}
