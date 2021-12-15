package com.memfault.bort

import android.content.SharedPreferences
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.shared.PreferenceKeyProvider
import javax.inject.Inject

/** A preference-backed provider of the user's opt in state. */
class PreferenceBortEnabledProvider @Inject constructor(
    sharedPreferences: SharedPreferences,
) : PreferenceKeyProvider<Boolean>(
    sharedPreferences = sharedPreferences,
    defaultValue = false,
    preferenceKey = PREFERENCE_BORT_ENABLED,
),
    BortEnabledProvider {
    override fun setEnabled(isOptedIn: Boolean) = setValue(isOptedIn)

    override fun isEnabled(): Boolean = getValue()
    override fun requiresRuntimeEnable(): Boolean = true
}
