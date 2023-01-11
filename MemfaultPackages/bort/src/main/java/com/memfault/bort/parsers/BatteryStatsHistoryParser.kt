package com.memfault.bort.parsers

import com.memfault.bort.parsers.Transition.NONE
import com.memfault.bort.parsers.Transition.OFF
import com.memfault.bort.parsers.Transition.ON
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.shared.Logger
import java.io.File
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import kotlinx.coroutines.delay

class BatteryStatsHistoryParser(
    private val file: File,
    private val batteryStatsHistoryMetricLogger: BatteryStatsHistoryMetricLogger,
) {
    private val hsp = mutableMapOf<Int, HspEntry>()
    private var used = false
    private var timeMs: Long? = null
    private var currentTopApp: String? = null
    private var currentForeground: String? = null
    private var reportedErrorMetric = false

    suspend fun parseToCustomMetrics() {
        if (used) throw IllegalStateException("Cannot re-use parser")
        used = true

        file.bufferedReader().useLines {
            it.forEach { line ->
                // For each line we try/catch, and move onto the next line if there is an error parsing.
                try {
                    if (line.startsWith("NEXT:")) return@forEach
                    val entries = line.split(",")
                    val version = entries[I_VERSION].toInt()
                    if (version != VALID_VERSION) return@forEach
                    val type = entries[I_TYPE]
                    val contentEntries = entries.drop(I_CONTENT_START)
                    when (type) {
                        "0" -> parseHeader(contentEntries)
                        "hsp" -> parseHsp(contentEntries)
                        "h" -> parseHistory(contentEntries)
                    }
                    // TODO hack to not crash metric service - needs a bulk insert API to remove this.
                    delay(5)
                } catch (e: ArrayIndexOutOfBoundsException) {
                    Logger.i("parseToCustomMetrics $line", e)
                    reportErrorMetric()
                } catch (e: NumberFormatException) {
                    Logger.i("parseToCustomMetrics $line", e)
                    reportErrorMetric()
                }
            }
        }
    }

    private fun parseHeader(entries: List<String>) = Unit

    data class HspEntry(
        val key: Int,
        val uid: Int,
        val value: String,
    )

    private fun parseHsp(entries: List<String>) {
        val key = entries[0].toInt()
        val uid = entries[1].toInt()
        val value = entries[2].dropWhile { it == '"' }.dropLastWhile { it == '"' }
        hsp[key] = HspEntry(key = key, uid = uid, value = value)
    }

    private fun hspLookup(value: String?): HspEntry? {
        if (value == null) return null
        try {
            val index = value.toInt()
            return hsp.get(index)
        } catch (e: NumberFormatException) {
            Logger.i("hspLookup $value", e)
            reportErrorMetric()
            return null
        }
    }

    private fun parseHistory(entries: List<String>) {
        val command = entries[0].split(":")
        if (command.size > 1) {
            val sinceLast = command[0].toLong()
            timeMs = timeMs?.plus(sinceLast)
            when (command[1]) {
                "TIME" -> timeMs = command[2].toLong()
                "RESET" -> timeMs = command[3].toLong()
                "START" -> timeMs?.let { batteryStatsHistoryMetricLogger.start(it) }
                "SHUTDOWN" -> timeMs?.let { batteryStatsHistoryMetricLogger.shutdown(it) }
            }
        } else {
            val sinceLast = entries[0].toLong()
            timeMs = timeMs?.plus(sinceLast)
            timeMs?.let { parseEvent(it, entries.drop(1)) }
        }
    }

    // Bl=85
    // +r
    // -r
    private fun parseEvent(timestampMs: Long, entries: List<String>) {
        entries.forEach { event ->
            // Separate try/catch for each entry within the line, so that we don't wipe out every entry if we see one
            // parsing error.
            try {
                val transition = when (event[0]) {
                    '+' -> ON
                    '-' -> OFF
                    else -> NONE
                }
                val content = when (transition) {
                    NONE -> event
                    else -> event.drop(1)
                }.split("=")
                val eventType = content[0]
                val value = content.getOrNull(1)
                when (eventType) {
                    "w" -> batteryStatsHistoryMetricLogger.wakelock(
                        timeMs = timestampMs,
                        wakeApp = if (transition.bool) hspLookup(value)?.value else null,
                    )
                    "r" -> batteryStatsHistoryMetricLogger.cpuRunning(timeMs = timestampMs, running = transition.bool)
                    // TODO don't add battery metrics if device doesn't have a battery.
                    "Bl" -> batteryStatsHistoryMetricLogger.batteryLevel(
                        timeMs = timestampMs,
                        level = value?.toLong()!!,
                    )
                    "Bt" -> batteryStatsHistoryMetricLogger.batteryTemp(timeMs = timestampMs, temp = value?.toLong()!!)
                    "Bv" -> batteryStatsHistoryMetricLogger.batteryVoltage(
                        timeMs = timestampMs,
                        voltage = value?.toLong()!!,
                    )
                    "Bcc" -> batteryStatsHistoryMetricLogger.batteryCoulombCharge(
                        timeMs = timestampMs,
                        coulomb = value?.toLong()!!,
                    )
                    "Bh" -> batteryStatsHistoryMetricLogger.batteryHealth(
                        timeMs = timestampMs,
                        health = BatteryHealth.fromString(value!!),
                    )
                    "Bs" -> batteryStatsHistoryMetricLogger.batteryStatus(
                        timeMs = timestampMs,
                        status = BatteryStatus.fromString(value!!),
                    )
                    "Bp" -> batteryStatsHistoryMetricLogger.batteryPlug(
                        timeMs = timestampMs,
                        plug = BatteryPlug.fromString(value!!),
                    )
                    "BP" -> batteryStatsHistoryMetricLogger.batteryPlugged(
                        timeMs = timestampMs,
                        plugged = transition.bool,
                    )
                    "ch" -> batteryStatsHistoryMetricLogger.charging(timeMs = timestampMs, charging = transition.bool)
                    "a" -> batteryStatsHistoryMetricLogger.audio(timeMs = timestampMs, audio = transition.bool)
                    "ca" -> batteryStatsHistoryMetricLogger.camera(timeMs = timestampMs, camera = transition.bool)
                    "v" -> batteryStatsHistoryMetricLogger.video(timeMs = timestampMs, video = transition.bool)
                    "s" -> batteryStatsHistoryMetricLogger.sensor(timeMs = timestampMs, sensor = transition.bool)
                    "g" -> batteryStatsHistoryMetricLogger.gpsOn(timeMs = timestampMs, gpsOn = transition.bool)
                    "Gss" -> batteryStatsHistoryMetricLogger.gpsSignalStrength(
                        timeMs = timestampMs,
                        gpsSignalStrength = GpsSignalStrength.fromString(value!!),
                    )
                    "S" -> batteryStatsHistoryMetricLogger.screenOn(timeMs = timestampMs, screenOn = transition.bool)
                    "Sb" -> batteryStatsHistoryMetricLogger.screenBrightness(
                        timeMs = timestampMs,
                        screenBrightness = ScreenBrightness.fromString(value!!),
                    )
                    // new
                    "W" -> batteryStatsHistoryMetricLogger.wifiOn(timeMs = timestampMs, wifiOn = transition.bool)
                    "Wl" -> batteryStatsHistoryMetricLogger.wifiFullLock(
                        timeMs = timestampMs,
                        wifiFullLock = transition.bool,
                    )
                    "Ws" -> batteryStatsHistoryMetricLogger.wifiScan(timeMs = timestampMs, wifiScan = transition.bool)
                    "Wm" -> batteryStatsHistoryMetricLogger.wifiMulticast(
                        timeMs = timestampMs,
                        wifiMulticast = transition.bool,
                    )
                    "Wr" -> batteryStatsHistoryMetricLogger.wifiRadio(timeMs = timestampMs, wifiRadio = transition.bool)
                    "Ww" -> batteryStatsHistoryMetricLogger.wifiRunning(
                        timeMs = timestampMs,
                        wifiRunning = transition.bool,
                    )
                    "Wss" -> batteryStatsHistoryMetricLogger.wifiSignalStrength(
                        timeMs = timestampMs,
                        wifiSignalStrength = SignalStrength.fromString(value!!),
                    )
                    "Wsp" -> batteryStatsHistoryMetricLogger.wifiSupplicantState(
                        timeMs = timestampMs,
                        wifiSupplicantState = WifiSupplicantState.fromString(value!!),
                    )
                    "ps", "lp" -> batteryStatsHistoryMetricLogger.powerSave(
                        timeMs = timestampMs,
                        powerSave = transition.bool,
                    )
                    "di" -> batteryStatsHistoryMetricLogger.doze(
                        timeMs = timestampMs,
                        doze = DozeState.fromString(value!!),
                    )
                    "Etp" -> run {
                        val lookupName = hspLookup(value)?.value
                        val topApp = if (transition.bool) lookupName else null
                        // A -Etp=123 might be reported after +Etp=456,
                        // so only clear if this is the currently foregrounded app
                        if (!transition.bool && lookupName != currentTopApp) return@run
                        currentTopApp = topApp
                        batteryStatsHistoryMetricLogger.topApp(
                            timeMs = timestampMs,
                            topApp = topApp,
                        )
                    }
                    "Efg" -> run {
                        val lookupName = hspLookup(value)?.value
                        val foreground = if (transition.bool) lookupName else null
                        // A -Efg=123 might be reported after +Efg=456,
                        // so only clear if this is the currently foregrounded app
                        if (!transition.bool && lookupName != currentForeground) return@run
                        currentForeground = foreground
                        batteryStatsHistoryMetricLogger.foreground(
                            timeMs = timestampMs,
                            foreground = foreground,
                        )
                    }
                    // new
                    "Eur" -> batteryStatsHistoryMetricLogger.user(
                        timeMs = timestampMs,
                        user = if (transition.bool) hspLookup(value)?.value else null,
                    )
                    // new
                    "Euf" -> batteryStatsHistoryMetricLogger.userForeground(
                        timeMs = timestampMs,
                        userForeground = if (transition.bool) hspLookup(value)?.value else null,
                    )
                    // new (didn't show app before)
                    "Ejb" -> batteryStatsHistoryMetricLogger.job(
                        timeMs = timestampMs,
                        job = if (transition.bool) hspLookup(value)?.value else null,
                    )
                    "Elw" -> batteryStatsHistoryMetricLogger.longwake(
                        timeMs = timestampMs,
                        longwake = if (transition.bool) hspLookup(value)?.value else null,
                    )
                    // new
                    "Epi" -> hspLookup(value)?.let {
                        batteryStatsHistoryMetricLogger.packageInstall(
                            timeMs = timestampMs,
                            packageInstall = "${it.value}: ${it.uid}",
                        )
                    }
                    // new
                    "Epu" -> hspLookup(value)?.let {
                        batteryStatsHistoryMetricLogger.packageUninstall(
                            timeMs = timestampMs,
                            packageUninstall = "${it.value}: ${it.uid}",
                        )
                    }
                    "Eac" -> batteryStatsHistoryMetricLogger.deviceActive(
                        timeMs = timestampMs,
                        deviceActive = hspLookup(value)?.value,
                    )
                    "bles" -> batteryStatsHistoryMetricLogger.bleScanning(
                        timeMs = timestampMs,
                        bleScanning = transition.bool,
                    )
                    // TODO don't add phone metrics if device doesn't have cell connectivity
                    "Pr" -> batteryStatsHistoryMetricLogger.phoneRadio(
                        timeMs = timestampMs,
                        phoneRadio = transition.bool,
                    )
                    "Pcn" -> batteryStatsHistoryMetricLogger.phoneConnection(
                        timeMs = timestampMs,
                        phoneConnection = PhoneConnection.fromString(value!!),
                    )
                    "Pcl" -> batteryStatsHistoryMetricLogger.phoneInCall(
                        timeMs = timestampMs,
                        phoneInCall = transition.bool,
                    )
                    "Psc" -> batteryStatsHistoryMetricLogger.phoneScanning(
                        timeMs = timestampMs,
                        phoneScanning = transition.bool,
                    )
                    "Pss" -> batteryStatsHistoryMetricLogger.phoneSignalStrength(
                        timeMs = timestampMs,
                        phoneSignalStrength = SignalStrength.fromString(value!!),
                    )
                    "Pst" -> {
                        batteryStatsHistoryMetricLogger.phoneState(
                            timeMs = timestampMs,
                            phoneState = transition.bool,
                        )
                        if (!transition.bool) {
                            batteryStatsHistoryMetricLogger.phoneSignalStrength(
                                timeMs = timestampMs,
                                phoneSignalStrength = null,
                            )
                        }
                    }
                    "Eal" -> batteryStatsHistoryMetricLogger.alarm(
                        timeMs = timestampMs,
                        alarm = hspLookup(value)?.value,
                    )
                }
            } catch (e: ArrayIndexOutOfBoundsException) {
                Logger.i("parseEvent $event", e)
                reportErrorMetric()
            } catch (e: NumberFormatException) {
                Logger.i("parseEvent $event", e)
                reportErrorMetric()
            } catch (e: NullPointerException) {
                Logger.i("parseEvent $event", e)
                reportErrorMetric()
            }
        }
    }

    private fun reportErrorMetric() {
        if (reportedErrorMetric) return
        reportedErrorMetric = true
        BATTERYSTATS_PARSE_ERROR_METRIC.increment()
    }

    companion object {
        private const val VALID_VERSION = 9
        private const val I_VERSION = 0
        private const val I_TYPE = 1
        private const val I_CONTENT_START = 2

        private val BATTERYSTATS_PARSE_ERROR_METRIC =
            Reporting.report().counter(name = "batterystats_parse_error", internal = true)
    }
}

