package com.memfault.bort.parsers

import com.memfault.bort.parsers.BatteryStatsSummaryParser.BatteryState
import com.memfault.bort.parsers.BatteryStatsSummaryParser.BatteryStatsSummary
import com.memfault.bort.parsers.BatteryStatsSummaryParser.DischargeData
import com.memfault.bort.parsers.BatteryStatsSummaryParser.PowerUseItemData
import com.memfault.bort.parsers.BatteryStatsSummaryParser.PowerUseSummary
import com.memfault.bort.time.AbsoluteTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class BatteryStatsSummaryParserTest {
    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private val timeMs: Long = 123456789
    private val timeProvider = { AbsoluteTime(Instant.ofEpochMilli(timeMs)) }
    private val parser = BatteryStatsSummaryParser(timeProvider)

    @Test
    fun testParser() {
        val file = tempFolder.newFile()
        file.writeText(CHECKIN_CONTENT)
        val result = parser.parse(file)
        assertEquals(
            BatteryStatsSummary(
                batteryState = BatteryState(
                    batteryRealtimeMs = 43722106,
                    startClockTimeMs = 1661068748155,
                    estimatedBatteryCapacity = 1000,
                    screenOffRealtimeMs = 43699893,
                ),
                dischargeData = DischargeData(totalMaH = 0, totalMaHScreenOff = 0),
                powerUseItemData = setOf(
                    PowerUseItemData(name = "unknown", totalPowerMaH = 156.0 + 32),
                    PowerUseItemData(name = "cell", totalPowerMaH = 1.22),
                    PowerUseItemData(name = "idle", totalPowerMaH = 1.21),
                    PowerUseItemData(name = "android", totalPowerMaH = 1.21 + 0.0000270 + 0.159),
                    PowerUseItemData(name = "screen", totalPowerMaH = 0.000856),
                    PowerUseItemData(name = "com.memfault.smartfridge.bort", totalPowerMaH = 0.000671),
                    PowerUseItemData(name = "com.android.systemui", totalPowerMaH = 0.0000741),
                    PowerUseItemData(name = "com.memfault.smartfridge.bort.ota", totalPowerMaH = 0.0000313),
                    PowerUseItemData(name = "com.android.providers.calendar", totalPowerMaH = 0.00000067),
                    PowerUseItemData(name = "bluetooth", totalPowerMaH = 0.00000053),
                ),
                timestampMs = timeMs,
                powerUseSummary = PowerUseSummary(
                    originalBatteryCapacity = 3900,
                    computedCapacityMah = 3243,
                    minCapacityMah = 3237,
                    maxCapacityMah = 3354,
                ),
            ),
            result,
        )
    }

    @Test
    fun invalidFile() {
        val file = tempFolder.newFile()
        file.writeText(INVALID_CONTENT)
        val result = parser.parse(file)
        assertEquals(
            null,
            result,
        )
    }

    @Test
    fun emulatorNoBatteryFile() {
        val file = tempFolder.newFile()
        file.writeText(NO_BATTERY_CONTENT)
        val result = parser.parse(file)
        assertEquals(
            BatteryStatsSummary(
                batteryState = BatteryState(
                    batteryRealtimeMs = 0,
                    startClockTimeMs = 1684814487593,
                    screenOffRealtimeMs = 0,
                    estimatedBatteryCapacity = 1000,
                ),
                dischargeData = DischargeData(
                    totalMaH = 0,
                    totalMaHScreenOff = 0,
                ),
                powerUseItemData = emptySet(),
                timestampMs = 123456789,
                powerUseSummary = PowerUseSummary(
                    originalBatteryCapacity = 0,
                    computedCapacityMah = 0,
                    minCapacityMah = 0,
                    maxCapacityMah = 0,
                ),
            ),

            result,
        )
    }

    companion object {
        private val INVALID_CONTENT = """
,,,
,
        """.trimIndent()

        private val CHECKIN_CONTENT = """
9,0,i,vers,35,186,QQ2A.200405.005,QQ2A.200405.005
9,0,i,uid,1000,com.android.dynsystem
9,0,i,uid,1000,android
9,0,i,uid,1000,com.android.google.gce.gceservice
9,0,i,uid,1000,com.android.wallpaperbackup
9,0,i,uid,1000,com.android.location.fused
9,0,i,uid,1001,com.android.providers.telephony
9,0,i,uid,1001,com.android.phone
9,0,i,uid,1002,com.android.bluetooth
9,0,i,uid,1027,com.android.nfc
9,0,i,uid,1068,com.android.se
9,0,i,uid,1073,com.android.networkstack
9,0,i,uid,1073,com.android.networkstack.permissionconfig
9,0,i,uid,2000,com.android.shell
9,0,i,uid,10030,android.auto_generated_rro_product__
9,0,i,uid,10039,com.memfault.smartfridge.bort.ota
9,0,i,uid,10041,com.android.providers.userdictionary
9,0,i,uid,10041,com.android.providers.contacts
9,0,i,uid,10042,com.memfault.smartfridge.bort
9,0,i,uid,10043,com.android.permissioncontroller
9,0,i,uid,10044,com.android.vpndialogs
9,0,i,uid,10045,android.ext.services
9,0,i,uid,10046,com.android.providers.calendar
9,0,i,uid,10047,com.android.statementservice
9,0,i,uid,10060,com.android.printspooler
9,0,i,uid,10075,com.android.settings.intelligence
9,0,i,uid,10076,com.android.onetimeinitializer
9,0,i,uid,10077,com.android.storagemanager
9,0,i,uid,10078,com.android.contacts
9,0,i,uid,10079,com.android.provision
9,0,i,uid,10080,com.android.carrierconfig
9,0,i,uid,10081,com.android.launcher3
9,0,i,uid,10082,com.android.wallpapercropper
9,0,i,uid,10083,com.android.systemui
9,0,i,uid,10096,com.android.deskclock
9,0,i,dsd,672165,68,s-,p-,i-
9,0,i,dsd,199088,70,s-,p-,i-
9,0,i,dsd,199088,70,s-,p-,i-
9,0,i,dsd,89110,75,s-,p-,i-
9,0,i,dsd,89110,75,s-,p-,i-
9,0,i,dtr,15400028000
9,0,l,bt,2,43722106,43722106,47075263,47075263,1661068748155,43699893,43699893,1000,4000000,4000000,0
9,0,l,pws,3900,3243,3237,3354
9,0,l,gn,0,0,0,0,0,0,0,0,0,0
9,0,l,gwfl,0,0,0,0,0
9,0,l,m,22213,0,0,43699892,152811,0,19993,0,3,0,0,0,0,15,0,0,0,0,0,0,0
9,0,l,br,6217,0,15996,0,0
9,0,l,sgt,0,840243,2620801,2760844,37500218
9,0,l,sst,0
9,0,l,sgc,0,15,30,34,19
9,0,l,dct,0,0,0,0,0,0,0,0,0,0,0,0,0,43722106,0,0,0,0,0,0,0,0,0
9,0,l,dcc,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
9,0,l,wst,0,0,0,0,0,0,0,0
9,0,l,wsc,0,0,0,0,0,0,0,0
9,0,l,wsst,0,0,0,0,0,0,0,0,0,0,0,0,0
9,0,l,wssc,0,0,0,0,0,0,0,0,0,0,0,0,0
9,0,l,wsgt,0,0,0,0,0
9,0,l,wsgc,0,0,0,0,0
9,0,l,wmct,0,0
9,0,l,dc,16,17,0,17,0,0,0,0,0,0
9,0,l,kwl,"ApmAudio",0,0,-1,-1
9,0,l,kwl,"SensorsHAL_WAKEUP",0,0,-1,-1
9,0,l,rpm,"DefaultEntity.Sleep",0,0
9,0,l,pws,1000,3.65,160,170
9,0,l,pwi,unacc,156,1,0,0
9,0,l,pwi,cell,1.22,1,0,0
9,0,l,pwi,idle,1.21,1,0,0
9,1001,l,pwi,uid,1.21,1,0,0
9,1001,l,pwi,scrn,0.000856,1,0,0
9,9999,l,pwi,uid,0.159,1,0,0
9,10042,l,pwi,uid,0.000671,0,0,0.0000104
9,10083,l,pwi,uid,0.0000741,1,0,0
9,10039,l,pwi,uid,0.0000313,0,0,0.00000048
9,1000,l,pwi,uid,0.0000270,1,0,0
9,10046,l,pwi,uid,0.00000067,1,0,0
9,10046,l,pwi,blue,0.00000053,0,0,0.00000001
9,50158,l,pwi,uid,32,0,0,0
9,0,l,pwi,uid,0,1,0,0
9,0,l,cpu,1,11,0
9,0,l,pr,"vsock_logcat",30,180,0,0,0,0
9,0,l,pr,"kworker/u4:6",0,40,0,0,0,0
9,0,l,pr,"logcat",200,150,0,0,0,0
9,1000,l,awl,1236,0
9,1000,l,wl,ActivityManager-Sleep,0,f,0,0,0,0,0,p,0,0,0,0,0,bp,0,0,0,0,0,w,0,0,0,0
9,1000,l,wl,*job*/android/com.android.server.net.watchlist.ReportWatchlistJobService,0,f,0,0,0,0,1,p,1,0,4,4,0,bp,0,0,0,0,0,w,0,0,0,0
9,1000,l,wl,*dexopt*,0,f,0,0,0,0,0,p,0,0,0,0,0,bp,0,0,0,0,0,w,0,0,0,0
9,1001,l,cpu,85,187,0
9,1001,l,pr,"com.android.phone",2090,1980,0,0,0,0
9,1001,l,pr,"libcuttlefish-rild",90,110,0,0,0,0
9,1001,l,pr,"*wakelock*",38,134,0,0,0,0
9,1001,l,apk,0,com.android.phone,com.android.internal.telephony.CellularNetworkService,0,0,3
9,10096,l,wl,*dexopt*,0,f,0,0,0,0,0,p,0,0,0,0,0,bp,0,0,0,0,0,w,0,0,0,0
9,10096,l,wl,AlarmAlertWakeLock,0,f,0,0,0,0,0,p,0,0,0,0,0,bp,0,0,0,0,0,w,0,0,0,0
9,50039,l,cpu,1,0,0
9,50042,l,cpu,1,0,0
        """.trim().trimIndent()
    }

    private val NO_BATTERY_CONTENT = """
9,0,i,vers,35,186,QQ2A.200405.005,QQ2A.200405.005
9,0,i,uid,1000,com.android.dynsystem
9,0,i,uid,1000,android
9,0,i,uid,1000,com.android.google.gce.gceservice
9,0,i,uid,1000,com.memfault.usagereporter
9,0,i,uid,1000,com.android.providers.settings
9,0,i,uid,1000,com.android.inputdevices
9,0,i,uid,10096,com.android.inputmethod.latin
9,0,l,bt,2,0,0,245194752,245194752,1684814487593,0,0,1000,4000000,4000000,0
9,0,l,pws,0,0,0,0
9,0,l,gn,0,0,0,0,0,0,0,0,0,0
9,0,l,gwfl,0,0,0,0,0
9,0,l,m,0,0,0,0,0,0,0,0,3,0,0,0,0,0,0,0,0,0,0,0,0
9,0,l,br,0,0,0,0,0
9,0,l,sgt,0,0,0,0,0
9,0,l,sst,0
9,0,l,sgc,0,0,0,0,0
9,0,l,dct,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
9,0,l,dcc,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
9,0,l,wst,0,0,0,0,0,0,0,0
9,0,l,wsc,0,0,0,0,0,0,0,0
9,0,l,wsst,0,0,0,0,0,0,0,0,0,0,0,0,0
9,0,l,wssc,0,0,0,0,0,0,0,0,0,0,0,0,0
9,0,l,wsgt,0,0,0,0,0
9,0,l,wsgc,0,0,0,0,0
9,0,l,wmct,0,0
9,0,l,dc,0,0,0,0,0,0,0,0,0,0
9,1000,l,awl,0,0
9,1000,l,wl,ActivityManager-Sleep,0,f,0,0,0,0,0,p,0,0,0,0,0,bp,0,0,0,0,0,w,0,0,0,0
9,1000,l,wl,*dexopt*,0,f,0,0,0,0,0,p,0,0,0,0,0,bp,0,0,0,0,0,w,0,0,0,0
9,1002,l,wl,*dexopt*,0,f,0,0,0,0,0,p,0,0,0,0,0,bp,0,0,0,0,0,w,0,0,0,0
9,1002,l,wl,bluetooth_timer,0,f,0,0,0,0,0,p,0,0,0,0,0,bp,0,0,0,0,0,w,0,0,0,0
    """.trim().trimIndent()
}
