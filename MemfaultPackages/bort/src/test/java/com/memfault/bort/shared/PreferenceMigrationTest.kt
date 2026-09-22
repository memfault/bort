package com.memfault.bort.shared

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val KEY = "BLOB"

@RunWith(RobolectricTestRunner::class)
class PreferenceMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val from = context.getSharedPreferences("old", Context.MODE_PRIVATE)
    private val into = context.getSharedPreferences("new", Context.MODE_PRIVATE)

    @Test
    fun `moves the value and clears the old key`() {
        from.edit().putString(KEY, "value").apply()

        migrateStringPreference(from = from, into = into, key = KEY)

        assertThat(into.getString(KEY, null)).isEqualTo("value")
        assertThat(from.contains(KEY)).isFalse()
    }

    @Test
    fun `keeps the newer value, but still clears the old key`() {
        from.edit().putString(KEY, "stale").apply()
        into.edit().putString(KEY, "current").apply()

        migrateStringPreference(from = from, into = into, key = KEY)

        assertThat(into.getString(KEY, null)).isEqualTo("current")
        assertThat(from.contains(KEY)).isFalse()
    }

    @Test
    fun `does nothing when there is nothing to move`() {
        migrateStringPreference(from = from, into = into, key = KEY)

        assertThat(into.getString(KEY, null)).isNull()
    }
}
