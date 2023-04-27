package com.memfault.bort.dropbox

import android.content.Intent
import android.content.SharedPreferences
import android.os.DropBoxManager
import com.memfault.bort.PREFERENCE_LAST_PROCESSED_DROPBOX_ENTRY_TIME_MILLIS
import com.memfault.bort.PREFERENCE_LAST_PROCESSED_DROPBOX_PENDING_TIME_CHANGE
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BaseAbsoluteTime
import com.memfault.bort.time.CombinedTimeProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.hours

interface DropBoxLastProcessedEntryProvider {
    var timeMillis: Long
}

interface DropBoxPendingTimeChangeProvider {
    var pendingBackwardsTimeChange: Boolean
}

@ContributesBinding(SingletonComponent::class, boundType = DropBoxLastProcessedEntryProvider::class)
class RealDropBoxLastProcessedEntryProvider @Inject constructor(
    sharedPreferences: SharedPreferences,
    private val timeProvider: CombinedTimeProvider,
) : DropBoxLastProcessedEntryProvider, PreferenceKeyProvider<Long>(
    sharedPreferences = sharedPreferences,
    defaultValue = NOT_SET,
    preferenceKey = PREFERENCE_LAST_PROCESSED_DROPBOX_ENTRY_TIME_MILLIS
) {
    override var timeMillis
        get() = when (val timeMs = super.getValue()) {
            // Only used once after Bort first starts: accept dropbox entries going back up to INITIAL_DROPBOX_LIMIT.
            NOT_SET -> timeProvider.now().timestamp.toEpochMilli() - INITIAL_DROPBOX_LIMIT.inWholeMilliseconds
            else -> timeMs
        }
        set(value) = super.setValue(value)

    companion object {
        private const val NOT_SET: Long = 0
        private val INITIAL_DROPBOX_LIMIT = 1.hours
    }
}

@ContributesBinding(SingletonComponent::class, boundType = DropBoxPendingTimeChangeProvider::class)
class RealDropBoxPendingTimeChangeProvider @Inject constructor(
    sharedPreferences: SharedPreferences
) : DropBoxPendingTimeChangeProvider, PreferenceKeyProvider<Boolean>(
    sharedPreferences = sharedPreferences,
    defaultValue = false,
    preferenceKey = PREFERENCE_LAST_PROCESSED_DROPBOX_PENDING_TIME_CHANGE
) {
    override var pendingBackwardsTimeChange
        get() = super.getValue()
        set(value) = super.setValue(value)
}

@Singleton
class ProcessedEntryCursorProvider @Inject constructor(
    lastProcessedEntryProvider: DropBoxLastProcessedEntryProvider,
    pendingTimeChangedProvider: DropBoxPendingTimeChangeProvider,
) {
    private val lock = ReentrantLock()
    private var timeMillis: Long by lastProcessedEntryProvider::timeMillis
    private var pendingBackwardsTimeChange: Boolean by pendingTimeChangedProvider::pendingBackwardsTimeChange
    private var currentToken = Object()

    inner class Cursor(
        val timeMillis: Long,
        val token: Any,
    ) {

        fun next(newTimeMillis: Long): Cursor {
            advanceTo(newTimeMillis, token)
            return makeCursor()
        }

        /**
         * Returns the current cursor if still up-to-date or otherwise returns a new, up-to-date one.
         */
        fun refresh(): Cursor {
            lock.withLock {
                if (token == currentToken) return this
                return makeCursor()
            }
        }
    }

    fun makeCursor() =
        lock.withLock {
            Cursor(timeMillis = timeMillis, token = currentToken)
        }

    fun advanceTo(newTimeMillis: Long, token: Any) {
        lock.withLock {
            if (token == currentToken) {
                timeMillis = newTimeMillis
                currentToken = Object()
            }
        }
    }

    fun handleTimeChange(getNow: () -> BaseAbsoluteTime = AbsoluteTime.Companion::now) {
        // DropBoxManagerService "backdates" past entries that appear in the
        // future due to a backwards time change, but only when adding a new entry to
        // the DropBoxManager. So, when the time is changed, we'll have to wait until
        // the next time a DROPBOX_ENTRY_ADDED is received. Only then should we adjust
        // the cursor position to the TIME extra in the intent.
        // See https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/DropBoxManagerService.java;l=1070-1099;drc=60ef47b3785cfb7e420b09692788293d1e564f7f
        val now = getNow().timestamp.toEpochMilli()
        lock.withLock {
            if (now < timeMillis) {
                Logger.w("Detected backwards time change! $now < $timeMillis")
                pendingBackwardsTimeChange = true
            }
        }
    }

    fun handleTimeFromEntryAddedIntent(intent: Intent) {
        if (!pendingBackwardsTimeChange) return

        val addedEntryTimeMillis = intent.getLongExtra(DropBoxManager.EXTRA_TIME, Long.MAX_VALUE)
        lock.withLock {
            Logger.w("Changing cursor from $timeMillis to $addedEntryTimeMillis")
            advanceTo(addedEntryTimeMillis - 1, currentToken)
        }
        pendingBackwardsTimeChange = false
    }
}