private enum class Transition(
    val bool: Boolean,
) {
    ON(true),
    OFF(false),
    NONE(false),
}

enum class BatteryHealth(
    private val value: String,
) {
    //    Unknown("?"),
    Good("g"),
    Overheat("h"),
    Dead("d"),
    OverVoltage("v"),
    Failure("f"),
    Cold("c"),
    ;

    companion object {
        private val map = values().associateBy(BatteryHealth::value)
        fun fromString(type: String) = map[type] // ?: Unknown
    }
}

enum class BatteryStatus(
    private val value: String,
) {
    //    Unknown("?"),
    Charging("c"),
    NotCharging("n"),
    Discharging("d"),
    Full("f"),
    ;

    companion object {
        private val map = values().associateBy(BatteryStatus::value)
        fun fromString(type: String) = map[type] // ?: Unknown
    }
}

enum class BatteryPlug(
    private val value: String,
) {
    //    Unknown("?"),
    NoPlug("n"),
    AC("a"),
    USB("u"),
    Wireless("w"),
    ;

    companion object {
        private val map = values().associateBy(BatteryPlug::value)
        fun fromString(type: String) = map[type] // ?: Unknown
    }
}

enum class SignalStrength(
    private val value: String,
) {
    NoSignal("0"),
    Poor("1"),
    Moderate("2"),
    Good("3"),
    Great("4"),
    ;

    companion object {
        private val map = values().associateBy(SignalStrength::value)
        fun fromString(type: String) = map[type]
    }
}

