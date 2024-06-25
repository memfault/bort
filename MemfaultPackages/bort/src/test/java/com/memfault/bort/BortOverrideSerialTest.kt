package com.memfault.bort

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class BortOverrideSerialTest {
    private val sharedPreferences = makeFakeSharedPreferences()
    private var isBortLite: Boolean = false
    private val bortSystemCapabilities: BortSystemCapabilities = mockk {
        every { isBortLite() } answers { isBortLite }
    }
    private val bortOverrideSerial: OverrideSerial = BortOverrideSerial(
        sharedPreferences = sharedPreferences,
        bortSystemCapabilities = bortSystemCapabilities,
    )

    @Test
    fun overridesSerialIfBortLite() {
        isBortLite = true
        bortOverrideSerial.overriddenSerial = "override"
        assertThat(bortOverrideSerial.overriddenSerial).isEqualTo("override")
    }

    @Test
    fun doesNotOverrideSerialIfNotBortLite() {
        isBortLite = false
        bortOverrideSerial.overriddenSerial = "override"
        assertThat(bortOverrideSerial.overriddenSerial).isEqualTo(null)
    }
}
