package com.memfault.bort.settings

import kotlinx.serialization.Serializable

/**
 * Controls SQLite's PRAGMA synchronous for all Bort-owned Room databases. Values match SQLite's
 * own PRAGMA synchronous values (OFF, NORMAL, FULL) - see https://www.sqlite.org/pragma.html#pragma_synchronous.
 */
@Serializable
enum class DbSynchronousMode {
    OFF,
    NORMAL,
    FULL,
}
