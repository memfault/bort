package com.memfault.bort.shared

import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Test

class BatteryStatsCommandTest {
    @Test
    fun c() {
        assertThat(
            BatteryStatsCommand(c = true).toList(),
        ).containsExactly("dumpsys", "batterystats", "-c")
    }

    @Test
    fun checkin() {
        assertThat(
            BatteryStatsCommand(checkin = true).toList(),
        ).containsExactly("dumpsys", "batterystats", "--checkin")
    }

    @Test
    fun proto() {
        assertThat(
            BatteryStatsCommand(proto = true).toList(),
        ).containsExactly("dumpsys", "batterystats", "--proto")
    }

    @Test
    fun history() {
        assertThat(
            BatteryStatsCommand(history = true).toList(),
        ).containsExactly("dumpsys", "batterystats", "--history")
    }

    @Test
    fun historyStart() {
        assertThat(
            BatteryStatsCommand(historyStart = 1234).toList(),
        ).containsExactly("dumpsys", "batterystats", "--history-start", "1234")
    }

    @Test
    fun charged() {
        assertThat(
            BatteryStatsCommand(charged = true).toList(),
        ).containsExactly("dumpsys", "batterystats", "--charged")
    }

    @Test
    fun daily() {
        assertThat(
            BatteryStatsCommand(daily = true).toList(),
        ).containsExactly("dumpsys", "batterystats", "--daily")
    }

    @Test
    fun reset() {
        assertThat(
            BatteryStatsCommand(reset = true).toList(),
        ).containsExactly("dumpsys", "batterystats", "--reset")
    }

    @Test
    fun write() {
        assertThat(
            BatteryStatsCommand(write = true).toList(),
        ).containsExactly("dumpsys", "batterystats", "--write")
    }

    @Test
    fun newDaily() {
        assertThat(
            BatteryStatsCommand(newDaily = true).toList(),
        ).containsExactly("dumpsys", "batterystats", "--new-daily")
    }

    @Test
    fun readDaily() {
        assertThat(
            BatteryStatsCommand(readDaily = true).toList(),
        ).containsExactly("dumpsys", "batterystats", "--read-daily")
    }

    @Test
    fun settings() {
        assertThat(
            BatteryStatsCommand(settings = true).toList(),
        ).containsExactly("dumpsys", "batterystats", "--settings")
    }

    @Test
    fun cpu() {
        assertThat(
            BatteryStatsCommand(cpu = true).toList(),
        ).containsExactly("dumpsys", "batterystats", "--cpu")
    }

    @Test
    fun help() {
        assertThat(
            BatteryStatsCommand(help = true).toList(),
        ).containsExactly("dumpsys", "batterystats", "-h")
    }

    @Test
    fun optionFullHistory() {
        val option = BatteryStatsOption.FULL_HISTORY
        assertThat(
            BatteryStatsCommand(
                optionEnablement = BatteryStatsOptionEnablement(true, option),
            ).toList(),
        ).containsExactly("dumpsys", "batterystats", "enable", "full-history")
        assertThat(
            BatteryStatsCommand(
                optionEnablement = BatteryStatsOptionEnablement(false, option),
            ).toList(),
        ).containsExactly("dumpsys", "batterystats", "disable", "full-history")
    }

    @Test
    fun optionNoAutoReset() {
        val option = BatteryStatsOption.NO_AUTO_RESET
        assertThat(
            BatteryStatsCommand(
                optionEnablement = BatteryStatsOptionEnablement(true, option),
            ).toList(),
        ).containsExactly("dumpsys", "batterystats", "enable", "no-auto-reset")
        assertThat(
            BatteryStatsCommand(
                optionEnablement = BatteryStatsOptionEnablement(false, option),
            ).toList(),
        ).containsExactly("dumpsys", "batterystats", "disable", "no-auto-reset")
    }

    @Test
    fun optionPretendScreenOff() {
        val option = BatteryStatsOption.PRETEND_SCREEN_OFF
        assertThat(
            BatteryStatsCommand(
                optionEnablement = BatteryStatsOptionEnablement(true, option),
            ).toList(),
        ).containsExactly("dumpsys", "batterystats", "enable", "pretend-screen-off")
        assertThat(
            BatteryStatsCommand(
                optionEnablement = BatteryStatsOptionEnablement(false, option),
            ).toList(),
        ).containsExactly("dumpsys", "batterystats", "disable", "pretend-screen-off")
    }
}
