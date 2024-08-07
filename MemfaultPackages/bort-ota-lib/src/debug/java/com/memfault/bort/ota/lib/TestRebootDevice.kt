package com.memfault.bort.ota.lib

import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@ContributesBinding(SingletonComponent::class, replaces = [RealRebootDevice::class])
class TestRebootDevice @Inject constructor() : RebootDevice {
    override fun invoke() {
        Logger.test("reboot triggered")
    }
}
