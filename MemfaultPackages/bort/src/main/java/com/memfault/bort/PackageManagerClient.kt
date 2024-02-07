package com.memfault.bort

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import com.memfault.bort.parsers.Package
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.settings.CachePackageManagerReport
import com.memfault.bort.shared.Logger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

class PackageManagerClient @Inject constructor(
    private val packageManager: PackageManager,
    private val cachePackageManagerReport: CachePackageManagerReport,
    @IO private val ioCoroutineContext: CoroutineContext,
) {
    private val mutex = Mutex()

    /**
     * Sequence number returned by getChangedPackages() (starts from zero). If this changes, then a package was added/
     * removed/updated.
     **/
    private var lastSequence: Int = 0

    /**
     * Cached package manager report. We recreate this whenever getChangedPackages() returns changes.
     */
    private var cachedReport: PackageManagerReport? = null

    /**
     * Get the latest installed packages report. This will always be current. State is cached internally, but there is
     * a small cost to calling this method, so iterate over the result if required, rather than calling multiple times.
     */
    suspend fun getPackageManagerReport(): PackageManagerReport = mutex.withLock {
        if (!cachePackageManagerReport()) {
            cachedReport = null
            return fetchApplicationsFromPackageManager()
        }
        val changes = packageManager.getChangedPackages(lastSequence)
        val newSequence = changes?.sequenceNumber ?: lastSequence
        val cachedReportValForSmartCast = cachedReport
        return if (newSequence != lastSequence || cachedReportValForSmartCast == null) {
            Logger.d("PackageManagerClient: re-creating report")
            val report = fetchApplicationsFromPackageManager()
            cachedReport = report
            lastSequence = newSequence
            report
        } else {
            cachedReportValForSmartCast
        }
    }

    /**
     * Build a report from [PackageManager]. This requires a couple of API calls to collect all of the required values.
     */
    @Suppress("DEPRECATION")
    @SuppressLint("QueryPermissionsNeeded")
    private suspend fun fetchApplicationsFromPackageManager(): PackageManagerReport = withContext(ioCoroutineContext) {
        try {
            withTimeout(TIMEOUT) {
                val fetchedApps = try {
                    packageManager.getInstalledApplications(0)
                } catch (e: NameNotFoundException) {
                    null
                }
                val fetchedPackages = try {
                    packageManager.getInstalledPackages(0)
                } catch (e: NameNotFoundException) {
                    null
                }
                val packagesList = mutableListOf<Package>()
                fetchedApps?.forEach { application ->
                    fetchedPackages?.firstOrNull { pkg -> pkg.packageName == application.packageName }?.let { pkg ->
                        val pkgToAdd = Package(
                            id = application.packageName,
                            userId = application.uid,
                            versionCode = pkg.versionCode.toLong(),
                            versionName = pkg.versionName,
                        )
                        packagesList.add(pkgToAdd)
                    }
                }
                PackageManagerReport(packagesList)
            }
        } catch (e: TimeoutCancellationException) {
            Logger.w("PackageManagerClient: timeout")
            PackageManagerReport()
        }
    }

    companion object {
        private val TIMEOUT = 10.seconds
    }
}
