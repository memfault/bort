package com.memfault.bort

import android.content.SharedPreferences
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
            get().let { currentValue ->
                if (predicate(currentValue)) {
                    Pair(true, currentValue).also { set(newValue) }
                } else {
                    Pair(false, null)
                }
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

    override fun read(): BugReportRequest? =
        super.getValue().let {
            if (it.isBlank()) {
                return null
            } else {
                try {
                    BortJson.decodeFromString(BugReportRequest.serializer(), it)
                } catch (e: Exception) {
                    null
                }
            }
        }
}
