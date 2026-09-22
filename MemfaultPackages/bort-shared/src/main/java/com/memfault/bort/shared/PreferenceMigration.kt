package com.memfault.bort.shared

import android.content.SharedPreferences

/**
 * Moves a string preference out of [from] and into [into], once.
 *
 * Removing the old key is the point: on a device which already has the value in the shared file, pointing new
 * code at a new file would otherwise leave the old copy behind, still being rewritten by every other edit.
 */
fun migrateStringPreference(
    from: SharedPreferences,
    into: SharedPreferences,
    key: String,
) {
    val existing = from.getString(key, null) ?: return
    if (!into.contains(key)) {
        into.edit().putString(key, existing).apply()
    }
    from.edit().remove(key).apply()
}
