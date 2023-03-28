package com.memfault.bort

/**
 * See [RealDevMode].
 */
interface DevMode {
    fun isEnabled(): Boolean
    fun updateMetric()
}

object DEV_MODE_DISABLED : DevMode {
    override fun isEnabled(): Boolean = false
    override fun updateMetric() = Unit
}
