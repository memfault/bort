package com.memfault.bort

import android.content.SharedPreferences
import com.memfault.bort.shared.CachedPreferenceKeyProvider
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores a locally overridden serial number for this device. Only functions when `isBortLite()`.
 *
 * Do not use this in bort-ota.
 */
@ContributesBinding(SingletonComponent::class)
@Singleton
class BortOverrideSerial @Inject constructor(
    sharedPreferences: SharedPreferences,
    private val bortSystemCapabilities: BortSystemCapabilities,
) : OverrideSerial {
    private val pref = object : CachedPreferenceKeyProvider<String>(
        sharedPreferences = sharedPreferences,
        defaultValue = UNSET,
        preferenceKey = PREFERENCE_OVERRIDDEN_SERIAL,
    ) {}

    override var overriddenSerial: String?
        get() {
            if (!bortSystemCapabilities.isBortLite()) {
                return null
            }
            val value = pref.getValue()
            if (value == UNSET) {
                return null
            }
            return value
        }
        set(value) {
            if (!bortSystemCapabilities.isBortLite()) {
                Logger.d("BortOverrideSerial: cannot override serial - not Bort Lite")
                return
            }
            if (value == null) {
                Logger.d("BortOverrideSerial: removing override")
                pref.remove()
                return
            }
            Logger.d("BortOverrideSerial: overriding device serial: $value")
            pref.setValue(value)
        }

    companion object {
        private const val PREFERENCE_OVERRIDDEN_SERIAL = "com.memfault.preference.OVERRIDDEN_SERIAL"
        private const val UNSET = ""
    }
}
