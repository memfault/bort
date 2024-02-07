package com.memfault.bort.boot

import android.content.SharedPreferences
import com.memfault.bort.PREFERENCE_LAST_TRACKED_BOOT_COUNT
import com.memfault.bort.shared.PreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

interface LastTrackedBootCountProvider {
    var bootCount: Int
}

@ContributesBinding(SingletonComponent::class, boundType = LastTrackedBootCountProvider::class)
class RealLastTrackedBootCountProvider
@Inject constructor(
    sharedPreferences: SharedPreferences,
) : LastTrackedBootCountProvider, PreferenceKeyProvider<Int>(
    sharedPreferences = sharedPreferences,
    defaultValue = 0,
    preferenceKey = PREFERENCE_LAST_TRACKED_BOOT_COUNT,
) {
    override var bootCount
        get() = super.getValue()
        set(value) = super.setValue(value)
}
