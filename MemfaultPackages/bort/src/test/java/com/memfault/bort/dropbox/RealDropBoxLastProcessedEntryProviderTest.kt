package com.memfault.bort.dropbox

import android.content.SharedPreferences
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.FakeCombinedTimeProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

internal class RealDropBoxLastProcessedEntryProviderTest {
    private val prefs: SharedPreferences = mockk {
        every { getLong(any(), any()) } answers { prefVal ?: arg(1) }
    }
    private var prefVal: Long? = null
    private val timeProvider = FakeCombinedTimeProvider
    private val provider = RealDropBoxLastProcessedEntryProvider(prefs, timeProvider)

    @Test
    fun doesOverrideTimestamp() {
        prefVal = null
        val expected = timeProvider.now().timestamp.toEpochMilli() - 3600000
        assertThat(provider.timeMillis).isEqualTo(expected)
    }

    @Test
    fun doesNotOverrideTimestamp() {
        prefVal = 123456789
        assertThat(provider.timeMillis).isEqualTo(123456789)
    }
}
