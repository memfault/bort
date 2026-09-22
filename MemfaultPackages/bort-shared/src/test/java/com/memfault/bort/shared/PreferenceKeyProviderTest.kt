package com.memfault.bort.shared

import android.content.SharedPreferences
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

private class TestProvider<T>(
    prefs: SharedPreferences,
    defaultValue: T,
) : PreferenceKeyProvider<T>(prefs, defaultValue, preferenceKey = KEY) {
    companion object {
        const val KEY = "test_key"
    }
}

class PreferenceKeyProviderTest {
    private val prefs = FakeSharedPreferences()

    @Test
    fun writesWhenValueChanges() {
        val provider = TestProvider(prefs, defaultValue = 0L)

        provider.setValue(1L)
        provider.setValue(2L)

        assertThat(prefs.writes).isEqualTo(2)
        assertThat(provider.getValue()).isEqualTo(2L)
    }

    @Test
    fun skipsWriteWhenValueUnchanged() {
        val provider = TestProvider(prefs, defaultValue = 0L)
        provider.setValue(1L)

        provider.setValue(1L)
        provider.setValue(1L)

        assertThat(prefs.writes).isEqualTo(1)
        assertThat(provider.getValue()).isEqualTo(1L)
    }

    @Test
    fun skipsWriteWhenSetRepeatedlyToSameFlag() {
        val provider = TestProvider(prefs, defaultValue = false)

        repeat(10) { provider.setValue(true) }

        assertThat(prefs.writes).isEqualTo(1)
        assertThat(provider.getValue()).isTrue()
    }

    /** An unset key already reads as the default. */
    @Test
    fun skipsWriteWhenSetToDefaultWhileUnset() {
        val provider = TestProvider(prefs, defaultValue = "default")

        provider.setValue("default")

        assertThat(prefs.writes).isEqualTo(0)
        assertThat(provider.getValue()).isEqualTo("default")
    }

    @Test
    fun writesWhenValueReturnsToDefault() {
        val provider = TestProvider(prefs, defaultValue = false)
        provider.setValue(true)

        provider.setValue(false)

        assertThat(prefs.writes).isEqualTo(2)
        assertThat(provider.getValue()).isFalse()
    }

    @Test
    fun comparesSetsByContent() {
        val provider = TestProvider(prefs, defaultValue = emptySet<String>())
        provider.setValue(setOf("a", "b"))

        provider.setValue(setOf("b", "a"))

        assertThat(prefs.writes).isEqualTo(1)

        provider.setValue(setOf("a", "b", "c"))

        assertThat(prefs.writes).isEqualTo(2)
    }
}
