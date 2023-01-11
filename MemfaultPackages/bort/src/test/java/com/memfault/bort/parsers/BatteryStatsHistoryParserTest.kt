package com.memfault.bort.parsers

import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BatteryStatsHistoryParserTest {
    private val historyLogger: BatteryStatsHistoryMetricLogger = mockk(relaxed = true)

    private fun createFile(content: String): File {
        val file = File.createTempFile("batterystats", ".txt")
        file.writeText(content)
        return file
    }

    private val BATTERYSTATS_FILE = """
        9,hsp,1,0,"Abort:Pending Wakeup Sources: 200f000.qcom,spmi:qcom,pm660@0:qpnp,fg battery qcom-step-chg "
        9,hsp,70,10103,"com.android.launcher3"
        9,hsp,71,10104,"com.memfault.bort"
        9,h,123:TIME:1000000
        9,h,1:START
        9,h,0,+r,wr=1,Bl=100,+S,Sb=0,+W,+Wr,+Ws,+Ww,Wss=0,+g,+bles,+Pr,+Psc,+a,Bh=g,di=light,Bt=213,+Efg=71,+Elw=70
        9,h,200000,-r,-S,Sb=3,-W,-Wr,-Ws,-Ww,Wss=2,-g,-bles,-Pr,-Psc,-a,Bh=f,di=full,Bt=263,+Etp=70,+Efg=70,+Eal=70
        9,h,3,-Efg=71,-Etp=71,wr=,Bl=x,+W
        9,h,0:SHUTDOWN
        9,h,123:RESET:TIME:2000000
        9,h,800000,Bl=90,Bt=250,-Efg=70,-Etp=70,-Elw=70
    """.trimIndent()

    @Test
    fun testParser() {
        val parser = BatteryStatsHistoryParser(createFile(BATTERYSTATS_FILE), historyLogger)
        var time: Long = 0
        runTest {
            parser.parseToCustomMetrics()
            coVerify {
                // TIME:1000000
                time = 1000000
                // 9,h,1:START
                time += 1
                historyLogger.start(time)
                // h,0
                // +r
                historyLogger.cpuRunning(time, true)
                // wr=1
                // not handled
                // Bl=100
                historyLogger.batteryLevel(time, 100)
                // +S
                historyLogger.screenOn(time, true)
                // Sb=0
                historyLogger.screenBrightness(time, ScreenBrightness.Dark)
                // +W
                historyLogger.wifiOn(time, true)
                // +Wr
                historyLogger.wifiRadio(time, true)
                // +Ws
                historyLogger.wifiScan(time, true)
                // +Ww
                historyLogger.wifiRunning(time, true)
                // Wss=0
                historyLogger.wifiSignalStrength(time, SignalStrength.NoSignal)
                // +g
                historyLogger.gpsOn(time, true)
                // +bles
                historyLogger.bleScanning(time, true)
                // +Pr
                historyLogger.phoneRadio(time, true)
                // +Psc
                historyLogger.phoneScanning(time, true)
                // +a
                historyLogger.audio(time, true)
                // Bh=g
                historyLogger.batteryHealth(time, BatteryHealth.Good)
                // di=light
                historyLogger.doze(time, DozeState.Light)
                // Bt=213
                historyLogger.batteryTemp(time, 213)
                // +Efg=71
                historyLogger.foreground(time, "com.memfault.bort")
                // +Elw=70
                historyLogger.longwake(time, "com.android.launcher3")
                // h,200000
                time += 200000
                // -r
                historyLogger.cpuRunning(time, false)
                // -S
                historyLogger.screenOn(time, false)
                // Sb=3
                historyLogger.screenBrightness(time, ScreenBrightness.Light)
                // -W
                historyLogger.wifiOn(time, false)
                // -Wr
                historyLogger.wifiRadio(time, false)
                // -Ws
                historyLogger.wifiScan(time, false)
                // -Ww
                historyLogger.wifiRunning(time, false)
                // Wss=2
                historyLogger.wifiSignalStrength(time, SignalStrength.Moderate)
                // -g
                historyLogger.gpsOn(time, false)
                // -bles
                historyLogger.bleScanning(time, false)
                // -Pr
                historyLogger.phoneRadio(time, false)
                // -Psc
                historyLogger.phoneScanning(time, false)
                // -a
                historyLogger.audio(time, false)
                // Bh=f
                historyLogger.batteryHealth(time, BatteryHealth.Failure)
                // di=full
                historyLogger.doze(time, DozeState.Full)
                // Bt=263
                historyLogger.batteryTemp(time, 263)
                // +Etp=70
                historyLogger.topApp(time, "com.android.launcher3")
                // +Efg=70
                historyLogger.foreground(time, "com.android.launcher3")
                // +Eal=70
                historyLogger.alarm(time, "com.android.launcher3")
                // h,3
                time += 3
                // -Efg=71
                // ignored, because 71 was not foreground
                // -Etp=71
                // ignored, because 71 was not top app
                // wr=
                // ignored: invalid
                // Bl=x
                // ignored: invalid
                // +W
                historyLogger.wifiOn(time, true)
                // h,0:SHUTDOWN
                historyLogger.shutdown(time)
                // h,123
                time += 123
                // RESET:TIME:2000000
                time = 2000000
                // h,800000
                time += 800000
                // Bl=90
                historyLogger.batteryLevel(time, 90)
                // Bt=250
                historyLogger.batteryTemp(time, 250)
                // -Efg=70
                historyLogger.foreground(time, null)
                // -Etp=70
                historyLogger.topApp(time, null)
                // -Elw=70
                historyLogger.longwake(time, null)
            }
            // Confirm no other/unexpected metrics logged.
            confirmVerified(historyLogger)
        }
    }
}
