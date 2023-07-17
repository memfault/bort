package com.memfault.bort.parsers

import com.memfault.bort.metrics.BatteryStatsAgg
import com.memfault.bort.metrics.BatteryStatsAgg.BatteryLevelAggregator
import com.memfault.bort.metrics.BatteryStatsAgg.CountByNominalAggregator
import com.memfault.bort.metrics.BatteryStatsAgg.MaximumValueAggregator
import com.memfault.bort.metrics.BatteryStatsAgg.TimeByNominalAggregator
import com.memfault.bort.metrics.BatteryStatsResult
import com.memfault.bort.metrics.HighResTelemetry.DataType
import com.memfault.bort.metrics.HighResTelemetry.DataType.DoubleType
import com.memfault.bort.metrics.HighResTelemetry.DataType.StringType
import com.memfault.bort.metrics.HighResTelemetry.Datum
import com.memfault.bort.metrics.HighResTelemetry.MetricType
import com.memfault.bort.metrics.HighResTelemetry.MetricType.Event
import com.memfault.bort.metrics.HighResTelemetry.MetricType.Gauge
import com.memfault.bort.metrics.HighResTelemetry.MetricType.Property
import com.memfault.bort.metrics.HighResTelemetry.Rollup
import com.memfault.bort.metrics.HighResTelemetry.RollupMetadata
import com.memfault.bort.parsers.BatteryStatsConstants.ALARM
import com.memfault.bort.parsers.BatteryStatsConstants.AUDIO
import com.memfault.bort.parsers.BatteryStatsConstants.BATTERY_COULOMB
import com.memfault.bort.parsers.BatteryStatsConstants.BATTERY_HEALTH
import com.memfault.bort.parsers.BatteryStatsConstants.BATTERY_LEVEL
import com.memfault.bort.parsers.BatteryStatsConstants.BATTERY_PLUG
import com.memfault.bort.parsers.BatteryStatsConstants.BATTERY_PLUGGED
import com.memfault.bort.parsers.BatteryStatsConstants.BATTERY_STATUS
import com.memfault.bort.parsers.BatteryStatsConstants.BATTERY_TEMP
import com.memfault.bort.parsers.BatteryStatsConstants.BATTERY_VOLTAGE
import com.memfault.bort.parsers.BatteryStatsConstants.BLUETOOTH_LE_SCANNING
import com.memfault.bort.parsers.BatteryStatsConstants.BOOL_VALUE_FALSE
import com.memfault.bort.parsers.BatteryStatsConstants.BOOL_VALUE_TRUE
import com.memfault.bort.parsers.BatteryStatsConstants.BatteryHealth
import com.memfault.bort.parsers.BatteryStatsConstants.BatteryPlug
import com.memfault.bort.parsers.BatteryStatsConstants.BatteryStatus
import com.memfault.bort.parsers.BatteryStatsConstants.CAMERA
import com.memfault.bort.parsers.BatteryStatsConstants.CHARGING
import com.memfault.bort.parsers.BatteryStatsConstants.CPU_RUNNING
import com.memfault.bort.parsers.BatteryStatsConstants.DEVICE_ACTIVE
import com.memfault.bort.parsers.BatteryStatsConstants.DOZE
import com.memfault.bort.parsers.BatteryStatsConstants.DozeState
import com.memfault.bort.parsers.BatteryStatsConstants.FOREGROUND
import com.memfault.bort.parsers.BatteryStatsConstants.GPS_ON
import com.memfault.bort.parsers.BatteryStatsConstants.GPS_SIGNAL_STRENGTH
import com.memfault.bort.parsers.BatteryStatsConstants.GpsSignalStrength
import com.memfault.bort.parsers.BatteryStatsConstants.I_CONTENT_START
import com.memfault.bort.parsers.BatteryStatsConstants.I_TYPE
import com.memfault.bort.parsers.BatteryStatsConstants.I_VERSION
import com.memfault.bort.parsers.BatteryStatsConstants.JOB
import com.memfault.bort.parsers.BatteryStatsConstants.LONGWAKE
import com.memfault.bort.parsers.BatteryStatsConstants.PACKAGE_INSTALL
import com.memfault.bort.parsers.BatteryStatsConstants.PACKAGE_UNINSTALL
import com.memfault.bort.parsers.BatteryStatsConstants.PHONE_CONNECTION
import com.memfault.bort.parsers.BatteryStatsConstants.PHONE_IN_CALL
import com.memfault.bort.parsers.BatteryStatsConstants.PHONE_RADIO
import com.memfault.bort.parsers.BatteryStatsConstants.PHONE_SCANNING
import com.memfault.bort.parsers.BatteryStatsConstants.PHONE_SIGNAL_STRENGTH
import com.memfault.bort.parsers.BatteryStatsConstants.PHONE_STATE
import com.memfault.bort.parsers.BatteryStatsConstants.POWER_SAVE
import com.memfault.bort.parsers.BatteryStatsConstants.PhoneConnection
import com.memfault.bort.parsers.BatteryStatsConstants.PhoneSignalStrength
import com.memfault.bort.parsers.BatteryStatsConstants.SCREEN_BRIGHTNESS
import com.memfault.bort.parsers.BatteryStatsConstants.SCREEN_ON
import com.memfault.bort.parsers.BatteryStatsConstants.SENSOR
import com.memfault.bort.parsers.BatteryStatsConstants.START
import com.memfault.bort.parsers.BatteryStatsConstants.ScreenBrightness
import com.memfault.bort.parsers.BatteryStatsConstants.TOP_APP
import com.memfault.bort.parsers.BatteryStatsConstants.Transition
import com.memfault.bort.parsers.BatteryStatsConstants.USER
import com.memfault.bort.parsers.BatteryStatsConstants.USER_FOREGROUND
import com.memfault.bort.parsers.BatteryStatsConstants.VALID_VERSION
import com.memfault.bort.parsers.BatteryStatsConstants.VIDEO
import com.memfault.bort.parsers.BatteryStatsConstants.WAKELOCK
import com.memfault.bort.parsers.BatteryStatsConstants.WIFI_FULL_LOCK
import com.memfault.bort.parsers.BatteryStatsConstants.WIFI_MULTICAST
import com.memfault.bort.parsers.BatteryStatsConstants.WIFI_ON
import com.memfault.bort.parsers.BatteryStatsConstants.WIFI_RADIO
import com.memfault.bort.parsers.BatteryStatsConstants.WIFI_RUNNING
import com.memfault.bort.parsers.BatteryStatsConstants.WIFI_SCAN
import com.memfault.bort.parsers.BatteryStatsConstants.WIFI_SIGNAL_STRENGTH
import com.memfault.bort.parsers.BatteryStatsConstants.WIFI_SUPPLICANT
import com.memfault.bort.parsers.BatteryStatsConstants.WifiSignalStrength
import com.memfault.bort.parsers.BatteryStatsConstants.WifiSupplicantState
import com.memfault.bort.parsers.BatteryStatsConstants.enumNames
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.shared.Logger
import java.io.File
import java.lang.IllegalStateException
import java.lang.NumberFormatException
import java.util.LinkedList
import kotlinx.serialization.json.JsonPrimitive

