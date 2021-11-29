package com.memfault.bort.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.memfault.bort.Bort
import com.memfault.bort.Bort.Companion.appCreated
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.SOFTWARE_TYPE
import com.memfault.bort.shared.SoftwareUpdateSettings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class SoftwareUpdateSettingsContentProvider : BaseContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor = MatrixCursor(arrayOf("settings")).apply {
        addRow(
            listOf(
                Json.encodeToString(SoftwareUpdateSettings.serializer(), gatherConfig())
            )
        )
    }

    private fun gatherConfig(): SoftwareUpdateSettings {
        // TODO: This does not run in the main thread, is runBlocking ok?
        return runBlocking {
            // Wait for confirmation that bort.onCreate() has happened. That call happens on the main thread, while this
            // code is running on a ContentProvider thread - so it is a race condition/random which happens first.
            appCreated.await()

            val deviceInfo = deviceInfoProvider.getDeviceInfo()
            SoftwareUpdateSettings(
                deviceSerial = deviceInfo.deviceSerial,
                currentVersion = deviceInfo.softwareVersion,
                hardwareVersion = deviceInfo.hardwareVersion,
                softwareType = SOFTWARE_TYPE,
                updateCheckIntervalMs = settings.otaSettings.updateCheckInterval.toLongMilliseconds(),
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
}

/**
 * A content provider that binds bort components. This operates lazily because, on Android, ContentProviders start
 * before the application class (https://developer.android.com/reference/android/app/Application.html#onCreate())
 */
abstract class BaseContentProvider : ContentProvider() {
    private val components by lazy { Bort.appComponents() }
    protected val deviceInfoProvider: DeviceInfoProvider get() = components.deviceInfoProvider
    protected val settings: SettingsProvider get() = components.settingsProvider
}
