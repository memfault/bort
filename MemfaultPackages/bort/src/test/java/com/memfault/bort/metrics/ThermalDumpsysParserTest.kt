package com.memfault.bort.metrics

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import com.memfault.bort.metrics.TemperatureType.BATTERY
import com.memfault.bort.metrics.TemperatureType.BCL_PERCENTAGE
import com.memfault.bort.metrics.TemperatureType.BCL_VOLTAGE
import com.memfault.bort.metrics.TemperatureType.CPU
import com.memfault.bort.metrics.TemperatureType.GPU
import com.memfault.bort.metrics.TemperatureType.NPU
import com.memfault.bort.metrics.TemperatureType.POWER_AMPLIFIER
import com.memfault.bort.metrics.TemperatureType.SKIN
import com.memfault.bort.metrics.TemperatureType.USB_PORT
import com.memfault.bort.metrics.ThrottlingSeverity.MODERATE
import com.memfault.bort.metrics.ThrottlingSeverity.NONE
import org.junit.Test

class ThermalDumpsysParserTest {
    @Test
    fun parsePixelOutput() {
        val output = """
            IsStatusOverride: false
            ThermalEventListeners:
            callbacks: 3
            killed: false
            broadcasts count: -1
            ThermalStatusListeners:
            callbacks: 4
            killed: false
            broadcasts count: -1
            Thermal Status: 0
            Cached temperatures:
            Temperature{mValue=54.600002, mType=0, mName=cpu0-silver-usr, mStatus=0}
            Temperature{mValue=53.9, mType=0, mName=cpu1-silver-usr, mStatus=0}
            Temperature{mValue=55.600002, mType=0, mName=cpu2-silver-usr, mStatus=0}
            Temperature{mValue=53.300003, mType=1, mName=gpu0-usr, mStatus=0}
            Temperature{mValue=53.000004, mType=1, mName=gpu1-usr, mStatus=0}
            Temperature{mValue=57.200005, mType=0, mName=cpu3-silver-usr, mStatus=0}
            Temperature{mValue=55.300003, mType=0, mName=cpu3-gold-usr, mStatus=0}
            Temperature{mValue=56.2, mType=0, mName=cpu2-gold-usr, mStatus=0}
            Temperature{mValue=55.9, mType=0, mName=cpu1-gold-usr, mStatus=0}
            Temperature{mValue=54.600002, mType=0, mName=cpu0-gold-usr, mStatus=0}
            Temperature{mValue=39.600002, mType=2, mName=maxfg, mStatus=0}
            Temperature{mValue=33.039, mType=4, mName=usbc-therm-monitor, mStatus=0}
            Temperature{mValue=37.083, mType=3, mName=fps-therm-monitor, mStatus=0}
            HAL Ready: true
            HAL connection:
            ThermalHAL 2.0 connected: yes
            Current temperatures from HAL:
            Temperature{mValue=31.7, mType=0, mName=cpu0-gold-usr, mStatus=0}
            Temperature{mValue=32.300003, mType=0, mName=cpu0-silver-usr, mStatus=0}
            Temperature{mValue=31.400002, mType=0, mName=cpu1-gold-usr, mStatus=0}
            Temperature{mValue=32.0, mType=0, mName=cpu1-silver-usr, mStatus=0}
            Temperature{mValue=30.400002, mType=0, mName=cpu2-gold-usr, mStatus=0}
            Temperature{mValue=32.0, mType=0, mName=cpu2-silver-usr, mStatus=0}
            Temperature{mValue=31.000002, mType=0, mName=cpu3-gold-usr, mStatus=0}
            Temperature{mValue=31.400002, mType=0, mName=cpu3-silver-usr, mStatus=0}
            Temperature{mValue=28.348001, mType=3, mName=fps-therm-monitor, mStatus=0}
            Temperature{mValue=31.400002, mType=1, mName=gpu0-usr, mStatus=0}
            Temperature{mValue=31.000002, mType=1, mName=gpu1-usr, mStatus=0}
            Temperature{mValue=25.900002, mType=2, mName=maxfg, mStatus=0}
            Temperature{mValue=26.926, mType=4, mName=usbc-therm-monitor, mStatus=0}
            Current cooling devices from HAL:
            CoolingDevice{mValue=0, mType=1, mName=battery}
            CoolingDevice{mValue=0, mType=5, mName=mnh}
            CoolingDevice{mValue=0, mType=2, mName=thermal-cpufreq-0}
            CoolingDevice{mValue=0, mType=2, mName=thermal-cpufreq-4}
            CoolingDevice{mValue=0, mType=3, mName=thermal-devfreq-0}
        """.trimIndent()
        val metrics = parseThermalDumpsys(output)
        assertThat(metrics).containsExactlyInAnyOrder(
            ThermalMetric(value = 31.7, type = CPU, name = "cpu0-gold-usr", status = NONE),
            ThermalMetric(value = 32.300003, type = CPU, name = "cpu0-silver-usr", status = NONE),
            ThermalMetric(value = 31.400002, type = CPU, name = "cpu1-gold-usr", status = NONE),
            ThermalMetric(value = 32.0, type = CPU, name = "cpu1-silver-usr", status = NONE),
            ThermalMetric(value = 30.400002, type = CPU, name = "cpu2-gold-usr", status = NONE),
            ThermalMetric(value = 32.0, type = CPU, name = "cpu2-silver-usr", status = NONE),
            ThermalMetric(value = 31.000002, type = CPU, name = "cpu3-gold-usr", status = NONE),
            ThermalMetric(value = 31.400002, type = CPU, name = "cpu3-silver-usr", status = NONE),
            ThermalMetric(value = 28.348001, type = SKIN, name = "fps-therm-monitor", status = NONE),
            ThermalMetric(value = 31.400002, type = GPU, name = "gpu0-usr", status = NONE),
            ThermalMetric(value = 31.000002, type = GPU, name = "gpu1-usr", status = NONE),
            ThermalMetric(value = 25.900002, type = BATTERY, name = "maxfg", status = NONE),
            ThermalMetric(value = 26.926, type = USB_PORT, name = "usbc-therm-monitor", status = NONE),
        )
    }

