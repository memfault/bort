package com.memfault.bort.shared

import android.os.Bundle

/**
 * Data class to hold all possible flags and options to the `dumpsys batterystats` command line
 * interface. For security reasons, we do not want to pass arbitrary (string) arguments.
 *
 * Battery stats (batterystats) dump options:
 *   [--checkin] [--proto] [--history] [--history-start] [--charged] [-c]
 *   [--daily] [--reset] [--write] [--new-daily] [--read-daily] [-h] [<package.name>]
 *   --checkin: generate output for a checkin report; will write (and clear) the
 *              last old completed stats when they had been reset.
 *   -c: write the current stats in checkin format.
 *   --proto: write the current aggregate stats (without history) in proto format.
 *   --history: show only history data.
 *   --history-start <num>: show only history data starting at given time offset.
 *   --charged: only output data since last charged.
 *   --daily: only output full daily data.
 *   --reset: reset the stats, clearing all current data.
 *   --write: force write current collected stats to disk.
 *   --new-daily: immediately create and write new daily stats record.
 *   --read-daily: read-load last written daily stats.
 *   --settings: dump the settings key/values related to batterystats
 *   --cpu: dump cpu stats for debugging purpose
 *   <package.name>: optional name of package to filter output by.
 *   -h: print this help text.
 * Battery stats (batterystats) commands:
 *   enable|disable <option>
 *     Enable or disable a running option.  Option state is not saved across boots.
 *     Options are:
 *       full-history: include additional detailed events in battery history:
 *           wake_lock_in, alarms and proc events
 *       no-auto-reset: don't automatically reset stats when unplugged
 *       pretend-screen-off: pretend the screen is off, even if screen state changes
 */
data class BatteryStatsCommand(
    val checkin: Boolean = false,
    val c: Boolean = false,
    val proto: Boolean = false,
    val history: Boolean = false,
    val historyStart: Long? = null,
    val charged: Boolean = false,
    val daily: Boolean = false,
    val reset: Boolean = false,
    val write: Boolean = false,
    val newDaily: Boolean = false,
    val readDaily: Boolean = false,
    val settings: Boolean = false,
    val cpu: Boolean = false,
    val optionEnablement: BatteryStatsOptionEnablement? = null,
    val help: Boolean = false
) : Command {
    private val booleanFlagMap
        get() = mapOf(
            CHECKIN to checkin,
            C to c,
            PROTO to proto,
            HISTORY to history,
            CHARGED to charged,
            DAILY to daily,
            RESET to reset,
            WRITE to write,
            NEW_DAILY to newDaily,
            READ_DAILY to readDaily,
            SETTINGS to settings,
            CPU to cpu,
            HELP to help
        )

    override fun toList(): List<String> =
        listOf("dumpsys", "batterystats") + if (optionEnablement != null) {
            listOf(
                if (optionEnablement.enabled) "enable" else "disable",
                optionEnablement.option.cliValue
            )
        } else {
            booleanFlagMap
                .filter { (_, value) -> value }
                .map { (flag, _) -> flag }
                .toMutableList().also { flags ->
                    historyStart?.let {
                        flags.add(HISTORY_START)
                        flags.add("$historyStart")
                    }
                }
        }

    override fun toBundle(): Bundle =
        Bundle().apply {
            for ((key, value) in booleanFlagMap) {
                if (value) putBoolean(key, value)
            }
            historyStart?.let { putLong(HISTORY_START, historyStart) }
            optionEnablement?.let {
                putBoolean(ENABLED, it.enabled)
                putByte(OPTION, it.option.id)
            }
        }

    companion object {
        fun fromBundle(bundle: Bundle) = with(bundle) {
            BatteryStatsCommand(
                checkin = getBoolean(CHECKIN),
                c = getBoolean(C),
                proto = getBoolean(PROTO),
                history = getBoolean(HISTORY),
                historyStart = getLongOrNull(HISTORY_START),
                charged = getBoolean(CHARGED),
                daily = getBoolean(DAILY),
                reset = getBoolean(RESET),
                write = getBoolean(WRITE),
                newDaily = getBoolean(NEW_DAILY),
                readDaily = getBoolean(READ_DAILY),
                settings = getBoolean(SETTINGS),
                cpu = getBoolean(CPU),
                optionEnablement = getBooleanOrNull(ENABLED)?.let { enabled ->
                    BatteryStatsOption.getById(getByte(OPTION))?.let { option ->
                        BatteryStatsOptionEnablement(enabled, option)
                    }
                },
                help = getBoolean(HELP)
            )
        }
    }
}

data class BatteryStatsOptionEnablement(val enabled: Boolean, val option: BatteryStatsOption)

enum class BatteryStatsOption(val id: Byte, val cliValue: String) {
    FULL_HISTORY(0, "full-history"),
    NO_AUTO_RESET(1, "no-auto-reset"),
    PRETEND_SCREEN_OFF(2, "pretend-screen-off");

    companion object {
        fun getById(id: Byte) = values().firstOrNull { it.id == id }
    }
}

private const val CHECKIN = "--checkin"
private const val C = "-c"
private const val PROTO = "--proto"
private const val HISTORY = "--history"
private const val CHARGED = "--charged"
private const val DAILY = "--daily"
private const val RESET = "--reset"
private const val WRITE = "--write"
private const val NEW_DAILY = "--new-daily"
private const val READ_DAILY = "--read-daily"
private const val SETTINGS = "--settings"
private const val CPU = "--cpu"
private const val HELP = "-h"
private const val HISTORY_START = "--history-start"
private const val ENABLED = "enabled"
private const val OPTION = "option"
