package com.memfault.bort.ota.lib

import android.content.SharedPreferences
import com.memfault.bort.ota.lib.TestOtaModePreferenceProvider.Companion.AB
import com.memfault.bort.shared.PreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@ContributesBinding(SingletonComponent::class, replaces = [RealIsAbDevice::class])
class TestIsAbDevice @Inject constructor(
    private val testOtaMode: TestOtaModePreferenceProvider,
) : IsAbDevice {
    override fun invoke(): Boolean = (testOtaMode.getValue() == AB)
}

class TestOtaModePreferenceProvider @Inject constructor(
    sharedPreferences: SharedPreferences,
) : PreferenceKeyProvider<String>(
    defaultValue = RECOVERY,
    preferenceKey = "test_mode",
    sharedPreferences = sharedPreferences,
    // We kill the app right after setting this; use commit so that the write is not lost.
    commit = true,
) {
    companion object {
        const val RECOVERY = "recovery"
        const val AB = "ab"
    }
}