    @Test
    fun parseOtherOutput() {
        val output = """
IsStatusOverride: false
ThermalEventListeners:
	callbacks: 2
	killed: false
	broadcasts count: -1
ThermalStatusListeners:
	callbacks: 2
	killed: false
	broadcasts count: -1
Thermal Status: 0
Cached temperatures:
	Temperature{mValue=29.0, mType=2, mName=battery, mStatus=0}
	Temperature{mValue=59.2, mType=0, mName=CPU0, mStatus=0}
	Temperature{mValue=61.1, mType=0, mName=CPU1, mStatus=0}
	Temperature{mValue=62.3, mType=0, mName=CPU2, mStatus=0}
	Temperature{mValue=64.6, mType=0, mName=CPU3, mStatus=0}
	Temperature{mValue=65.8, mType=0, mName=CPU4, mStatus=0}
	Temperature{mValue=64.6, mType=0, mName=CPU5, mStatus=0}
	Temperature{mValue=52.1, mType=1, mName=GPU0, mStatus=0}
	Temperature{mValue=49.8, mType=1, mName=GPU1, mStatus=0}
	Temperature{mValue=50.9, mType=9, mName=nsp0, mStatus=0}
	Temperature{mValue=50.5, mType=9, mName=nsp1, mStatus=0}
	Temperature{mValue=34.984, mType=3, mName=skin, mStatus=0}
	Temperature{mValue=15.0, mType=8, mName=socd, mStatus=0}
	Temperature{mValue=4.285, mType=6, mName=vbat, mStatus=0}
HAL Ready: true
HAL connection:
	ThermalHAL 2.0 connected: yes
Current temperatures from HAL:
	Temperature{mValue=11.0, mType=8, mName=socd, mStatus=0}
	Temperature{mValue=29.0, mType=2, mName=battery, mStatus=0}
	Temperature{mValue=33.136, mType=3, mName=skin, mStatus=0}
	Temperature{mValue=34.7, mType=9, mName=nsp1, mStatus=0}
	Temperature{mValue=34.3, mType=1, mName=GPU0, mStatus=0}
	Temperature{mValue=34.3, mType=9, mName=nsp0, mStatus=0}
	Temperature{mValue=35.0, mType=0, mName=CPU5, mStatus=0}
	Temperature{mValue=4.384, mType=6, mName=vbat, mStatus=0}
	Temperature{mValue=34.3, mType=1, mName=GPU1, mStatus=0}
	Temperature{mValue=35.0, mType=0, mName=CPU4, mStatus=0}
	Temperature{mValue=36.2, mType=0, mName=CPU1, mStatus=0}
	Temperature{mValue=36.2, mType=0, mName=CPU3, mStatus=0}
	Temperature{mValue=35.4, mType=0, mName=CPU2, mStatus=0}
	Temperature{mValue=36.2, mType=0, mName=CPU0, mStatus=0}
Current cooling devices from HAL:
	CoolingDevice{mValue=0, mType=2, mName=thermal-cpufreq-4}
	CoolingDevice{mValue=0, mType=1, mName=battery}
	CoolingDevice{mValue=0, mType=2, mName=cpu-isolate5}
	CoolingDevice{mValue=0, mType=5, mName=cdsp_hw}
	CoolingDevice{mValue=0, mType=2, mName=cpu-isolate2}
	CoolingDevice{mValue=0, mType=3, mName=thermal-devfreq-0}
	CoolingDevice{mValue=0, mType=2, mName=cpu-isolate0}
	CoolingDevice{mValue=0, mType=2, mName=thermal-cpufreq-0}
	CoolingDevice{mValue=0, mType=2, mName=cpu-isolate4}
	CoolingDevice{mValue=0, mType=5, mName=cdsp}
	CoolingDevice{mValue=0, mType=2, mName=cpu-isolate3}
	CoolingDevice{mValue=0, mType=2, mName=cpu-isolate1}
Temperature static thresholds from HAL:
	TemperatureThreshold{mType=8, mName=socd, mHotThrottlingThresholds=[NaN, NaN, NaN, 90.0, NaN, NaN, 99.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}
	TemperatureThreshold{mType=2, mName=battery, mHotThrottlingThresholds=[NaN, NaN, NaN, 80.0, NaN, NaN, 90.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}
	TemperatureThreshold{mType=3, mName=skin, mHotThrottlingThresholds=[NaN, NaN, NaN, 40.0, NaN, NaN, 95.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}
	TemperatureThreshold{mType=9, mName=nsp1, mHotThrottlingThresholds=[NaN, NaN, NaN, 95.0, NaN, NaN, 115.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}
	TemperatureThreshold{mType=1, mName=GPU0, mHotThrottlingThresholds=[NaN, NaN, NaN, 95.0, NaN, NaN, 115.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}
	TemperatureThreshold{mType=9, mName=nsp0, mHotThrottlingThresholds=[NaN, NaN, NaN, 95.0, NaN, NaN, 115.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}
	TemperatureThreshold{mType=0, mName=CPU5, mHotThrottlingThresholds=[NaN, NaN, NaN, 95.0, NaN, NaN, 115.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}
	TemperatureThreshold{mType=6, mName=vbat, mHotThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN], mColdThrottlingThresholds=[NaN, NaN, NaN, 3.2, NaN, NaN, 3.0]}
	TemperatureThreshold{mType=1, mName=GPU1, mHotThrottlingThresholds=[NaN, NaN, NaN, 95.0, NaN, NaN, 115.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}
	TemperatureThreshold{mType=0, mName=CPU4, mHotThrottlingThresholds=[NaN, NaN, NaN, 95.0, NaN, NaN, 115.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}
	TemperatureThreshold{mType=0, mName=CPU1, mHotThrottlingThresholds=[NaN, NaN, NaN, 95.0, NaN, NaN, 115.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}
	TemperatureThreshold{mType=0, mName=CPU3, mHotThrottlingThresholds=[NaN, NaN, NaN, 95.0, NaN, NaN, 115.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}
	TemperatureThreshold{mType=0, mName=CPU2, mHotThrottlingThresholds=[NaN, NaN, NaN, 95.0, NaN, NaN, 115.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}
	TemperatureThreshold{mType=0, mName=CPU0, mHotThrottlingThresholds=[NaN, NaN, NaN, 95.0, NaN, NaN, 115.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}
        """.trimIndent()
        val metrics = parseThermalDumpsys(output)
        assertThat(metrics).containsExactlyInAnyOrder(
            ThermalMetric(value = 11.0, type = BCL_PERCENTAGE, name = "socd", status = NONE),
            ThermalMetric(value = 29.0, type = BATTERY, name = "battery", status = NONE),
            ThermalMetric(value = 33.136, type = SKIN, name = "skin", status = NONE),
            ThermalMetric(value = 34.7, type = NPU, name = "nsp1", status = NONE),
            ThermalMetric(value = 34.3, type = GPU, name = "GPU0", status = NONE),
            ThermalMetric(value = 34.3, type = NPU, name = "nsp0", status = NONE),
            ThermalMetric(value = 35.0, type = CPU, name = "CPU5", status = NONE),
            ThermalMetric(value = 4.384, type = BCL_VOLTAGE, name = "vbat", status = NONE),
            ThermalMetric(value = 34.3, type = GPU, name = "GPU1", status = NONE),
            ThermalMetric(value = 35.0, type = CPU, name = "CPU4", status = NONE),
            ThermalMetric(value = 36.2, type = CPU, name = "CPU1", status = NONE),
            ThermalMetric(value = 36.2, type = CPU, name = "CPU3", status = NONE),
            ThermalMetric(value = 35.4, type = CPU, name = "CPU2", status = NONE),
            ThermalMetric(value = 36.2, type = CPU, name = "CPU0", status = NONE),
        )
    }

