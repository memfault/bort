package com.memfault.bort.metrics

import com.memfault.bort.PackageManagerClient
import com.memfault.bort.parsers.Package
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.settings.MetricsSettings
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class AppVersionsCollectorTest {
    val store: DevicePropertiesStore = mockk(relaxed = true)
    var report = PackageManagerReport()
    var packages = emptyList<String>()
    val pmClient: PackageManagerClient = mockk(relaxed = true) {
        coEvery { getPackageManagerReport() } answers { report }
    }
    var maxNumberAppVersions = 50
    val collector = AppVersionsCollector(
        devicePropertiesStore = store,
        metricsSettings = object : MetricsSettings {
            override val dataSourceEnabled: Boolean = false
            override val collectionInterval: Duration = ZERO
            override val systemProperties: List<String> = emptyList()
            override val appVersions: List<String>
                get() = packages
            override val maxNumAppVersions: Int
                get() = maxNumberAppVersions
            override val reporterCollectionInterval: Duration = ZERO
        },
        packageManagerClient = pmClient,
    )

    @Test fun addsAppVersionsWithWildcard() {
        packages = listOf("a.b.c", "b.*")
        report = PackageManagerReport(
            listOf(
                Package(id = ABC_ID, versionName = ABC_VERSION),
                Package(id = ABCD_ID, versionName = ABCD_VERSION),
                Package(id = BCD_ID, versionName = BCD_VERSION),
                Package(id = BCE_ID, versionName = BCE_VERSION),
            )
        )
        runBlocking {
            collector.updateAppVersions()
            coVerifyAll {
                store.upsert(
                    name = "version.$ABC_ID",
                    value = ABC_VERSION,
                    internal = false,
                )
                store.upsert(
                    name = "version.$BCD_ID",
                    value = BCD_VERSION,
                    internal = false,
                )
                store.upsert(
                    name = "version.$BCE_ID",
                    value = BCE_VERSION,
                    internal = false,
                )
            }
        }
    }

    @Test fun packageManagerNotCalledIfNoRegexes() {
        packages = emptyList()
        runBlocking {
            collector.updateAppVersions()
            verify { pmClient wasNot Called }
            verify { store wasNot Called }
        }
    }

    @Test fun maxNumVersionsCollected() {
        packages = listOf("*")
        maxNumberAppVersions = 3
        report = PackageManagerReport(
            listOf(
                Package(id = ABC_ID, versionName = ABC_VERSION),
                Package(id = ABCD_ID, versionName = ABCD_VERSION),
                Package(id = BCD_ID, versionName = BCD_VERSION),
                Package(id = BCE_ID, versionName = BCE_VERSION),
            )
        )
        runBlocking {
            collector.updateAppVersions()
            coVerify(exactly = 3) { store.upsert(any(), any<String>(), false) }
        }
    }

    companion object {
        private const val ABC_ID = "a.b.c"
        private const val ABC_VERSION = "a.b.c 1.0"
        private const val ABCD_ID = "a.b.c.d"
        private const val ABCD_VERSION = "a.b.c.d 1.1"
        private const val BCD_ID = "b.c.d"
        private const val BCD_VERSION = "b.c.d 1.2"
        private const val BCE_ID = "b.c.e"
        private const val BCE_VERSION = "b.c.e 1.3"
    }
}
