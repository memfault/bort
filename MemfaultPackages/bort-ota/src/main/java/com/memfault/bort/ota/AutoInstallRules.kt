package com.memfault.bort.ota

import android.content.Context

/**
 * Custom rules defining whether an OTA update can be auto-installed right now on this device.
 */
fun custom_canAutoInstallOtaUpdateNow(context: Context): Boolean {
    // TODO Customize this method to add and custom logic which determines whether an OTA update can be
    // auto-installed right now on this device.
    return true
}
