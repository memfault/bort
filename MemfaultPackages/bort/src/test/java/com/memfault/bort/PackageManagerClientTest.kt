package com.memfault.bort

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.parsers.Package
import com.memfault.bort.parsers.PackageManagerReport
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PackageManagerClientTest {
    private var changedPackagesSequence = 0
    private val packageManager: PackageManager = mockk {
        every { getInstalledApplications(0) } answers { PM_APPS }
        every { getInstalledPackages(0) } answers { PM_PACKAGES }
        every { getChangedPackages(any()) } answers {
            mockk {
                val sequence = changedPackagesSequence
                every { sequenceNumber } answers { sequence }
            }
        }
    }
    private var cachePackages: Boolean = true

    @Test
    fun runsAndCachesResult() = runTest {
        val packageManagerClient =
            PackageManagerClient(packageManager, { cachePackages }, StandardTestDispatcher(testScheduler))
        assertThat(packageManagerClient.getPackageManagerReport().findByPackage(APP_BORT.id)).isEqualTo(APP_BORT)
        assertThat(packageManagerClient.getPackageManagerReport().findByPackage(APP_BORT.id)).isEqualTo(APP_BORT)
        verify(exactly = 2) { packageManager.getChangedPackages(any()) }
        verify(exactly = 1) { packageManager.getInstalledPackages(0) }
        verify(exactly = 1) { packageManager.getInstalledApplications(0) }
    }

    @Test
    fun runsAndInvalidatesCache() = runTest {
        val packageManagerClient =
            PackageManagerClient(packageManager, { cachePackages }, StandardTestDispatcher(testScheduler))
        assertThat(packageManagerClient.getPackageManagerReport().findByPackage(APP_BORT.id)).isEqualTo(APP_BORT)
        changedPackagesSequence = 1
        assertThat(packageManagerClient.getPackageManagerReport().findByPackage(APP_BORT.id)).isEqualTo(APP_BORT)
        verify(exactly = 2) { packageManager.getChangedPackages(any()) }
        verify(exactly = 2) { packageManager.getInstalledPackages(0) }
        verify(exactly = 2) { packageManager.getInstalledApplications(0) }
    }

    @Test
    fun returnsAllAppsWithSameUid() = runTest {
        val packageManagerClient =
            PackageManagerClient(packageManager, { cachePackages }, StandardTestDispatcher(testScheduler))
        val report = packageManagerClient.getPackageManagerReport()
        assertThat(report).isEqualTo(PackageManagerReport(APPS))
        assertThat(report.packages.size).isEqualTo(3)
    }

    @Test
    fun doesNotCacheIfDisabled() = runTest {
        cachePackages = false
        val packageManagerClient =
            PackageManagerClient(packageManager, { cachePackages }, StandardTestDispatcher(testScheduler))
        assertThat(packageManagerClient.getPackageManagerReport().findByPackage(APP_BORT.id)).isEqualTo(APP_BORT)
        assertThat(packageManagerClient.getPackageManagerReport().findByPackage(APP_BORT.id)).isEqualTo(APP_BORT)
        verify(exactly = 0) { packageManager.getChangedPackages(any()) }
        verify(exactly = 2) { packageManager.getInstalledPackages(0) }
        verify(exactly = 2) { packageManager.getInstalledApplications(0) }
    }

    companion object {
        private val APP_BORT =
            Package(id = "com.memfault.bort", userId = 11000, versionName = "4.12.0", versionCode = 401200)
        private val APP_YOUTUBE =
            Package(id = "com.google.youtube", userId = 12000, versionName = "17.5.36", versionCode = 778899)
        private val APP_YOUTUBE_2 =
            Package(id = "com.google.youtube2", userId = 12000, versionName = "17.5.38", versionCode = 778890)
        private val APPS = listOf(
            APP_BORT,
            APP_YOUTUBE,
            APP_YOUTUBE_2,
        )
        private val PM_APPS = APPS.map { app ->
            ApplicationInfo().apply {
                packageName = app.id
                uid = app.userId!!
            }
        }

        @Suppress("DEPRECATION")
        private val PM_PACKAGES = APPS.map { app ->
            PackageInfo().apply {
                packageName = app.id
                versionName = app.versionName
                versionCode = app.versionCode!!.toInt()
            }
        }
    }
}
