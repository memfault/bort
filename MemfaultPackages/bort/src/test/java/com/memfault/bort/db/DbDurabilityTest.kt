package com.memfault.bort.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.memfault.bort.settings.DbSynchronousMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

internal class DbDurabilityTest {
    @Test
    fun appliesEachModeAsExpectedPragma() {
        for (mode in DbSynchronousMode.entries) {
            val db: SupportSQLiteDatabase = mockk(relaxed = true)
            applySynchronousMode(db, mode)
            verify(exactly = 1) { db.execSQL("PRAGMA synchronous=${mode.name}") }
        }
    }

    @Test
    fun onOpenCallbackAppliesCurrentMode() {
        val db: SupportSQLiteDatabase = mockk(relaxed = true)
        var currentMode = DbSynchronousMode.OFF
        val callback = synchronousModeOnOpenCallback { currentMode }

        callback.onOpen(db)
        verify(exactly = 1) { db.execSQL("PRAGMA synchronous=OFF") }

        currentMode = DbSynchronousMode.FULL
        callback.onOpen(db)
        verify(exactly = 1) { db.execSQL("PRAGMA synchronous=FULL") }
    }

    @Test
    fun reapplySynchronousModeUsesWritableDatabase() {
        val db: SupportSQLiteDatabase = mockk(relaxed = true)
        val fakeOpenHelper = mockk<SupportSQLiteOpenHelper> {
            every { writableDatabase } returns db
        }
        val roomDatabase = mockk<RoomDatabase>(relaxed = true) {
            every { openHelper } returns fakeOpenHelper
        }

        roomDatabase.reapplySynchronousMode(DbSynchronousMode.NORMAL)

        verify(exactly = 1) { db.execSQL("PRAGMA synchronous=NORMAL") }
    }
}
