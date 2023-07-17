package com.memfault.bort.android

import android.app.Application
import android.content.ContentResolver
import android.content.res.Resources
import android.os.Looper
import com.memfault.bort.Main
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

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
}
