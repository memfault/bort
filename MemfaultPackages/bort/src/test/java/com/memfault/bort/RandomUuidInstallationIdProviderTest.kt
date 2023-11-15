package com.memfault.bort

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RandomUuidInstallationIdProviderTest {
    lateinit var mockSharedPreferences: FakeSharedPreferences

    @BeforeEach
    fun setUp() {
        mockSharedPreferences = makeFakeSharedPreferences()
    }

    @Test
    fun setsRandomUUIDWhenUninitialized() {
        assertNull(mockSharedPreferences.backingStorage[PREFERENCE_DEVICE_ID])
        val deviceIdProvider = RandomUuidInstallationIdProvider(mockSharedPreferences)

        val id = mockSharedPreferences.backingStorage[PREFERENCE_DEVICE_ID]
        assertNotNull(id)
        assertEquals(id, deviceIdProvider.id())
    }

    @Test
    fun loadsIdFromSharedPreferences() {
        val id = "00000000-0000-0000-0000-000000000000"
        mockSharedPreferences.backingStorage[PREFERENCE_DEVICE_ID] = id

        val deviceIdProvider = RandomUuidInstallationIdProvider(mockSharedPreferences)
        assertEquals(id, deviceIdProvider.id())
    }
}
