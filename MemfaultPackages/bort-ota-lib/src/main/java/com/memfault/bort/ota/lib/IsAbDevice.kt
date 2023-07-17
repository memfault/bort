package com.memfault.bort.ota.lib

import com.memfault.bort.android.SystemPropertiesProxy
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

fun interface IsAbDevice : () -> Boolean

@ContributesBinding(SingletonComponent::class)
class RealIsAbDevice @Inject constructor() : IsAbDevice {
    override fun invoke(): Boolean = !SystemPropertiesProxy.get("ro.boot.slot_suffix").isNullOrBlank() ||
        SystemPropertiesProxy.get("ro.build.ab_update") == "true"
}
