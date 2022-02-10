package com.memfault.bort.dropbox

import android.content.Intent
import android.os.DropBoxManager
import com.memfault.bort.time.toAbsoluteTime
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val TEST_INIT_TIME: Long = 123

class ProcessedEntryCursorProviderTest {
    lateinit var entryProvider: DropBoxLastProcessedEntryProvider
    lateinit var pendingTimeChangeProvider: DropBoxPendingTimeChangeProvider
    lateinit var cursorProvider: ProcessedEntryCursorProvider
    lateinit var cursor: ProcessedEntryCursorProvider.Cursor

    @BeforeEach
    fun setUp() {
        entryProvider = FakeLastProcessedEntryProvider(TEST_INIT_TIME)
        pendingTimeChangeProvider = FakeDropBoxPendingTimeChangeProvider(false)
        cursorProvider = ProcessedEntryCursorProvider(entryProvider, pendingTimeChangeProvider)
        cursor = cursorProvider.makeCursor()
        assertEquals(TEST_INIT_TIME, cursor.timeMillis)
    }

    @Test
    fun nextUnchanged() {
        cursor.next(TEST_INIT_TIME + 1)
        assertEquals(TEST_INIT_TIME + 1, entryProvider.timeMillis)
    }

    @Test
    fun refreshUpToDate() {
        assertEquals(cursor, cursor.refresh())
    }

    @Test
    fun refreshStale() {
        cursor.next(TEST_INIT_TIME + 1)
        assertNotEquals(cursor, cursor.refresh())
    }

    @Test
    fun nextChangedWithBackwardsTimeChange() {
        // Time gets changed backwards:
        val getNow = {
            (TEST_INIT_TIME - 20).toAbsoluteTime()
        }
        cursorProvider.handleTimeChange(getNow)

        // Entry added intent:
        val mockIntent = mockk<Intent> {
            every { getLongExtra(DropBoxManager.EXTRA_TIME, Long.MAX_VALUE) } returns TEST_INIT_TIME - 10
        }
        cursorProvider.handleTimeFromEntryAddedIntent(mockIntent)
        cursor.next(TEST_INIT_TIME + 1)
        assertEquals(TEST_INIT_TIME - 10 - 1, entryProvider.timeMillis)
    }

    @Test
    fun unchangedByIntent() {
        // Time gets changed forwards:
        val getNow = {
            (TEST_INIT_TIME + 20).toAbsoluteTime()
        }
        cursorProvider.handleTimeChange(getNow)

        val mockIntent = mockk<Intent> {
            every { getLongExtra(DropBoxManager.EXTRA_TIME, Long.MAX_VALUE) } returns TEST_INIT_TIME + 30
        }
        cursorProvider.handleTimeFromEntryAddedIntent(mockIntent)

        // Intent should not affect the last processed entry:
        assertEquals(TEST_INIT_TIME, entryProvider.timeMillis)
    }
}
