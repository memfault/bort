package com.memfault.bort.tokenbucket

import com.memfault.bort.dagger.InjectSet
import com.memfault.bort.time.UptimeTracker
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenBucketStoreRegistry @Inject constructor(
    private val updateTracker: UptimeTracker,
    private val tokenBucketStores: InjectSet<TokenBucketStore>,
) {
    fun handleLinuxReboot() {
        tokenBucketStores.forEach { store ->
            store.handleLinuxReboot(updateTracker.getPreviousUptime())
        }
    }

    /**
     * For testing purposes: resets all token bucket stores.
     */
    fun reset() {
        tokenBucketStores.forEach { store ->
            store.reset()
        }
    }
}
