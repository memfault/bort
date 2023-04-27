package com.memfault.bort.dropbox

import android.content.SharedPreferences
import com.memfault.bort.FakeCombinedTimeProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals

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
        assertEquals(expected, provider.timeMillis)
    }

    @Test
    fun doesNotOverrideTimestamp() {
        prefVal = 123456789
        assertEquals(123456789, provider.timeMillis)
    }
}
