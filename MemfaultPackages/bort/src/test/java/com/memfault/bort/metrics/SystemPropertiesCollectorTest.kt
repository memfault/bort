package com.memfault.bort.metrics

import com.memfault.bort.settings.MetricsSettings
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SystemPropertiesCollectorTest {
    @Test
    fun collectsAndAddsProperties() {
        val store: DevicePropertiesStore = mockk(relaxed = true)
        val settings = object : MetricsSettings {
            override val dataSourceEnabled = true
            override val collectionInterval = Duration.ZERO
            override val systemProperties = listOf("a", "c", "1", "doesntexist", "1.2", "1.3", "true", "notypelisted")
            override val appVersions = emptyList<String>()
            override val maxNumAppVersions: Int = 50
            override val reporterCollectionInterval = Duration.ZERO
            override val propertiesUseMetricService: Boolean = true
        }
        val collector = SystemPropertiesCollector(
            settings = settings,
            dumpsterClient = mockk(relaxed = true)
        )
        runBlocking {
            collector.updateSystemPropertiesWith(
                systemProperties = mapOf(
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
                ),
                systemPropertyTypes = mapOf(
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
                ),
                store,
            )
        }
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
                internal = true
            )
            store.upsert(
                name = "sysprop.vendor.memfault.bort.version.patch",
                value = "4.1",
                internal = true
            )
            store.upsert(name = "sysprop.notypelisted", value = "notype", internal = false)
        }
    }
}
