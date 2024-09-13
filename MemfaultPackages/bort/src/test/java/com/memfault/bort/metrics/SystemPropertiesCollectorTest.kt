package com.memfault.bort.metrics

import android.app.Application
import android.telephony.TelephonyManager
import com.memfault.bort.DumpsterClient
import com.memfault.bort.settings.MetricsSettings
import com.memfault.bort.settings.RateLimitingSettings
import com.memfault.bort.time.boxed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class SystemPropertiesCollectorTest {
    @Test
    fun collectsAndAddsProperties() = runTest {
        val store: DevicePropertiesStore = mockk(relaxed = true)
        val settings = object : MetricsSettings {
            override val dataSourceEnabled = true
            override val dailyHeartbeatEnabled: Boolean = false
            override val sessionsRateLimitingSettings: RateLimitingSettings = RateLimitingSettings(
                defaultCapacity = 11,
                defaultPeriod = 24.hours.boxed(),
                maxBuckets = 1,
            )
            override val collectionInterval = Duration.ZERO
            override val systemProperties = listOf("a", "c", "1", "doesntexist", "1.2", "1.3", "true", "notypelisted")
            override val appVersions = emptyList<String>()
            override val maxNumAppVersions: Int = 50
            override val reporterCollectionInterval = Duration.ZERO
            override val propertiesUseMetricService: Boolean = true
            override val cachePackageManagerReport: Boolean = true
            override val recordImei: Boolean = true
            override val operationalCrashesExclusions: List<String> = emptyList()
            override val pollingInterval: Duration = 15.minutes
        }
        val deviceImei = "12345678987654321"
        val telephony: TelephonyManager = mockk {
            every { imei } answers { deviceImei }
        }
        val application: Application = mockk {
            every { getSystemService(TelephonyManager::class.java) } answers { telephony }
        }
        val dumpsterClient: DumpsterClient = mockk {
            coEvery { getprop() } answers {
                mapOf(
                    "a" to "a",
                    "b" to "b",
                    "c" to "c",
                    "0" to "0",
                    "1" to "1",
                    "1.2" to "1.2",
                    "1.3" to "1.3",
                    "false" to "0",
                    "true" to "true",
                    "vendor.memfault.bort.version.sdk" to "4.0",
                    "vendor.memfault.bort.version.patch" to "4.1",
                    "notypelisted" to "notype",
                )
            }
            coEvery { getpropTypes() } answers {
                mapOf(
                    "a" to "string",
                    "b" to "string",
                    "c" to "enum",
                    "0" to "int",
                    "1" to "int",
                    "1.2" to "double",
                    "1.3" to "somethingelse",
                    "false" to "bool",
                    "true" to "bool",
                    "sysprop.vendor.memfault.bort.version.sdk" to "string",
                    "sysprop.vendor.memfault.bort.version.patch" to "string",
                )
            }
        }
        val collector = SystemPropertiesCollector(
            settings = settings,
            dumpsterClient = dumpsterClient,
            application = application,
        )
        collector.collect()?.let { collector.record(it, store) }
        coVerify(exactly = 1) {
            store.upsert(name = "sysprop.a", value = "a", internal = false)
            store.upsert(name = "sysprop.c", value = "c", internal = false)
            store.upsert(name = "sysprop.1", value = 1L, internal = false)
            store.upsert(name = "sysprop.1.2", value = 1.2, internal = false)
            store.upsert(name = "sysprop.1.3", value = "1.3", internal = false)
            store.upsert(name = "sysprop.true", value = true, internal = false)
            store.upsert(
                name = "sysprop.vendor.memfault.bort.version.sdk",
                value = "4.0",
                internal = true,
            )
            store.upsert(
                name = "sysprop.vendor.memfault.bort.version.patch",
                value = "4.1",
                internal = true,
            )
            store.upsert(name = "sysprop.notypelisted", value = "notype", internal = false)
            store.upsert(name = "phone.imei", value = deviceImei)
        }
    }
}
