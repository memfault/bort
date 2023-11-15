package com.memfault.bort.android

import android.app.Application
import android.app.usage.NetworkStatsManager
import android.content.ContentResolver
import android.content.res.Resources
import android.os.DropBoxManager
import android.os.Looper
import com.memfault.bort.IO
import com.memfault.bort.Main
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
    fun dropBoxManager(application: Application): DropBoxManager? =
        application.getSystemService(DropBoxManager::class.java)

    @Provides
    fun networkStatsManager(application: Application): NetworkStatsManager =
        application.getSystemService(NetworkStatsManager::class.java)
}
