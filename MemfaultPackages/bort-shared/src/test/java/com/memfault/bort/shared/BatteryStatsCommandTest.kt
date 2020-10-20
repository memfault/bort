package com.memfault.bort.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryStatsCommandTest {
    @Test
    fun c() {
        assertEquals(listOf("dumpsys", "batterystats", "-c"),
            BatteryStatsCommand(c = true).toList())
    }

    @Test
    fun checkin() {
        assertEquals(listOf("dumpsys", "batterystats", "--checkin"),
            BatteryStatsCommand(checkin = true).toList())
    }

    @Test
    fun proto() {
        assertEquals(listOf("dumpsys", "batterystats", "--proto"),
            BatteryStatsCommand(proto = true).toList())
    }

    @Test
    fun history() {
        assertEquals(listOf("dumpsys", "batterystats", "--history"),
            BatteryStatsCommand(history = true).toList())
    }

    @Test
    fun historyStart() {
        assertEquals(listOf("dumpsys", "batterystats", "--history-start", "1234"),
            BatteryStatsCommand(historyStart = 1234).toList())
    }

    @Test
    fun charged() {
        assertEquals(listOf("dumpsys", "batterystats", "--charged"),
            BatteryStatsCommand(charged = true).toList())
    }

    @Test
    fun daily() {
        assertEquals(listOf("dumpsys", "batterystats", "--daily"),
            BatteryStatsCommand(daily = true).toList())
    }

    @Test
    fun reset() {
        assertEquals(listOf("dumpsys", "batterystats", "--reset"),
            BatteryStatsCommand(reset = true).toList())
    }

    @Test
    fun write() {
        assertEquals(listOf("dumpsys", "batterystats", "--write"),
            BatteryStatsCommand(write = true).toList())
    }

    @Test
    fun newDaily() {
        assertEquals(listOf("dumpsys", "batterystats", "--new-daily"),
            BatteryStatsCommand(newDaily = true).toList())
    }

    @Test
    fun readDaily() {
        assertEquals(listOf("dumpsys", "batterystats", "--read-daily"),
            BatteryStatsCommand(readDaily = true).toList())
    }

    @Test
    fun settings() {
        assertEquals(listOf("dumpsys", "batterystats", "--settings"),
            BatteryStatsCommand(settings = true).toList())
    }

    @Test
    fun cpu() {
        assertEquals(listOf("dumpsys", "batterystats", "--cpu"),
            BatteryStatsCommand(cpu = true).toList())
    }

    @Test
    fun help() {
        assertEquals(listOf("dumpsys", "batterystats", "-h"),
            BatteryStatsCommand(help = true).toList())
    }

    @Test
    fun optionFullHistory() {
        val option = BatteryStatsOption.FULL_HISTORY
        assertEquals(listOf("dumpsys", "batterystats", "enable", "full-history"), BatteryStatsCommand(
            optionEnablement = BatteryStatsOptionEnablement(true, option)).toList())
        assertEquals(listOf("dumpsys", "batterystats", "disable", "full-history"), BatteryStatsCommand(
            optionEnablement = BatteryStatsOptionEnablement(false, option)).toList())
    }

    @Test
    fun optionNoAutoReset() {
        val option = BatteryStatsOption.NO_AUTO_RESET
        assertEquals(listOf("dumpsys", "batterystats", "enable", "no-auto-reset"), BatteryStatsCommand(
            optionEnablement = BatteryStatsOptionEnablement(true, option)).toList())
        assertEquals(listOf("dumpsys", "batterystats", "disable", "no-auto-reset"), BatteryStatsCommand(
            optionEnablement = BatteryStatsOptionEnablement(false, option)).toList())
    }

    @Test
    fun optionPretendScreenOff() {
        val option = BatteryStatsOption.PRETEND_SCREEN_OFF
        assertEquals(listOf("dumpsys", "batterystats", "enable", "pretend-screen-off"), BatteryStatsCommand(
            optionEnablement = BatteryStatsOptionEnablement(true, option)).toList())
        assertEquals(listOf("dumpsys", "batterystats", "disable", "pretend-screen-off"), BatteryStatsCommand(
            optionEnablement = BatteryStatsOptionEnablement(false, option)).toList())
    }
}
