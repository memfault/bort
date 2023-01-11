package com.memfault.bort.parsers

import com.memfault.bort.reporting.Reporting
import com.memfault.bort.reporting.StateAgg
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

interface BatteryStatsHistoryMetricLogger {
    fun wakelock(timeMs: Long, wakeApp: String?)
    fun cpuRunning(timeMs: Long, running: Boolean)
    fun batteryLevel(timeMs: Long, level: Long)
    fun batteryTemp(timeMs: Long, temp: Long)
    fun batteryVoltage(timeMs: Long, voltage: Long)
    fun batteryCoulombCharge(timeMs: Long, coulomb: Long)
    fun batteryHealth(timeMs: Long, health: BatteryHealth?)
    fun batteryStatus(timeMs: Long, status: BatteryStatus?)
    fun batteryPlug(timeMs: Long, plug: BatteryPlug?)
    fun batteryPlugged(timeMs: Long, plugged: Boolean)
    fun charging(timeMs: Long, charging: Boolean)
    fun audio(timeMs: Long, audio: Boolean)
    fun camera(timeMs: Long, camera: Boolean)
    fun video(timeMs: Long, video: Boolean)
    fun sensor(timeMs: Long, sensor: Boolean)
    fun gpsOn(timeMs: Long, gpsOn: Boolean)
    fun gpsSignalStrength(timeMs: Long, gpsSignalStrength: GpsSignalStrength?)
    fun screenOn(timeMs: Long, screenOn: Boolean)
    fun screenBrightness(timeMs: Long, screenBrightness: ScreenBrightness?)
    fun wifiOn(timeMs: Long, wifiOn: Boolean)
    fun wifiFullLock(timeMs: Long, wifiFullLock: Boolean)
    fun wifiScan(timeMs: Long, wifiScan: Boolean)
    fun wifiMulticast(timeMs: Long, wifiMulticast: Boolean)
    fun wifiRadio(timeMs: Long, wifiRadio: Boolean)
    fun wifiRunning(timeMs: Long, wifiRunning: Boolean)
    fun wifiSignalStrength(timeMs: Long, wifiSignalStrength: SignalStrength?)
    fun wifiSupplicantState(timeMs: Long, wifiSupplicantState: WifiSupplicantState?)
    fun powerSave(timeMs: Long, powerSave: Boolean)
    fun doze(timeMs: Long, doze: DozeState?)
    fun user(timeMs: Long, user: String?)
    fun userForeground(timeMs: Long, userForeground: String?)
    fun job(timeMs: Long, job: String?)
    fun packageInstall(timeMs: Long, packageInstall: String)
    fun packageUninstall(timeMs: Long, packageUninstall: String)
    fun deviceActive(timeMs: Long, deviceActive: String?)
    fun bleScanning(timeMs: Long, bleScanning: Boolean)
    fun phoneRadio(timeMs: Long, phoneRadio: Boolean)
    fun phoneConnection(timeMs: Long, phoneConnection: PhoneConnection?)
    fun phoneInCall(timeMs: Long, phoneInCall: Boolean)
    fun phoneScanning(timeMs: Long, phoneScanning: Boolean)
    fun phoneSignalStrength(timeMs: Long, phoneSignalStrength: SignalStrength?)
    fun phoneState(timeMs: Long, phoneState: Boolean)
    fun topApp(timeMs: Long, topApp: String?)
    fun foreground(timeMs: Long, foreground: String?)
    fun longwake(timeMs: Long, longwake: String?)
    fun alarm(timeMs: Long, alarm: String?)
    fun start(timeMs: Long)
    fun shutdown(timeMs: Long)
}

@ContributesBinding(SingletonComponent::class)
class BatteryStatsHistoryCustomMetricLogger @Inject constructor() : BatteryStatsHistoryMetricLogger {
    // TODO don't enable carryover for batterystats metrics which have built-in carryover.

    override fun wakelock(timeMs: Long, wakeApp: String?) {
        wakelockMetric.state(wakeApp, timestamp = timeMs)
    }

    override fun cpuRunning(timeMs: Long, running: Boolean) {
        cpuRunningMetric.state(running.asMetricState(), timestamp = timeMs)
    }

    override fun batteryLevel(timeMs: Long, level: Long) {
        batteryLevelMetric.record(level, timestamp = timeMs)
    }

    override fun batteryTemp(timeMs: Long, temp: Long) {
        batteryTempMetric.record(temp, timestamp = timeMs)
    }

    override fun batteryVoltage(timeMs: Long, voltage: Long) {
        batteryVoltageMetric.record(voltage, timestamp = timeMs)
    }

    override fun batteryCoulombCharge(timeMs: Long, coulomb: Long) {
        batteryCoulombChargeMetric.record(coulomb, timestamp = timeMs)
    }

