package com.memfault.bort.parsers

import com.memfault.bort.BortJson
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTimeProvider
import java.io.File
import javax.inject.Inject
import kotlinx.serialization.Serializable

/**
 * Parses `batterystats --checkin` output.
 */
class BatteryStatsSummaryParser @Inject constructor(
    private val timeProvider: AbsoluteTimeProvider,
) {
    private data class ParserContext(
        val uids: MutableMap<Int, String> = mutableMapOf(),
        var batteryState: BatteryState? = null,
        var dischargeData: DischargeData? = null,
        val powerUseItemData: MutableMap<String, PowerUseItemData> = mutableMapOf(),
    )

    fun parse(file: File): BatteryStatsSummary? {
        val context = ParserContext()
        context.apply {
            file.bufferedReader().useLines {
                it.forEach { line ->
                    try {
                        val entries = line.split(",")
                        val version = entries[I_VERSION].toInt()
                        if (version != VALID_VERSION) return@forEach
                        val uid = entries[I_UID].toInt()
                        val type = entries[I_TYPE]
                        val contentEntries = entries.drop(I_CONTENT_START)
                        when (type) {
                            "i" -> parsePackage(contentEntries)
                            "l" -> parseItem(uid, contentEntries)
                        }
                    } catch (e: IndexOutOfBoundsException) {
                        Logger.i("BatteryStatsSummaryParser $line", e)
                    } catch (e: NumberFormatException) {
                        Logger.i("BatteryStatsSummaryParser $line", e)
                    }
                }
            }

            val state = batteryState
            val discharge = dischargeData
            if (state != null && discharge != null) {
                return BatteryStatsSummary(
                    batteryState = state,
                    dischargeData = discharge,
                    powerUseItemData = powerUseItemData.values.toSet(),
                    timestampMs = timeProvider().timestamp.toEpochMilli()
                )
            }
            Logger.w("failed to parse batterystats summary: state=$state discharge=$discharge")
        }
        return null
    }

    private fun ParserContext.parsePackage(entries: List<String>) {
        val key = entries[0]
        if (key != UID) return
        val uid = entries[1].toInt()
        val pkg = entries[2]
        // Multiple packages share the same UID: we only track the last one
        uids[uid] = pkg
    }

    private fun ParserContext.parseItem(uid: Int, entries: List<String>) {
        val eventType = entries[0]
        val content = entries.drop(1)
        when (eventType) {
            "bt" -> parseBt(content)
            "dc" -> parseDischarge(content)
            "pwi" -> parsePowerUseItemData(uid, content)
        }
    }

    private fun ParserContext.parseBt(entries: List<String>) {
        batteryState = BatteryState(
            batteryRealtimeMs = entries[BATTERY_STATS_INDEX_BATTERY_REALTIME].toLong(),
            startClockTimeMs = entries[BATTERY_STATS_INDEX_START_CLOCK_TIME].toLong(),
            screenOffRealtimeMs = entries[BATTERY_STATS_INDEX_SCREEN_OFF_REALTIME].toLong(),
            estimatedBatteryCapacity = entries[BATTERY_STATS_INDEX_ESTIMATED_BATTERY_CAPACITY].toLong(),
        )
    }

    @Serializable
    data class BatteryState(
        val batteryRealtimeMs: Long,
        val startClockTimeMs: Long,
        val screenOffRealtimeMs: Long,
        val estimatedBatteryCapacity: Long,
    )

    private fun ParserContext.parseDischarge(entries: List<String>) {
        dischargeData = DischargeData(
            totalMaH = entries[DISCHARGE_INDEX_TOTAL_MAH].toLong(),
            totalMaHScreenOff = entries[DISCHARGE_INDEX_TOTAL_MAH_SCREEN_OFF].toLong(),
        )
    }

    @Serializable
    data class DischargeData(
        val totalMaH: Long,
        val totalMaHScreenOff: Long,
    )

    private fun ParserContext.uuidToName(uid: Int) = when {
        // Every system UID's usage is assigned to "android"
        uid <= UID_SYSTEM_MAX -> COMPONENT_ANDROID
        else -> uids[uid] ?: COMPONENT_UNKNOWN
    }

    private fun ParserContext.componentName(drainType: String, uid: Int): String = when (drainType) {
        UID -> uuidToName(uid)
        else -> SYSTEM_COMPONENT_MAP[drainType] ?: drainType
    }

    private fun ParserContext.parsePowerUseItemData(uid: Int, entries: List<String>) {
        val totalPowerMaH = entries[PUI_INDEX_TOTAL_POWER_MAH].toDouble()
        if (totalPowerMaH > 0) {
            val name = componentName(drainType = entries[PUI_INDEX_DRAIN_TYPE], uid = uid)
            val pwi = PowerUseItemData(
                name = name,
                totalPowerMaH = totalPowerMaH,
            )
            val existing = powerUseItemData[name]
            powerUseItemData[name] = pwi + existing
        }
    }

    @Serializable
    data class PowerUseItemData(
        val name: String,
        val totalPowerMaH: Double,
    )

    /**
     * All of the above - to be serialized for comparison next time.
     *
     * Bear in mind when changing this that values will be persisted: default values will be required for any new
     * fields.
     */
    @Serializable
    data class BatteryStatsSummary(
        val batteryState: BatteryState,
        val dischargeData: DischargeData,
        val powerUseItemData: Set<PowerUseItemData>,
        val timestampMs: Long,
    ) {
        companion object {
            fun BatteryStatsSummary.toJson() = BortJson.encodeToString(serializer(), this)
            fun decodeFromString(json: String) = BortJson.decodeFromString(serializer(), json)
        }
    }

    companion object {
        private const val VALID_VERSION = 9
        private const val I_VERSION = 0
        private const val I_UID = 1
        private const val I_TYPE = 2
        private const val I_CONTENT_START = 3
        private const val UID_SYSTEM_MAX = 10000
        private const val COMPONENT_ANDROID = "android"
        private const val COMPONENT_UNKNOWN = "unknown"
        private const val UID = "uid"
        private val SYSTEM_COMPONENT_MAP = mapOf(
            "scrn" to "screen",
            "blue" to "bluetooth",
            "ambi" to "ambient",
            "unacc" to COMPONENT_UNKNOWN,
            "???" to COMPONENT_ANDROID,
        )

        // We might use some of these in the future - save some time by leaving the constants here.
//        private const val DISCHARGE_INDEX_LOWER_BOUND = 0
//        private const val DISCHARGE_INDEX_UPPER_BOUND = 1
//        private const val DISCHARGE_INDEX_SCREEN_ON_PERC = 2
//        private const val DISCHARGE_INDEX_SCREEN_OFF_PERC = 3
        private const val DISCHARGE_INDEX_TOTAL_MAH = 4
        private const val DISCHARGE_INDEX_TOTAL_MAH_SCREEN_OFF = 5
//        private const val DISCHARGE_INDEX_DOZE_PERC = 6
//        private const val DISCHARGE_INDEX_DOZE_MAH = 7
//        private const val DISCHARGE_INDEX_LIGHT_DOZE_MAH = 8
//        private const val DISCHARGE_INDEX_DEEP_DOSE_MAH = 9

//        private const val BATTERY_STATS_INDEX_START_COUNT = 0
        private const val BATTERY_STATS_INDEX_BATTERY_REALTIME = 1
//        private const val BATTERY_STATS_INDEX_BATTERY_UPTIME = 2
//        private const val BATTERY_STATS_INDEX_TOTAL_REALTIME = 3
//        private const val BATTERY_STATS_INDEX_TOTAL_UPTIME = 4
        private const val BATTERY_STATS_INDEX_START_CLOCK_TIME = 5
        private const val BATTERY_STATS_INDEX_SCREEN_OFF_REALTIME = 6
//        private const val BATTERY_STATS_INDEX_SCREEN_OFF_UPTIME = 7
        private const val BATTERY_STATS_INDEX_ESTIMATED_BATTERY_CAPACITY = 8
//        private const val BATTERY_STATS_INDEX_LEARNED_MIN_BATTERY_CAPACITY = 9
//        private const val BATTERY_STATS_INDEX_LEANRED_MAX_BATTERY_CAPACITY = 10
//        private const val BATTERY_STATS_INDEX_DOZE_TIME = 11

        private const val PUI_INDEX_DRAIN_TYPE = 0
        private const val PUI_INDEX_TOTAL_POWER_MAH = 1

        operator fun PowerUseItemData.plus(other: PowerUseItemData?) = PowerUseItemData(
            name = name,
            totalPowerMaH = totalPowerMaH + (other?.totalPowerMaH ?: 0.0),
        )
    }
}