enum class GpsSignalStrength(
    private val value: String,
) {
    Good("good"),
    Poor("poor"),
    ;

    companion object {
        private val map = values().associateBy(GpsSignalStrength::value)
        fun fromString(type: String) = map[type]
    }
}

enum class DozeState(
    private val value: String,
) {
    Off("off"),
    Light("light"),
    Full("full"),
    ;

    companion object {
        private val map = values().associateBy(DozeState::value)
        fun fromString(type: String) = map[type]
    }
}

enum class ScreenBrightness(
    private val value: String,
) {
    Dark("0"),
    Dim("1"),
    Medium("2"),
    Light("3"),
    Bright("4"),
    ;

    companion object {
        private val map = values().associateBy(ScreenBrightness::value)
        fun fromString(type: String) = map[type]
    }
}

enum class WifiSupplicantState(
    private val value: String,
) {
    Invalid("inv"),
    Disconnected("dsc"),
    Disabled("dis"),
    Inactive("inact"),
    Scanning("scan"),
    Authenticating("auth"),
    Associating("ascing"),
    Associated("asced"),
    FourWayHandshake("4-way"),
    GroupHandshake("group"),
    Completed("compl"),
    Dormant("dorm"),
    Uninitialized("uninit"),
    ;

    companion object {
        private val map = values().associateBy(WifiSupplicantState::value)
        fun fromString(type: String) = map[type]
    }
}

enum class PhoneConnection(
    private val value: String,
) {
    None("none"),
    Other("other"),
    EHRPD("ehrpd"),
    LTE("lte"),
    EDGE("edge"),
    HSPA("hspa"),
    HSPAP("hspap"),
    OneXRTT("1xrtt"),
    ;

    companion object {
        private val map = values().associateBy(PhoneConnection::value)
        fun fromString(type: String) = map[type]
    }
}

enum class PhoneState(
    private val value: String,
) {
    In("in"),
    Out("out"),
    Emergency("em"),
    Off("off"),
    ;

    companion object {
        private val map = values().associateBy(PhoneState::value)
        fun fromString(type: String) = map[type]
    }
}