    @Test
    fun parseSamsungOutput() {
        val output = """
IsStatusOverride: false
ThermalEventListeners:
	callbacks: 1
	killed: false
	broadcasts count: -1
ThermalStatusListeners:
	callbacks: 12
	killed: false
	broadcasts count: -1
Thermal Status: 0
Cached temperatures:
	Temperature{mValue=0.0, mType=2, mName=SUBBAT, mStatus=0}
	Temperature{mValue=55.0, mType=0, mName=AP, mStatus=0}
	Temperature{mValue=43.5, mType=5, mName=PA, mStatus=0}
	Temperature{mValue=32.0, mType=2, mName=BAT, mStatus=0}
	Temperature{mValue=29.7, mType=4, mName=USB, mStatus=0}
	Temperature{mValue=35.8, mType=3, mName=SKIN, mStatus=0}
HAL Ready: true
HAL connection:
	ThermalHAL AIDL 1  connected: yes
Current temperatures from HAL:
	Temperature{mValue=34.6, mType=0, mName=AP, mStatus=0}
	Temperature{mValue=25.2, mType=2, mName=BAT, mStatus=0}
	Temperature{mValue=30.0, mType=5, mName=PA, mStatus=0}
	Temperature{mValue=28.2, mType=3, mName=SKIN, mStatus=0}
	Temperature{mValue=0.0, mType=2, mName=SUBBAT, mStatus=0}
	Temperature{mValue=25.8, mType=4, mName=USB, mStatus=0}
Current cooling devices from HAL:
Temperature static thresholds from HAL:
	TemperatureThreshold{mType=2, mName=BAT, mHotThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, 55.0, 85.0], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}
	TemperatureThreshold{mType=3, mName=SKIN, mHotThrottlingThresholds=[36.0, 38.0, 40.0, 42.0, 45.0, NaN, NaN], mColdThrottlingThresholds=[NaN, NaN, NaN, NaN, NaN, NaN, NaN]}
        """.trimIndent()
        val metrics = parseThermalDumpsys(output)
        assertThat(metrics).containsExactlyInAnyOrder(
            ThermalMetric(value = 34.6, type = CPU, name = "AP", status = NONE),
            ThermalMetric(value = 25.2, type = BATTERY, name = "BAT", status = NONE),
            ThermalMetric(value = 30.0, type = POWER_AMPLIFIER, name = "PA", status = NONE),
            ThermalMetric(value = 28.2, type = SKIN, name = "SKIN", status = NONE),
            ThermalMetric(value = 0.0, type = BATTERY, name = "SUBBAT", status = NONE),
            ThermalMetric(value = 25.8, type = USB_PORT, name = "USB", status = NONE),
        )
    }

