package com.memfault.bort.shared

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BatchableSharedPreferencesTest {
    private val delegate = FakeSharedPreferences()
    private val prefs = BatchableSharedPreferences(delegate)

    private fun write(key: String, value: String) = prefs.edit().putString(key, value).apply()

    @Test
    fun writesThroughWithoutABatch() {
        write("a", "1")
        write("b", "2")

        assertThat(delegate.writes).isEqualTo(2)
        assertThat(prefs.getString("a", null)).isEqualTo("1")
    }

    @Test
    fun collapsesBatchedWritesIntoOne() = runTest {
        prefs.withBatch {
            write("a", "1")
            write("b", "2")
            write("c", "3")
            assertThat(delegate.writes).isEqualTo(0)
        }

        assertThat(delegate.writes).isEqualTo(1)
        assertThat(delegate.getString("a", null)).isEqualTo("1")
        assertThat(delegate.getString("b", null)).isEqualTo("2")
        assertThat(delegate.getString("c", null)).isEqualTo("3")
    }

    /** Otherwise [PreferenceKeyProvider]'s compare-before-write would compare against stale values. */
    @Test
    fun readsSeeStagedValues() = runTest {
        write("a", "before")

        prefs.withBatch {
            write("a", "after")
            assertThat(prefs.getString("a", null)).isEqualTo("after")
            assertThat(delegate.getString("a", null)).isEqualTo("before")
        }

        assertThat(prefs.getString("a", null)).isEqualTo("after")
    }

    @Test
    fun lastStagedWriteWins() = runTest {
        prefs.withBatch {
            write("a", "1")
            write("a", "2")
        }

        assertThat(delegate.writes).isEqualTo(1)
        assertThat(delegate.getString("a", null)).isEqualTo("2")
    }

    @Test
    fun stagesRemovals() = runTest {
        write("a", "1")

        prefs.withBatch {
            prefs.edit().remove("a").apply()
            assertThat(prefs.contains("a")).isFalse()
            assertThat(delegate.contains("a")).isTrue()
        }

        assertThat(delegate.contains("a")).isFalse()
    }

    @Test
    fun stagesANullPutAsARemoval() = runTest {
        write("a", "1")

        prefs.withBatch {
            prefs.edit().putString("a", null).apply()
            assertThat(prefs.contains("a")).isFalse()
        }

        assertThat(delegate.contains("a")).isFalse()
    }

    @Test
    fun aStagedWriteAfterARemovalWins() = runTest {
        write("a", "1")

        prefs.withBatch {
            prefs.edit().remove("a").apply()
            write("a", "2")
        }

        assertThat(delegate.getString("a", null)).isEqualTo("2")
    }

    @Test
    fun flushesWhenTheBlockThrows() = runTest {
        runCatching {
            prefs.withBatch {
                write("a", "1")
                error("boom")
            }
        }

        assertThat(delegate.writes).isEqualTo(1)
        assertThat(delegate.getString("a", null)).isEqualTo("1")
    }

    @Test
    fun flushesOnceForNestedBatches() = runTest {
        prefs.withBatch {
            write("a", "1")
            prefs.withBatch { write("b", "2") }
            assertThat(delegate.writes).isEqualTo(0)
        }

        assertThat(delegate.writes).isEqualTo(1)
        assertThat(delegate.getString("b", null)).isEqualTo("2")
    }

    @Test
    fun writesThroughWhenTheBatchClosesFirst() = runTest {
        var editor = prefs.edit()
        prefs.withBatch { editor = prefs.edit().putString("a", "1") }

        editor.apply()

        assertThat(delegate.getString("a", null)).isEqualTo("1")
    }

    @Test
    fun preservesValueTypes() = runTest {
        prefs.withBatch {
            prefs.edit()
                .putBoolean("bool", true)
                .putInt("int", 7)
                .putLong("long", 8L)
                .putFloat("float", 1.5f)
                .putStringSet("set", mutableSetOf("x", "y"))
                .apply()
        }

        assertThat(delegate.writes).isEqualTo(1)
        assertThat(delegate.getBoolean("bool", false)).isTrue()
        assertThat(delegate.getInt("int", 0)).isEqualTo(7)
        assertThat(delegate.getLong("long", 0L)).isEqualTo(8L)
        assertThat(delegate.getFloat("float", 0f)).isEqualTo(1.5f)
        assertThat(delegate.getStringSet("set", null)).isEqualTo(mutableSetOf("x", "y"))
    }
}
