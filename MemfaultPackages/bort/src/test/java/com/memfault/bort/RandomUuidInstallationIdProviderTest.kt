package com.memfault.bort

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.Before
import org.junit.Test

class RandomUuidInstallationIdProviderTest {
    lateinit var mockSharedPreferences: FakeSharedPreferences

    @Before
    fun setUp() {
        mockSharedPreferences = makeFakeSharedPreferences()
    }

    @Test
    fun setsRandomUUIDWhenUninitialized() {
        assertThat(mockSharedPreferences.backingStorage[PREFERENCE_DEVICE_ID]).isNull()
        val deviceIdProvider = RandomUuidInstallationIdProvider(mockSharedPreferences)

        val id = mockSharedPreferences.backingStorage[PREFERENCE_DEVICE_ID]
        assertThat(id).isNotNull()
        assertThat(deviceIdProvider.id()).isEqualTo(id)
    }

    @Test
    fun loadsIdFromSharedPreferences() {
        val id = "00000000-0000-0000-0000-000000000000"
        mockSharedPreferences.backingStorage[PREFERENCE_DEVICE_ID] = id

        val deviceIdProvider = RandomUuidInstallationIdProvider(mockSharedPreferences)
        assertThat(deviceIdProvider.id()).isEqualTo(id)
    }
}
