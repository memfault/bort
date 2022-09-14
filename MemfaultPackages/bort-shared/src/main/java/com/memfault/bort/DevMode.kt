package com.memfault.bort

/**
 * See [RealDevMode].
 */
interface DevMode {
    fun isEnabled(): Boolean
}

object DEV_MODE_DISABLED : DevMode {
    override fun isEnabled(): Boolean = false
}
