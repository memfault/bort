package com.memfault.bort.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.SOFTWARE_TYPE
import com.memfault.bort.shared.SoftwareUpdateSettings
import com.memfault.bort.shared.SoftwareUpdateSettings.Companion.createCursor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking

class SoftwareUpdateSettingsContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor = createCursor(gatherConfig())

    private fun gatherConfig(): SoftwareUpdateSettings {
        // TODO: This does not run in the main thread, is runBlocking ok?
        return runBlocking {
            val deviceInfo = runBlocking { entryPoint().deviceInfo().getDeviceInfo() }
            val settings = entryPoint().settings()
            SoftwareUpdateSettings(
                deviceSerial = deviceInfo.deviceSerial,
                currentVersion = deviceInfo.softwareVersion,
                hardwareVersion = deviceInfo.hardwareVersion,
                softwareType = SOFTWARE_TYPE,
                updateCheckIntervalMs = settings.otaSettings.updateCheckInterval.inWholeMilliseconds,
                baseUrl = settings.httpApiSettings.deviceBaseUrl,
                projectApiKey = settings.httpApiSettings.projectKey,
            )
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        0

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ContentProviderEntryPoint {
        fun deviceInfo(): DeviceInfoProvider
        fun settings(): SettingsProvider
    }

    fun entryPoint() = EntryPointAccessors.fromApplication(context!!, ContentProviderEntryPoint::class.java)
}
