package com.memfault.bort.android

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports which hardware features are present on this device, so that metrics which depend on
 * hardware the device doesn't have (cellular radio, wifi, bluetooth, GPS, screen, battery,
 * camera) aren't collected as always-empty/zero.
 */
interface DeviceFeatures {
    val hasTelephony: Boolean
    val hasWifi: Boolean
    val hasBluetooth: Boolean
    val hasGps: Boolean
    val hasScreen: Boolean
    val hasCamera: Boolean
    val hasBattery: Boolean
}

@Singleton
@ContributesBinding(SingletonComponent::class)
class RealDeviceFeatures @Inject constructor(
    private val packageManager: PackageManager,
    private val application: Application,
) : DeviceFeatures {
    override val hasTelephony: Boolean by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) }
    override val hasWifi: Boolean by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI) }
    override val hasBluetooth: Boolean by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }
    override val hasGps: Boolean by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS) }

    override val hasScreen: Boolean by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
    }
    override val hasCamera: Boolean by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    /**
     * There is no PackageManager feature for battery presence. Use the sticky ACTION_BATTERY_CHANGED
     * broadcast's EXTRA_PRESENT extra instead. Fail open (assume present) if unavailable, so we don't
     * silently drop battery data on devices where this can't be determined.
     */
    override val hasBattery: Boolean by lazy {
        application.stickyIntent(Intent.ACTION_BATTERY_CHANGED)
            ?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) ?: true
    }
}
