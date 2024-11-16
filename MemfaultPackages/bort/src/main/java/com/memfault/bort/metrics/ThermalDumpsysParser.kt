package com.memfault.bort.metrics

import com.memfault.bort.process.ProcessExecutor
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Example output:
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
 */
internal fun parseThermalDumpsys(dumpsysOutput: String): List<ThermalMetric> {
    var inCurrentTempBlock = false
    val metrics = mutableListOf<ThermalMetric>()
    dumpsysOutput.lines().forEach { line ->
        if (line.trim().startsWith("Current temperatures from HAL:")) {
            inCurrentTempBlock = true
        } else if (inCurrentTempBlock && line.trim().startsWith("Temperature{")) {
            TEMPERATURE_REGEX.find(line)?.let { match ->
                try {
                    metrics.add(
                        ThermalMetric(
                            value = match.groupValues[POS_VALUE].toDouble(),
                            type = TemperatureType.fromInt(match.groupValues[POS_TYPE].toInt()),
                            name = match.groupValues[POS_NAME],
                            status = ThrottlingSeverity.fromInt(match.groupValues[POS_STATUS].toInt()),
                        ),
                    )
                } catch (e: NumberFormatException) {
                    Logger.w("Error parsing temperature ('$line')", e)
                } catch (e: ArrayIndexOutOfBoundsException) {
                    Logger.w("Error parsing temperature ('$line')", e)
                }
            }
        } else {
            inCurrentTempBlock = false
        }
    }
    return metrics
}

private val TEMPERATURE_REGEX =
    Regex("Temperature\\{mValue=(\\d+?\\.\\d+?), mType=(\\d+), mName=(.+?), mStatus=(\\d+)\\}")
private val POS_VALUE = 1
private val POS_TYPE = 2
private val POS_NAME = 3
private val POS_STATUS = 4

fun interface CollectThermalDumpsys : suspend () -> List<ThermalMetric>

@ContributesBinding(SingletonComponent::class)
class RealCollectThermalDumpsys @Inject constructor(
    private val processExecutor: ProcessExecutor,
) : CollectThermalDumpsys {
    override suspend fun invoke(): List<ThermalMetric> {
        val thermalDump = processExecutor.execute(listOf("dumpsys", "thermalservice")) { inputStream ->
            inputStream.bufferedReader().use { bufferedReader -> bufferedReader.readText() }
        }
        if (thermalDump == null) {
            Logger.w("thermal dumpsys is null")
            return emptyList()
        }
        return parseThermalDumpsys(thermalDump)
    }
}