    @Test
    fun parseOtherOtherOutput() {
        val output = """
IsStatusOverride: false
ThermalEventListeners:
	callbacks: 2
	killed: false
	broadcasts count: -1
ThermalStatusListeners:
	callbacks: 6
	killed: false
	broadcasts count: -1
Thermal Status: 2
Cached temperatures:
	Temperature{mValue=25.0, mType=2, mName=battery, mStatus=0}
	Temperature{mValue=38.5, mType=1, mName=gpu, mStatus=0}
	Temperature{mValue=44.3, mType=0, mName=CPU0, mStatus=0}
	Temperature{mValue=41.7, mType=0, mName=CPU1, mStatus=0}
	Temperature{mValue=41.1, mType=0, mName=CPU2, mStatus=0}
	Temperature{mValue=42.7, mType=0, mName=CPU3, mStatus=0}
	Temperature{mValue=43.3, mType=0, mName=CPU4, mStatus=0}
	Temperature{mValue=46.2, mType=0, mName=CPU5, mStatus=0}
	Temperature{mValue=47.8, mType=0, mName=CPU6, mStatus=0}
	Temperature{mValue=44.3, mType=0, mName=CPU7, mStatus=0}
	Temperature{mValue=45.05, mType=3, mName=skin, mStatus=2}
HAL Ready: true
HAL connection:
	ThermalHAL 2.0 connected: yes
Current temperatures from HAL:
	Temperature{mValue=43.8, mType=2, mName=battery, mStatus=0}
	Temperature{mValue=44.637, mType=3, mName=skin, mStatus=2}
	Temperature{mValue=45.9, mType=1, mName=gpu, mStatus=0}
	Temperature{mValue=47.8, mType=0, mName=CPU7, mStatus=0}
	Temperature{mValue=48.2, mType=0, mName=CPU6, mStatus=0}
	Temperature{mValue=47.8, mType=0, mName=CPU5, mStatus=0}
	Temperature{mValue=47.5, mType=0, mName=CPU4, mStatus=0}
	Temperature{mValue=47.2, mType=0, mName=CPU1, mStatus=0}
	Temperature{mValue=47.5, mType=0, mName=CPU3, mStatus=0}
	Temperature{mValue=46.2, mType=0, mName=CPU2, mStatus=0}
	Temperature{mValue=47.8, mType=0, mName=CPU0, mStatus=0}
Current cooling devices from HAL:
	CoolingDevice{mValue=11, mType=2, mName=thermal-cpufreq-6}
	CoolingDevice{mValue=6, mType=2, mName=thermal-cpufreq-4}
	CoolingDevice{mValue=6, mType=2, mName=thermal-cpufreq-2}
	CoolingDevice{mValue=6, mType=2, mName=thermal-cpufreq-0}
	CoolingDevice{mValue=0, mType=3, mName=thermal-devfreq-0}
	CoolingDevice{mValue=11, mType=2, mName=thermal-cpufreq-7}
	CoolingDevice{mValue=6, mType=2, mName=thermal-cpufreq-5}
	CoolingDevice{mValue=6, mType=2, mName=thermal-cpufreq-3}
	CoolingDevice{mValue=6, mType=2, mName=thermal-cpufreq-1}
Temperature static thresholds from HAL:
	{.type = BATTERY, .name = battery, .hotThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, 65.0, 70.0], .coldThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, NaN], .vrThrottlingThreshold = NaN}
	{.type = SKIN, .name = skin, .hotThrottlingThresholds = [25.0, 35.0, 45.0, 52.0, 55.0, 58.0, 60.0], .coldThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, NaN], .vrThrottlingThreshold = NaN}
	{.type = GPU, .name = gpu, .hotThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, 125.0], .coldThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, NaN], .vrThrottlingThreshold = NaN}
	{.type = CPU, .name = CPU7, .hotThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, 125.0], .coldThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, NaN], .vrThrottlingThreshold = NaN}
	{.type = CPU, .name = CPU6, .hotThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, 125.0], .coldThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, NaN], .vrThrottlingThreshold = NaN}
	{.type = CPU, .name = CPU5, .hotThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, 125.0], .coldThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, NaN], .vrThrottlingThreshold = NaN}
	{.type = CPU, .name = CPU4, .hotThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, 125.0], .coldThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, NaN], .vrThrottlingThreshold = NaN}
	{.type = CPU, .name = CPU1, .hotThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, 125.0], .coldThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, NaN], .vrThrottlingThreshold = NaN}
	{.type = CPU, .name = CPU3, .hotThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, 125.0], .coldThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, NaN], .vrThrottlingThreshold = NaN}
	{.type = CPU, .name = CPU2, .hotThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, 125.0], .coldThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, NaN], .vrThrottlingThreshold = NaN}
	{.type = CPU, .name = CPU0, .hotThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, 125.0], .coldThrottlingThresholds = [NaN, NaN, NaN, NaN, NaN, NaN, NaN], .vrThrottlingThreshold = NaN}
        """.trimIndent()
        val metrics = parseThermalDumpsys(output)
        assertThat(metrics).containsExactlyInAnyOrder(
            ThermalMetric(value = 43.8, type = BATTERY, name = "battery", status = NONE),
            ThermalMetric(value = 44.637, type = SKIN, name = "skin", status = MODERATE),
            ThermalMetric(value = 45.9, type = GPU, name = "gpu", status = NONE),
            ThermalMetric(value = 47.8, type = CPU, name = "CPU7", status = NONE),
            ThermalMetric(value = 48.2, type = CPU, name = "CPU6", status = NONE),
            ThermalMetric(value = 47.8, type = CPU, name = "CPU5", status = NONE),
            ThermalMetric(value = 47.5, type = CPU, name = "CPU4", status = NONE),
            ThermalMetric(value = 47.2, type = CPU, name = "CPU1", status = NONE),
            ThermalMetric(value = 47.5, type = CPU, name = "CPU3", status = NONE),
            ThermalMetric(value = 46.2, type = CPU, name = "CPU2", status = NONE),
            ThermalMetric(value = 47.8, type = CPU, name = "CPU0", status = NONE),
        )
    }

