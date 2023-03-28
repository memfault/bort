package com.memfault.bort.parsers

import kotlinx.serialization.json.JsonPrimitive

object BatteryStatsConstants {
    const val VALID_VERSION = 9
    const val I_VERSION = 0
    const val I_TYPE = 1
    const val I_CONTENT_START = 2

    val BOOL_VALUE_TRUE = JsonPrimitive(true)
    val BOOL_VALUE_FALSE = JsonPrimitive(null as Boolean?)

    const val WAKELOCK = "wakelock"
    const val CPU_RUNNING = "cpu_running"
    const val BATTERY_LEVEL = "battery_level"
    const val BATTERY_TEMP = "battery_temperature"
    const val BATTERY_VOLTAGE = "battery_voltage"
    const val BATTERY_COULOMB = "battery_coulomb_charge"
    const val BATTERY_HEALTH = "battery_health"
    const val BATTERY_STATUS = "battery_status"
    const val BATTERY_PLUG = "battery_plug"
    const val BATTERY_PLUGGED = "battery_plugged"
    const val CHARGING = "charging"
    const val AUDIO = "audio"
    const val CAMERA = "camera"
    const val VIDEO = "video"
    const val SENSOR = "sensor"
    const val GPS_ON = "gps_on"
    const val GPS_SIGNAL_STRENGTH = "gps_signal_strength"
    const val SCREEN_ON = "screen_on"
    const val SCREEN_BRIGHTNESS = "screen_brightness"
    const val WIFI_ON = "wifi_on"
    const val WIFI_FULL_LOCK = "wifi_full_lock"
    const val WIFI_SCAN = "wifi_scan"
    const val WIFI_MULTICAST = "wifi_multicast"
    const val WIFI_RADIO = "wifi_radio"
    const val WIFI_RUNNING = "wifi_running"
    const val WIFI_SIGNAL_STRENGTH = "wifi_signal_strength"
    const val WIFI_SUPPLICANT = "wifi_supplicant"
    const val POWER_SAVE = "power_save"
    const val DOZE = "doze"
    const val USER = "user"
    const val USER_FOREGROUND = "user_foreground"
    const val JOB = "job"
    const val PACKAGE_INSTALL = "package_install"
    const val PACKAGE_UNINSTALL = "package_uninstall"
    const val DEVICE_ACTIVE = "device_active"
    const val BLUETOOTH_LE_SCANNING = "bluetooth_le_scanning"
    const val PHONE_RADIO = "phone_radio"
    const val PHONE_CONNECTION = "phone_connection"
    const val PHONE_IN_CALL = "phone_in_call"
    const val PHONE_SCANNING = "phone_scanning"
    const val PHONE_SIGNAL_STRENGTH = "phone_signal_strength"
    const val PHONE_STATE = "phone_state"
    const val TOP_APP = "top_app"
    const val FOREGROUND = "foreground"
    const val LONGWAKE = "longwake"
    const val ALARM = "alarm"
    const val START = "start"

    fun <T : Enum<T>> enumNames(values: List<Enum<T>>): List<JsonPrimitive> {
        return values.map { JsonPrimitive(it.name) }
    }

    enum class Transition(
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

    enum class PhoneSignalStrength(
        private val value: String,
    ) {
        NoSignal("0"),
        Poor("1"),
        Moderate("2"),
        Good("3"),
        Great("4"),
        ;

        companion object {
            private val map = values().associateBy(PhoneSignalStrength::value)
            fun fromString(type: String) = map[type]
        }
    }

    enum class WifiSignalStrength(
        private val value: String,
    ) {
        VeryPoor("0"),
        Poor("1"),
        Moderate("2"),
        Good("3"),
        Great("4"),
        ;

        companion object {
            private val map = values().associateBy(WifiSignalStrength::value)
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
}
