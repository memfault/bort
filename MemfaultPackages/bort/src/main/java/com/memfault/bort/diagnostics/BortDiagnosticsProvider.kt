package com.memfault.bort.diagnostics

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.MatrixCursor
import android.net.Uri
import com.memfault.bort.DevMode
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.OverrideSerial
import com.memfault.bort.ProjectKeySyspropName
import com.memfault.bort.requester.PeriodicWorkRequester.PeriodicWorkManager
import com.memfault.bort.settings.AllowProjectKeyChange
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.settings.BuiltInProjectKey
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.BuildConfig
import com.memfault.bort.shared.Logger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import com.memfault.bort.BuildConfig as BortBuildConfig

class BortDiagnosticsProvider : ContentProvider() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DiagnosticsProviderEntryPoint {
        fun bortEnabledProvider(): BortEnabledProvider
        fun devMode(): DevMode
        fun allowProjectKeyChange(): AllowProjectKeyChange
        fun builtInProjectKey(): BuiltInProjectKey
        fun projectKeySysprop(): ProjectKeySyspropName
        fun settings(): SettingsProvider
        fun bortErrors(): BortErrors
        fun periodicWorkManager(): PeriodicWorkManager
        fun bortJobReporter(): BortJobReporter
        fun deviceInfoProvider(): DeviceInfoProvider
        fun overrideSerial(): OverrideSerial
    }

    val entryPoint: DiagnosticsProviderEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            context!!,
            DiagnosticsProviderEntryPoint::class.java,
        )
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(arrayOf("key", "value"))
        runBlocking {
            cursor.addRow(arrayOf("enabled", entryPoint.bortEnabledProvider().isEnabled()))
            cursor.addRow(arrayOf("requires_runtime_enable", entryPoint.bortEnabledProvider().requiresRuntimeEnable()))
            cursor.addRow(arrayOf("dev_mode", entryPoint.devMode().isEnabled()))
            cursor.addRow(arrayOf("allow_project_key_change", entryPoint.allowProjectKeyChange()()))
            cursor.addRow(arrayOf("project_key_sysprop", entryPoint.projectKeySysprop()()))
            cursor.addRow(arrayOf("builtin_project_key", entryPoint.builtInProjectKey()()))
            cursor.addRow(arrayOf("project_key", entryPoint.settings().httpApiSettings.projectKey))
            cursor.addRow(arrayOf("bort_lite", BortBuildConfig.BORT_LITE))
            cursor.addRow(arrayOf("upstream_version_code", BuildConfig.UPSTREAM_VERSION_CODE))
            cursor.addRow(arrayOf("upstream_version_name", BuildConfig.UPSTREAM_VERSION_NAME))
            val bortErrors = entryPoint.bortErrors().getAllErrors()
            bortErrors.forEach {
                cursor.addRow(arrayOf("bort_error", it.toString()))
            }
            addJobStatus(cursor)
            cursor.addRow(arrayOf("device_serial", entryPoint.deviceInfoProvider().getDeviceInfo().deviceSerial))
            cursor.addRow(arrayOf("override_serial", entryPoint.overrideSerial().overriddenSerial))
        }
        Logger.d("Bort Diagnostics:")
        Logger.d(DatabaseUtils.dumpCursorToString(cursor))
        cursor.moveToFirst()
        return cursor
    }

    private suspend fun addJobStatus(cursor: MatrixCursor) {
        // From WorkManager (only tells us about future jobs + last stopped reason)
        entryPoint.periodicWorkManager().diagnostics().forEach { work ->
            cursor.addRow(arrayOf("workinfo_${work.name}", work.toString()))
        }
        // From our own records: latest run for each type
        entryPoint.bortJobReporter().getLatestForEachJob().forEach { job ->
            cursor.addRow(arrayOf("job_latest_${job.jobName}", job.toString()))
        }
        entryPoint.bortJobReporter().getIncompleteJobs().forEach { job ->
            cursor.addRow(arrayOf("job_incomplete", job.toString()))
        }
        entryPoint.bortJobReporter().jobStats().forEach { jobName, job ->
            cursor.addRow(arrayOf("job_stats_$jobName", job))
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
