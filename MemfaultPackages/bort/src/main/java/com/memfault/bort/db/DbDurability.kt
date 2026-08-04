package com.memfault.bort.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.memfault.bort.settings.DbSynchronousMode

/**
 * Applies the given durability mode to a live SQLite connection.
 */
fun applySynchronousMode(db: SupportSQLiteDatabase, mode: DbSynchronousMode) {
    db.execSQL("PRAGMA synchronous=${mode.name}")
}

/**
 * Room callback that applies [dbSynchronousMode] every time the database connection is (re)opened,
 * e.g. on first access after process start.
 */
fun synchronousModeOnOpenCallback(dbSynchronousMode: () -> DbSynchronousMode): RoomDatabase.Callback =
    object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            applySynchronousMode(db, dbSynchronousMode())
        }
    }

/**
 * Re-applies [mode] to this database's connection, so a backend settings update takes effect
 * without requiring an app/process restart. Note that [RoomDatabase.openHelper]'s
 * `writableDatabase` will open the database if it is not already open.
 */
fun RoomDatabase.reapplySynchronousMode(mode: DbSynchronousMode) {
    applySynchronousMode(openHelper.writableDatabase, mode)
}