    override fun batteryHealth(timeMs: Long, health: BatteryHealth?) {
        batteryHealthMetric.state(health, timestamp = timeMs)
    }

    override fun batteryStatus(timeMs: Long, status: BatteryStatus?) {
        batteryStatusMetric.state(status, timestamp = timeMs)
    }

    override fun batteryPlug(timeMs: Long, plug: BatteryPlug?) {
        batteryPlugMetric.state(plug, timestamp = timeMs)
    }

    override fun batteryPlugged(timeMs: Long, plugged: Boolean) {
        batteryPluggedMetric.state(plugged.asMetricState(), timestamp = timeMs)
    }

    override fun charging(timeMs: Long, charging: Boolean) {
        chargingMetric.state(charging.asMetricState(), timestamp = timeMs)
    }

    override fun audio(timeMs: Long, audio: Boolean) {
        audioMetric.state(audio.asMetricState(), timestamp = timeMs)
    }

    override fun camera(timeMs: Long, camera: Boolean) {
        cameraMetric.state(camera.asMetricState(), timestamp = timeMs)
    }

    override fun video(timeMs: Long, video: Boolean) {
        videoMetric.state(video.asMetricState(), timestamp = timeMs)
    }

    override fun sensor(timeMs: Long, sensor: Boolean) {
        sensorMetric.state(sensor.asMetricState(), timestamp = timeMs)
    }

    override fun gpsOn(timeMs: Long, gpsOn: Boolean) {
        gpsOnMetric.state(gpsOn.asMetricState(), timestamp = timeMs)
    }

    override fun gpsSignalStrength(timeMs: Long, gpsSignalStrength: GpsSignalStrength?) {
        gpsSignalStrengthMetric.state(gpsSignalStrength, timestamp = timeMs)
    }

    override fun screenOn(timeMs: Long, screenOn: Boolean) {
        screenOnMetric.state(screenOn.asMetricState(), timestamp = timeMs)
    }

    override fun screenBrightness(timeMs: Long, screenBrightness: ScreenBrightness?) {
        screenBrightnessMetric.state(screenBrightness, timestamp = timeMs)
    }

    override fun wifiOn(timeMs: Long, wifiOn: Boolean) {
        wifiOnMetric.state(wifiOn.asMetricState(), timestamp = timeMs)
    }

    override fun wifiFullLock(timeMs: Long, wifiFullLock: Boolean) {
        wifiFullLockMetric.state(wifiFullLock.asMetricState(), timestamp = timeMs)
    }

    override fun wifiScan(timeMs: Long, wifiScan: Boolean) {
        wifiScanMetric.state(wifiScan.asMetricState(), timestamp = timeMs)
    }

    override fun wifiMulticast(timeMs: Long, wifiMulticast: Boolean) {
        wifiMulticastMetric.state(wifiMulticast.asMetricState(), timestamp = timeMs)
    }

    override fun wifiRadio(timeMs: Long, wifiRadio: Boolean) {
        wifiRadioMetric.state(wifiRadio.asMetricState(), timestamp = timeMs)
    }

    override fun wifiRunning(timeMs: Long, wifiRunning: Boolean) {
        wifiRunningMetric.state(wifiRunning.asMetricState(), timestamp = timeMs)
    }

    override fun wifiSignalStrength(timeMs: Long, wifiSignalStrength: SignalStrength?) {
        wifiSignalStrengthMetric.state(wifiSignalStrength, timestamp = timeMs)
    }

    override fun wifiSupplicantState(timeMs: Long, wifiSupplicantState: WifiSupplicantState?) {
        wifiSupplicantStateMetric.state(wifiSupplicantState, timestamp = timeMs)
    }

    override fun powerSave(timeMs: Long, powerSave: Boolean) {
        powerSaveMetric.state(powerSave.asMetricState(), timestamp = timeMs)
    }

    override fun doze(timeMs: Long, doze: DozeState?) {
        dozeMetric.state(doze, timestamp = timeMs)
    }

    override fun user(timeMs: Long, user: String?) {
        userMetric.state(user, timestamp = timeMs)
    }

    override fun userForeground(timeMs: Long, userForeground: String?) {
        userForegroundMetric.state(userForeground, timestamp = timeMs)
    }

    override fun job(timeMs: Long, job: String?) {
        jobMetric.state(job, timestamp = timeMs)
    }

    override fun packageInstall(timeMs: Long, packageInstall: String) {
        packageInstallMetric.add(packageInstall, timestamp = timeMs)
    }

    override fun packageUninstall(timeMs: Long, packageUninstall: String) {
        packageUninstallMetric.add(packageUninstall, timestamp = timeMs)
    }