class BatteryStatsHistoryParser(
    private val file: File,
) {
    private val hsp = mutableMapOf<Int, HspEntry>()
    private var used = false

    /**
     * Wall clock. This can get reset during a file. This is used for creating HRT metrics.
     */
    private var timeMs: Long? = null

    /**
     * Elapsed time during file, based on `h` deltas. Used for aggregates.
     */
    private var elapsedMs: Long = 0
    private var currentTopApp: String? = null
    private var currentForeground: String? = null
    private var reportedErrorMetric = false
    private val metrics = listOf(
        // TODO use bools for some of these instead of StringType?
        BatteryMetric(key = WAKELOCK, type = Property, dataType = StringType),
        BatteryMetric(
            key = CPU_RUNNING, type = Property, dataType = StringType,
            aggregations = listOf(
                TimeByNominalAggregator(metricName = "cpu_running_ratio", states = listOf(BOOL_VALUE_TRUE)),
                CountByNominalAggregator(metricName = "cpu_resume_count_per_hour", state = BOOL_VALUE_TRUE),
                CountByNominalAggregator(metricName = "cpu_suspend_count_per_hour", state = BOOL_VALUE_FALSE),
            )
        ),
        BatteryMetric(
            key = BATTERY_LEVEL, type = Gauge, dataType = DoubleType,
            aggregations = listOf(BatteryLevelAggregator())
        ),
        BatteryMetric(
            key = BATTERY_TEMP, type = Gauge, dataType = DoubleType,
            aggregations = listOf(MaximumValueAggregator(metricName = "max_battery_temp"))
        ),
        BatteryMetric(key = BATTERY_VOLTAGE, type = Gauge, dataType = DoubleType),
        BatteryMetric(key = BATTERY_COULOMB, type = Gauge, dataType = DoubleType),
        BatteryMetric(
            key = BATTERY_HEALTH, type = Property, dataType = StringType,
            aggregations = listOf(
                TimeByNominalAggregator(
                    metricName = "battery_health_not_good_ratio",
                    states = enumNames(
                        listOf(
                            BatteryHealth.Cold,
                            BatteryHealth.Failure,
                            BatteryHealth.OverVoltage,
                            BatteryHealth.Dead,
                            BatteryHealth.Overheat,
                        )
                    )
                ),
            )
        ),
        BatteryMetric(key = BATTERY_STATUS, type = Property, dataType = StringType),
        BatteryMetric(key = BATTERY_PLUG, type = Property, dataType = StringType),
        BatteryMetric(key = BATTERY_PLUGGED, type = Property, dataType = StringType),
        BatteryMetric(key = CHARGING, type = Property, dataType = StringType),
        BatteryMetric(
            key = AUDIO, type = Property, dataType = StringType,
            aggregations = listOf(
                TimeByNominalAggregator(metricName = "audio_on_ratio", states = listOf(BOOL_VALUE_TRUE)),
            )
        ),
        BatteryMetric(key = CAMERA, type = Property, dataType = StringType),
        BatteryMetric(key = VIDEO, type = Property, dataType = StringType),
        BatteryMetric(key = SENSOR, type = Property, dataType = StringType),
        BatteryMetric(
            key = GPS_ON, type = Property, dataType = StringType,
            aggregations = listOf(
                TimeByNominalAggregator(metricName = "gps_on_ratio", states = listOf(BOOL_VALUE_TRUE)),
            )
        ),
        BatteryMetric(key = GPS_SIGNAL_STRENGTH, type = Property, dataType = StringType),
        BatteryMetric(
            key = SCREEN_ON, type = Property, dataType = StringType,
            aggregations = listOf(
                TimeByNominalAggregator(metricName = "screen_on_ratio", states = listOf(BOOL_VALUE_TRUE)),
            )
        ),
        BatteryMetric(
            key = SCREEN_BRIGHTNESS, type = Property, dataType = StringType,
            aggregations = listOf(
                TimeByNominalAggregator(
                    metricName = "screen_brightness_light_or_bright_ratio",
                    states = enumNames(listOf(ScreenBrightness.Light, ScreenBrightness.Bright)),
                ),
            )
        ),
        BatteryMetric(
            key = WIFI_ON, type = Property, dataType = StringType,
            aggregations = listOf(
                TimeByNominalAggregator(metricName = "wifi_on_ratio", states = listOf(BOOL_VALUE_TRUE)),
            )
        ),
        BatteryMetric(key = WIFI_FULL_LOCK, type = Property, dataType = StringType),
        BatteryMetric(
            key = WIFI_SCAN, type = Property, dataType = StringType,
            aggregations = listOf(
                TimeByNominalAggregator(metricName = "wifi_scan_ratio", states = listOf(BOOL_VALUE_TRUE)),
            )
        ),
        BatteryMetric(key = WIFI_MULTICAST, type = Property, dataType = StringType),
        BatteryMetric(
            key = WIFI_RADIO, type = Property, dataType = StringType,
            aggregations = listOf(
                TimeByNominalAggregator(metricName = "wifi_radio_active_ratio", states = listOf(BOOL_VALUE_TRUE)),
            )
        ),
        BatteryMetric(
            key = WIFI_RUNNING, type = Property, dataType = StringType,
            aggregations = listOf(
                TimeByNominalAggregator(metricName = "wifi_running_ratio", states = listOf(BOOL_VALUE_TRUE)),
            )
        ),
        BatteryMetric(
            key = WIFI_SIGNAL_STRENGTH, type = Property, dataType = StringType,
            aggregations = listOf(
                TimeByNominalAggregator(
                    metricName = "wifi_signal_strength_poor_or_very_poor_ratio",
                    states = enumNames(listOf(WifiSignalStrength.Poor, WifiSignalStrength.VeryPoor)),
                ),
            ),
        ),
        BatteryMetric(key = WIFI_SUPPLICANT, type = Property, dataType = StringType),
        BatteryMetric(key = POWER_SAVE, type = Property, dataType = StringType),
        BatteryMetric(
            key = DOZE, type = Property, dataType = StringType,
            aggregations = listOf(
                TimeByNominalAggregator(metricName = "doze_full_ratio", states = enumNames(listOf(DozeState.Full))),
                TimeByNominalAggregator(
                    metricName = "doze_ratio",
                    states = enumNames(listOf(DozeState.Full, DozeState.Light)),
                ),
            )
        ),
        BatteryMetric(key = USER, type = Property, dataType = StringType),
        BatteryMetric(key = USER_FOREGROUND, type = Property, dataType = StringType),
        BatteryMetric(key = JOB, type = Property, dataType = StringType),
        BatteryMetric(key = PACKAGE_INSTALL, type = Event, dataType = StringType),
        BatteryMetric(key = PACKAGE_UNINSTALL, type = Event, dataType = StringType),
        BatteryMetric(key = DEVICE_ACTIVE, type = Property, dataType = StringType),
        BatteryMetric(
            key = BLUETOOTH_LE_SCANNING, type = Property, dataType = StringType,
            aggregations = listOf(
                TimeByNominalAggregator(metricName = "bluetooth_scan_ratio", states = listOf(BOOL_VALUE_TRUE)),
            )
        ),
        BatteryMetric(
            key = PHONE_RADIO, type = Property, dataType = StringType,
            aggregations = listOf(
                TimeByNominalAggregator(metricName = "phone_radio_active_ratio", states = listOf(BOOL_VALUE_TRUE)),
            )
        ),
        BatteryMetric(key = PHONE_CONNECTION, type = Property, dataType = StringType),
        BatteryMetric(key = PHONE_IN_CALL, type = Property, dataType = StringType),
        BatteryMetric(
            key = PHONE_SCANNING, type = Property, dataType = StringType,
            aggregations = listOf(
                TimeByNominalAggregator(metricName = "phone_scanning_ratio", states = listOf(BOOL_VALUE_TRUE)),
            )
        ),
        BatteryMetric(
            key = PHONE_SIGNAL_STRENGTH, type = Property, dataType = StringType,
            aggregations = listOf(
                TimeByNominalAggregator(
                    metricName = "phone_signal_strength_none_ratio",
                    states = enumNames(listOf(PhoneSignalStrength.NoSignal)),
                ),
                TimeByNominalAggregator(
                    metricName = "phone_signal_strength_poor_ratio",
                    states = enumNames(listOf(PhoneSignalStrength.Poor)),
                ),
            )
        ),
        BatteryMetric(key = PHONE_STATE, type = Property, dataType = StringType),
        BatteryMetric(key = TOP_APP, type = Property, dataType = StringType),
        BatteryMetric(key = FOREGROUND, type = Property, dataType = StringType),
        BatteryMetric(key = LONGWAKE, type = Property, dataType = StringType),
        BatteryMetric(key = ALARM, type = Property, dataType = StringType),
        BatteryMetric(key = START, type = Event, dataType = StringType),
    )
    private val metricsMap = metrics.associateBy { it.key }

    private inner class BatteryMetric(
        val key: String,
        private val type: MetricType,
        private val dataType: DataType,
        private val data: LinkedList<Datum> = LinkedList(),
        private val aggregations: List<BatteryStatsAgg> = emptyList(),
        private val hrt: Boolean = true,
    ) {
        /**
         * Gather the HRT follup for this metric (if there are any values recorded).
         */
        fun rollup(): Rollup? {
            if (data.isEmpty()) return null
            return Rollup(
                metadata = RollupMetadata(
                    stringKey = key,
                    metricType = type,
                    dataType = dataType,
                    internal = false,
                ),
                data = data,
            )
        }

        /**
         * Gather the aggregated metric(s).
         */
        fun aggregates(): List<Pair<String, JsonPrimitive>> {
            return aggregations.flatMap { it.finish(elapsedMs) }
        }

        /**
         * Add a value, at the current timestamp.
         */
        fun addValue(value: JsonPrimitive) {
            val time = timeMs ?: return
            if (hrt) {
                // Drop any previous values which are >= this timestamp (i.e. if there was a backwards time change).
                data.dropLastWhile { it.t >= time }
                data.add(Datum(t = time, value))
            }
            // Add to aggregations (using elapsed time).
            aggregations.forEach { it.addValue(elapsedMs, value) }
        }
    }

    fun parseToCustomMetrics(): BatteryStatsResult {
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
                } catch (e: ArrayIndexOutOfBoundsException) {
                    Logger.i("parseToCustomMetrics $line", e)
                    reportErrorMetric()
                } catch (e: NumberFormatException) {
                    Logger.i("parseToCustomMetrics $line", e)
                    reportErrorMetric()
                }
            }
        }
        val hrt = metrics.mapNotNull { it.rollup() }.toSet()
        val aggregateMetrics = mutableMapOf<String, JsonPrimitive>()
        metrics.forEach { aggregateMetrics.putAll(it.aggregates()) }
        return BatteryStatsResult(
            batteryStatsFileToUpload = null,
            batteryStatsHrt = hrt,
            aggregatedMetrics = aggregateMetrics,
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun parseHeader(entries: List<String>) = Unit

    data class HspEntry(
        val key: Int,
        val uid: Int,
        val value: String,
    )

    private fun addValue(key: String, value: String?) {
        addValue(key, JsonPrimitive(value))
    }

    private fun addValue(key: String, value: Boolean?) {
        // null is false
        addValue(key, if (value == true) BOOL_VALUE_TRUE else BOOL_VALUE_FALSE)
    }

    private fun <T : Enum<T>> addValue(key: String, value: Enum<T>?) {
        addValue(key, JsonPrimitive(value?.name ?: ""))
    }

    private fun addValue(key: String, value: Long?) {
        addValue(key, JsonPrimitive(value))
    }

    private fun addValue(key: String, value: JsonPrimitive) {
        metricsMap[key]?.addValue(value = value)
    }

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

    private fun addElapsedTime(sinceLast: Long) {
        timeMs = timeMs?.plus(sinceLast)?.also {
            // Only increment if time was already set (we see crazy initial values for this before that).
            elapsedMs += sinceLast
        }
    }

    private fun parseHistory(entries: List<String>) {
        val command = entries[0].split(":")
        if (command.size > 1) {
            val sinceLast = command[0].toLong()
            addElapsedTime(sinceLast)
            when (command[1]) {
                "TIME" -> timeMs = command[2].toLong()
                "RESET" -> timeMs = command[3].toLong()
                "START" -> timeMs?.let { addValue(START, "Start") }
                "SHUTDOWN" -> timeMs?.let { addValue(START, "Shutdown") }
            }
        } else {
            val sinceLast = entries[0].toLong()
            addElapsedTime(sinceLast)
            timeMs?.let { parseEvent(entries.drop(1)) }
        }
    }

    // Bl=85
    // +r
    // -r
    private fun parseEvent(entries: List<String>) {
        entries.forEach { event ->
            // Separate try/catch for each entry within the line, so that we don't wipe out every entry if we see one
            // parsing error.
            try {
                val transition = when (event[0]) {
                    '+' -> Transition.ON
                    '-' -> Transition.OFF
                    else -> Transition.NONE
                }
                val content = when (transition) {
                    Transition.NONE -> event
                    else -> event.drop(1)
                }.split("=")
                val eventType = content[0]
                val value = content.getOrNull(1)
                when (eventType) {
                    "w" -> addValue(WAKELOCK, if (transition.bool) hspLookup(value)?.value else null)
                    "r" -> addValue(CPU_RUNNING, transition.bool)
                    // TODO MFLT-9391 don't add battery metrics if device doesn't have a battery.
                    "Bl" -> addValue(BATTERY_LEVEL, value?.toLong()!!)
                    "Bt" -> addValue(BATTERY_TEMP, value?.toLong()!!)
                    "Bv" -> addValue(BATTERY_VOLTAGE, value?.toLong()!!)
                    "Bcc" -> addValue(BATTERY_COULOMB, value?.toLong()!!)
                    "Bh" -> addValue(BATTERY_HEALTH, BatteryHealth.fromString(value!!))
                    "Bs" -> addValue(BATTERY_STATUS, BatteryStatus.fromString(value!!))
                    "Bp" -> addValue(BATTERY_PLUG, BatteryPlug.fromString(value!!))
                    "BP" -> addValue(BATTERY_PLUGGED, transition.bool)
                    "ch" -> addValue(CHARGING, transition.bool)
                    "a" -> addValue(AUDIO, transition.bool)
                    "ca" -> addValue(CAMERA, transition.bool)
                    "v" -> addValue(VIDEO, transition.bool)
                    "s" -> addValue(SENSOR, transition.bool)
                    "g" -> addValue(GPS_ON, transition.bool)
                    "Gss" -> addValue(GPS_SIGNAL_STRENGTH, GpsSignalStrength.fromString(value!!))
                    "S" -> addValue(SCREEN_ON, transition.bool)
                    "Sb" -> addValue(SCREEN_BRIGHTNESS, ScreenBrightness.fromString(value!!))
                    // new
                    "W" -> addValue(WIFI_ON, transition.bool)
                    "Wl" -> addValue(WIFI_FULL_LOCK, transition.bool)
                    "Ws" -> addValue(WIFI_SCAN, transition.bool)
                    "Wm" -> addValue(WIFI_MULTICAST, transition.bool)
                    "Wr" -> addValue(WIFI_RADIO, transition.bool)
                    "Ww" -> addValue(WIFI_RUNNING, transition.bool)
                    "Wss" -> addValue(WIFI_SIGNAL_STRENGTH, WifiSignalStrength.fromString(value!!))
                    "Wsp" -> addValue(WIFI_SUPPLICANT, WifiSupplicantState.fromString(value!!))
                    "ps", "lp" -> addValue(POWER_SAVE, transition.bool)
                    "di" -> addValue(DOZE, DozeState.fromString(value!!))
                    "Etp" -> run {
                        val lookupName = hspLookup(value)?.value
                        val topApp = if (transition.bool) lookupName else null
                        // A -Etp=123 might be reported after +Etp=456,
                        // so only clear if this is the currently foregrounded app
                        if (!transition.bool && lookupName != currentTopApp) return@run
                        currentTopApp = topApp
                        addValue(TOP_APP, topApp)
                    }
                    "Efg" -> run {
                        val lookupName = hspLookup(value)?.value
                        val foreground = if (transition.bool) lookupName else null
                        // A -Efg=123 might be reported after +Efg=456,
                        // so only clear if this is the currently foregrounded app
                        if (!transition.bool && lookupName != currentForeground) return@run
                        currentForeground = foreground
                        addValue(FOREGROUND, foreground)
                    }
                    // new
                    "Eur" -> addValue(USER, if (transition.bool) hspLookup(value)?.value else null)
                    // new
                    "Euf" -> addValue(USER_FOREGROUND, if (transition.bool) hspLookup(value)?.value else null)
                    // new (didn't show app before)
                    "Ejb" -> addValue(JOB, if (transition.bool) hspLookup(value)?.value else null)
                    "Elw" -> addValue(LONGWAKE, if (transition.bool) hspLookup(value)?.value else null)
                    // new
                    "Epi" -> hspLookup(value)?.let {
                        addValue(PACKAGE_INSTALL, "${it.value}: ${it.uid}")
                    }
                    // new
                    "Epu" -> hspLookup(value)?.let {
                        addValue(PACKAGE_UNINSTALL, "${it.value}: ${it.uid}")
                    }
                    "Eac" -> addValue(DEVICE_ACTIVE, hspLookup(value)?.value)
                    "bles" -> addValue(BLUETOOTH_LE_SCANNING, transition.bool)
                    "Pr" -> addValue(PHONE_RADIO, transition.bool)
                    "Pcn" -> addValue(PHONE_CONNECTION, PhoneConnection.fromString(value!!))
                    "Pcl" -> addValue(PHONE_IN_CALL, transition.bool)
                    "Psc" -> addValue(PHONE_SCANNING, transition.bool)
                    "Pss" -> addValue(PHONE_SIGNAL_STRENGTH, PhoneSignalStrength.fromString(value!!))
                    "Pst" -> {
                        addValue(PHONE_STATE, transition.bool)
                        if (!transition.bool) {
                            addValue(PHONE_SIGNAL_STRENGTH, null as String?)
                        }
                    }
                    "Eal" -> addValue(ALARM, hspLookup(value)?.value)
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
        private val BATTERYSTATS_PARSE_ERROR_METRIC =
            Reporting.report().counter(name = "batterystats_parse_error", internal = true)
    }
}
