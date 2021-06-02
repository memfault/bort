package com.memfault.bort.dropbox

import android.content.Intent
import android.content.SharedPreferences
import android.os.DropBoxManager
import com.memfault.bort.PREFERENCE_LAST_PROCESSED_DROPBOX_ENTRY_TIME_MILLIS
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface DropBoxLastProcessedEntryProvider {
    var timeMillis: Long
}

class RealDropBoxLastProcessedEntryProvider(
    sharedPreferences: SharedPreferences
) : DropBoxLastProcessedEntryProvider, PreferenceKeyProvider<Long>(
    sharedPreferences = sharedPreferences,
    defaultValue = 0,
    preferenceKey = PREFERENCE_LAST_PROCESSED_DROPBOX_ENTRY_TIME_MILLIS
) {
    override var timeMillis
        get() = super.getValue()
        set(value) = super.setValue(value)
}

class ProcessedEntryCursorProvider(
    lastProcessedEntryProvider: DropBoxLastProcessedEntryProvider
) {
    private val lock = ReentrantLock()
    private var timeMillis: Long by lastProcessedEntryProvider::timeMillis
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

    fun handleTimeChangeFromEntryAddedIntent(intent: Intent) {
        // If the time is changed past the last processed entry time, move it back to the time of the added entry
        // to avoid missing new entries with timestamps from before the previous last processed entry time:
        val addedEntryTimeMillis = intent.getLongExtra(DropBoxManager.EXTRA_TIME, Long.MAX_VALUE)
        lock.withLock {
            if (addedEntryTimeMillis < timeMillis) {
                Logger.w("Detected backwards time change! $addedEntryTimeMillis < $timeMillis")
                advanceTo(addedEntryTimeMillis - 1, currentToken)
            }
        }
    }
}
