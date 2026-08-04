package com.memfault.bort.android

/**
 * Plain test double: every feature present by default, override individual flags per-test. Has no
 * Android dependencies, so it can be constructed directly in tests without mocking PackageManager etc.
 */
data class FakeDeviceFeatures(
    override val hasTelephony: Boolean = true,
    override val hasWifi: Boolean = true,
    override val hasBluetooth: Boolean = true,
    override val hasGps: Boolean = true,
    override val hasScreen: Boolean = true,
    override val hasCamera: Boolean = true,
    override val hasBattery: Boolean = true,
) : DeviceFeatures
