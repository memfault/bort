package com.memfault.bort

/**
 * Locally overridden serial number.
 *
 * This should only ever be used for Bort Lite (it does not affect bort-ota's fallback serial mechanism, so is not
 * safe to use there).
 */
interface OverrideSerial {
    var overriddenSerial: String?
}

val NoOpOverrideSerial = object : OverrideSerial {
    override var overriddenSerial: String?
        get() = null
        set(_) = Unit
}
