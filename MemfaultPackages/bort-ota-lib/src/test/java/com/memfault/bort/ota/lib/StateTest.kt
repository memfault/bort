package com.memfault.bort.ota.lib

import android.content.SharedPreferences
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

internal class StateTest {
    /**
     * Verify that we didn't break deserialization of persisted OTA state between releases.
     */
    @Test
    fun deserializesOldState() {
        // Never change this (we are validating ability to read old serialized data).
        // Missing: isForced.
        // Renamed: artifactMetadata (was metadata).
        val serializedState = """
{"type":"com.memfault.bort.ota.lib.State.UpdateAvailable","ota":{"url":"url","version":"version","releaseNotes":"notes","metadata":{"METADATA_HASH":"z4x6Wb+qNYpMKA7+KnMcbSFK6fxX8vbyEzhK2gBfJbQ=","FILE_SIZE":"99449","METADATA_SIZE":"51846","FILE_HASH":"X9zpqKb2z15s5eNhRuzntqYlPSB011/aGcdftaTRsrI=","_MFLT_PAYLOAD_SIZE":"99449","_MFLT_PAYLOAD_OFFSET":"1295"},"releaseMetadata":{},"isForced":null},"background":false}
        """.trimIndent()
        val sharedPrefs: SharedPreferences = mockk {
            every { getString(any(), any()) } answers { serializedState }
        }
        val stateStore = SharedPreferencesStateStore(sharedPrefs)

        val ota =
            Ota(
                url = "url",
                version = "version",
                releaseNotes = "notes",
                artifactMetadata = mapOf(
                    "METADATA_HASH" to "z4x6Wb+qNYpMKA7+KnMcbSFK6fxX8vbyEzhK2gBfJbQ=",
                    "FILE_SIZE" to "99449",
                    "METADATA_SIZE" to "51846",
                    "FILE_HASH" to "X9zpqKb2z15s5eNhRuzntqYlPSB011/aGcdftaTRsrI=",
                    "_MFLT_PAYLOAD_SIZE" to "99449",
                    "_MFLT_PAYLOAD_OFFSET" to "1295",
                ),
                releaseMetadata = emptyMap(),
                isForced = null,
            )
        // Can update this if default values change.
        val expectedState: State = State.UpdateAvailable(ota = ota)
        val deserializedState = stateStore.read()
        assertThat(deserializedState).isEqualTo(expectedState)
    }
}