    override fun deviceActive(timeMs: Long, deviceActive: String?) {
        deviceActiveMetric.state(deviceActive, timestamp = timeMs)
    }

    override fun bleScanning(timeMs: Long, bleScanning: Boolean) {
        bleScanningMetric.state(bleScanning.asMetricState(), timestamp = timeMs)
    }

    override fun phoneRadio(timeMs: Long, phoneRadio: Boolean) {
        phoneRadioMetric.state(phoneRadio.asMetricState(), timestamp = timeMs)
    }

    override fun phoneConnection(timeMs: Long, phoneConnection: PhoneConnection?) {
        phoneConnectionMetric.state(phoneConnection, timestamp = timeMs)
    }

    override fun phoneInCall(timeMs: Long, phoneInCall: Boolean) {
        phoneInCallMetric.state(phoneInCall.asMetricState(), timestamp = timeMs)
    }

    override fun phoneScanning(timeMs: Long, phoneScanning: Boolean) {
        phoneScanningMetric.state(phoneScanning.asMetricState(), timestamp = timeMs)
    }

    override fun phoneSignalStrength(timeMs: Long, phoneSignalStrength: SignalStrength?) {
        phoneSignalStrengthMetric.state(phoneSignalStrength, timestamp = timeMs)
    }

    override fun phoneState(timeMs: Long, phoneState: Boolean) {
        phoneStateMetric.state(phoneState.asMetricState(), timestamp = timeMs)
    }

    override fun topApp(timeMs: Long, topApp: String?) {
        topAppMetric.state(topApp, timestamp = timeMs)
    }

    override fun foreground(timeMs: Long, foreground: String?) {
        foregroundMetric.state(foreground, timestamp = timeMs)
    }

    override fun longwake(timeMs: Long, longwake: String?) {
        longwakeMetric.state(longwake, timestamp = timeMs)
    }

    override fun alarm(timeMs: Long, alarm: String?) {
        alarmMetric.state(alarm, timestamp = timeMs)
    }

    override fun start(timeMs: Long) {
        startMetric.add("Start")
    }

    override fun shutdown(timeMs: Long) {
        startMetric.add("Shutdown")
    }

