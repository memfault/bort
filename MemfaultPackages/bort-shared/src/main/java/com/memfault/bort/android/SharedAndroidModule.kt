package com.memfault.bort.android

import android.app.ActivityManager
import android.app.Application
import android.app.usage.NetworkStatsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.DropBoxManager
import android.os.Looper
import com.memfault.bort.BasicCommandTimeout
import com.memfault.bort.Default
import com.memfault.bort.IO
import com.memfault.bort.Main
import com.memfault.bort.shared.BASIC_COMMAND_TIMEOUT_MS
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * Module to bind simple Android services to the Dagger graph.
 */
@ContributesTo(SingletonComponent::class)
@Module
class SharedAndroidModule {
    @Provides
    fun contentResolver(application: Application): ContentResolver = application.contentResolver

    @Provides
    fun packageManager(application: Application): PackageManager = application.packageManager

    @Provides
    fun resources(application: Application): Resources = application.resources

    @Provides
    @Main
    fun mainLooper(): Looper = Looper.getMainLooper()

    @Provides
    @Main
    fun mainCoroutineContext(): CoroutineContext = Dispatchers.Main

    @Provides
    @IO
    fun ioCoroutineContext(): CoroutineContext = Dispatchers.IO

    @Provides
    @Default
    fun defaultCoroutineContext(): CoroutineContext = Dispatchers.Default

    @Provides
    fun dropBoxManager(application: Application): DropBoxManager? =
        application.getSystemService(DropBoxManager::class.java)

    @Provides
    fun networkStatsManager(application: Application): NetworkStatsManager =
        application.getSystemService(NetworkStatsManager::class.java)

    @Provides
    fun connectivityManager(application: Application): ConnectivityManager =
        application.getSystemService(ConnectivityManager::class.java)

    @Provides
    fun wifiManager(application: Application): WifiManager? =
        application.getSystemService(WifiManager::class.java)

    @Provides
    fun storageStatsManager(application: Application): StorageStatsManager =
        application.getSystemService(StorageStatsManager::class.java)

    @Provides
    fun batteryManager(application: Application): BatteryManager =
        application.getSystemService(BatteryManager::class.java)

    @Provides
    fun activityManager(application: Application): ActivityManager =
        application.getSystemService(ActivityManager::class.java)

    @Provides
    fun usageStatsManager(application: Application): UsageStatsManager =
        application.getSystemService(UsageStatsManager::class.java)

    @Provides
    @BasicCommandTimeout
    fun basicTimeout(): Long = BASIC_COMMAND_TIMEOUT_MS
}
