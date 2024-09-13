package com.memfault.bort

import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

fun interface OverrideDeviceInfo : (DeviceInfo) -> DeviceInfo

@ContributesBinding(SingletonComponent::class, replaces = [RealDeviceInfoProvider::class])
@Singleton
class TestDeviceInfoProvider @Inject constructor(
    private val realDeviceInfoProvider: RealDeviceInfoProvider,
) : DeviceInfoProvider by realDeviceInfoProvider {

    var overrideDeviceInfo: OverrideDeviceInfo = OverrideDeviceInfo { it }

    override suspend fun getDeviceInfo(): DeviceInfo =
        overrideDeviceInfo.invoke(realDeviceInfoProvider.getDeviceInfo())
}