    companion object {
        const val WAKELOCK = "wakelock"
        private val wakelockMetric = Reporting.report().stringStateTracker(WAKELOCK)
        const val CPU_RUNNING = "cpu_running"
        val cpuRunningMetric = Reporting.report().stringStateTracker(CPU_RUNNING)
        const val BATTERY_LEVEL = "battery_level"
        val batteryLevelMetric = Reporting.report().distribution(BATTERY_LEVEL)
        const val BATTERY_TEMP = "battery_temperature"
        val batteryTempMetric = Reporting.report().distribution(BATTERY_TEMP)
        const val BATTERY_VOLTAGE = "battery_voltage"
        val batteryVoltageMetric = Reporting.report().distribution(BATTERY_VOLTAGE)
        const val BATTERY_COULOMB = "battery_coulomb_charge"
        val batteryCoulombChargeMetric = Reporting.report().distribution(BATTERY_COULOMB)
        const val BATTERY_HEALTH = "battery_health"
        val batteryHealthMetric = Reporting.report().stateTracker<BatteryHealth>(BATTERY_HEALTH)
        const val BATTERY_STATUS = "battery_status"
        val batteryStatusMetric = Reporting.report().stateTracker<BatteryStatus>(BATTERY_STATUS)
        const val BATTERY_PLUG = "battery_plug"
        val batteryPlugMetric = Reporting.report().stateTracker<BatteryPlug>(BATTERY_PLUG)
        const val BATTERY_PLUGGED = "battery_plugged"
        val batteryPluggedMetric = Reporting.report().stringStateTracker(BATTERY_PLUGGED)
        const val CHARGING = "charging"
        val chargingMetric = Reporting.report().stringStateTracker(CHARGING)
        const val AUDIO = "audio"
        val audioMetric = Reporting.report().stringStateTracker(AUDIO, aggregations = listOf(StateAgg.TIME_PER_HOUR))
        const val CAMERA = "camera"
        val cameraMetric = Reporting.report().stringStateTracker(CAMERA)
        const val VIDEO = "video"
        val videoMetric = Reporting.report().stringStateTracker(VIDEO)
        const val SENSOR = "sensor"
        val sensorMetric = Reporting.report().stringStateTracker(SENSOR)
        const val GPS_ON = "gps_on"
        val gpsOnMetric = Reporting.report().stringStateTracker(GPS_ON)
        const val GPS_SIGNAL_STRENGTH = "gps_signal_strength"
        val gpsSignalStrengthMetric = Reporting.report().stateTracker<GpsSignalStrength>(GPS_SIGNAL_STRENGTH)
        const val SCREEN_ON = "screen_on"
        val screenOnMetric = Reporting.report().stringStateTracker(SCREEN_ON)
        const val SCREEN_BRIGHTNESS = "screen_brightness"
        val screenBrightnessMetric = Reporting.report().stateTracker<ScreenBrightness>(SCREEN_BRIGHTNESS)
        const val WIFI_ON = "wifi_on"
        val wifiOnMetric = Reporting.report().stringStateTracker(WIFI_ON)
        const val WIFI_FULL_LOCK = "wifi_full_lock"
        val wifiFullLockMetric = Reporting.report().stringStateTracker(WIFI_FULL_LOCK)
        const val WIFI_SCAN = "wifi_scan"
        val wifiScanMetric = Reporting.report().stringStateTracker(WIFI_SCAN)
        const val WIFI_MULTICAST = "wifi_multicast"
        val wifiMulticastMetric = Reporting.report().stringStateTracker(WIFI_MULTICAST)
        const val WIFI_RADIO = "wifi_radio"
        val wifiRadioMetric = Reporting.report().stringStateTracker(WIFI_RADIO)
        const val WIFI_RUNNING = "wifi_running"
        val wifiRunningMetric = Reporting.report().stringStateTracker(WIFI_RUNNING)
        const val WIFI_SIGNAL_STRENGTH = "wifi_signal_strength"
        val wifiSignalStrengthMetric = Reporting.report().stateTracker<SignalStrength>(WIFI_SIGNAL_STRENGTH)
        const val WIFI_SUPPLICANT = "wifi_supplicant"
        val wifiSupplicantStateMetric = Reporting.report().stateTracker<WifiSupplicantState>(WIFI_SUPPLICANT)
        const val POWER_SAVE = "power_save"
        val powerSaveMetric = Reporting.report().stringStateTracker(POWER_SAVE)
        const val DOZE = "doze"
        val dozeMetric = Reporting.report().stateTracker<DozeState>(DOZE)
        const val USER = "user"
        val userMetric = Reporting.report().stringStateTracker(USER)
        const val USER_FOREGROUND = "user_foreground"
        val userForegroundMetric = Reporting.report().stringStateTracker(USER_FOREGROUND)
        const val JOB = "job"
        val jobMetric = Reporting.report().stringStateTracker(JOB)
        const val PACKAGE_INSTALL = "package_install"
        val packageInstallMetric = Reporting.report().event(PACKAGE_INSTALL)
        const val PACKAGE_UNINSTALL = "package_uninstall"
        val packageUninstallMetric = Reporting.report().event(PACKAGE_UNINSTALL)
        const val DEVICE_ACTIVE = "device_active"
        val deviceActiveMetric = Reporting.report().stringStateTracker(DEVICE_ACTIVE)
        const val BLUETOOTH_LE_SCANNING = "bluetooth_le_scanning"
        val bleScanningMetric = Reporting.report().stringStateTracker(BLUETOOTH_LE_SCANNING)
        const val PHONE_RADIO = "phone_radio"
        val phoneRadioMetric = Reporting.report().stringStateTracker(PHONE_RADIO)
        const val PHONE_CONNECTION = "phone_connection"
        val phoneConnectionMetric = Reporting.report().stateTracker<PhoneConnection>(PHONE_CONNECTION)
        const val PHONE_IN_CALL = "phone_in_call"
        val phoneInCallMetric = Reporting.report().stringStateTracker(PHONE_IN_CALL)
        const val PHONE_SCANNING = "phone_scanning"
        val phoneScanningMetric = Reporting.report().stringStateTracker(PHONE_SCANNING)
        const val PHONE_SIGNAL_STRENGTH = "phone_signal_strength"
        val phoneSignalStrengthMetric = Reporting.report().stateTracker<SignalStrength>(PHONE_SIGNAL_STRENGTH)
        const val PHONE_STATE = "phone_state"
        val phoneStateMetric = Reporting.report().stringStateTracker(PHONE_STATE)
        const val TOP_APP = "top_app"
        val topAppMetric = Reporting.report().stringStateTracker(TOP_APP)
        const val FOREGROUND = "foreground"
        val foregroundMetric = Reporting.report().stringStateTracker(FOREGROUND)
        const val LONGWAKE = "longwake"
        val longwakeMetric = Reporting.report().stringStateTracker(LONGWAKE)
        const val ALARM = "alarm"
        val alarmMetric = Reporting.report().stringStateTracker(ALARM)
        const val START = "start"
        val startMetric = Reporting.report().event(START)
    }
}

private fun Boolean.asMetricState(): String? = if (this) "True" else null
