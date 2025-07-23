package com.memfault.bort.bugreport

import android.content.SharedPreferences
import com.memfault.bort.BortJson
import com.memfault.bort.PREFERENCE_PENDING_BUG_REPORT_REQUEST_OPTIONS
import com.memfault.bort.shared.BugReportRequest
import com.memfault.bort.shared.PreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

interface PendingBugReportRequestStorage {
    fun write(request: BugReportRequest?)
    fun read(): BugReportRequest?
}

@Singleton
class PendingBugReportRequestAccessor @Inject constructor(
    private val storage: PendingBugReportRequestStorage,
) {
    private val lock: ReentrantLock = ReentrantLock()

    fun set(request: BugReportRequest?) = lock.withLock {
        storage.write(request)
    }

    fun get(): BugReportRequest? = lock.withLock {
        storage.read()
    }

    fun compareAndSwap(
        newValue: BugReportRequest?,
        predicate: (readRequest: BugReportRequest?) -> Boolean,
    ): Pair<Boolean, BugReportRequest?> =
        lock.withLock {
            val currentValue = get()
            if (predicate(currentValue)) {
                set(newValue)
                Pair(true, currentValue)
            } else {
                Pair(false, null)
            }
        }
}

@ContributesBinding(SingletonComponent::class, boundType = PendingBugReportRequestStorage::class)
class RealPendingBugReportRequestStorage @Inject constructor(
    sharedPreferences: SharedPreferences,
) : PendingBugReportRequestStorage, PreferenceKeyProvider<String>(
    sharedPreferences = sharedPreferences,
    defaultValue = "",
    preferenceKey = PREFERENCE_PENDING_BUG_REPORT_REQUEST_OPTIONS,
) {
    override fun write(request: BugReportRequest?) =
        super.setValue(
            request?.let { BortJson.encodeToString(BugReportRequest.serializer(), it) } ?: "",
        )

    override fun read(): BugReportRequest? {
        val currentValue = super.getValue()
        return if (currentValue.isBlank()) {
            null
        } else {
            try {
                BortJson.decodeFromString(BugReportRequest.serializer(), currentValue)
            } catch (e: Exception) {
                null
            }
        }
    }
}