    @Test
    fun parseMissingValue_ignoresRecord() {
        val output = """
            Thermal Status: 0
            Current temperatures from HAL:
            Temperature{mValue=31.7, mType=0, mName=cpu0-gold-usr}
            Temperature{mValue=32.300003, mType=0, mName=cpu0-silver-usr, mStatus=0}
        """.trimIndent()
        val metrics = parseThermalDumpsys(output)
        assertThat(metrics).containsExactlyInAnyOrder(
            ThermalMetric(value = 32.300003, type = CPU, name = "cpu0-silver-usr", status = NONE),
        )
    }

    @Test
    fun parseInvalidValue_ignoresRecord() {
        val output = """
            Thermal Status: 0
            Current temperatures from HAL:
            Temperature{mValue=s, mType=0, mName=cpu0-gold-usr, mStatus=0}
            Temperature{mValue=32.300003, mType=0, mName=cpu0-silver-usr, mStatus=0}
        """.trimIndent()
        val metrics = parseThermalDumpsys(output)
        assertThat(metrics).containsExactlyInAnyOrder(
            ThermalMetric(value = 32.300003, type = CPU, name = "cpu0-silver-usr", status = NONE),
        )
    }

    @Test
    fun parseEmulator_spaceInName() {
        val output = """
            ThermalEventListeners:
                callbacks: 1
                killed: false
                broadcasts count: -1
            ThermalStatusListeners:
                callbacks: 1
                killed: false
                broadcasts count: -1
            Thermal Status: 0
            Cached temperatures:
                Temperature{mValue=30.8, mType=3, mName=test temperature sensor, mStatus=0}
            HAL Ready: true
            HAL connection:
                ThermalHAL 2.0 connected: yes
            Current temperatures from HAL:
                Temperature{mValue=30.8, mType=3, mName=test temperature sensor, mStatus=0}
            Current cooling devices from HAL:
                CoolingDevice{mValue=100, mType=0, mName=test cooling device}
        """.trimIndent()
        val metrics = parseThermalDumpsys(output)
        assertThat(metrics).containsExactlyInAnyOrder(
            ThermalMetric(value = 30.8, type = SKIN, name = "test temperature sensor", status = NONE),
        )
    }
}
