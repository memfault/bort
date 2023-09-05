package com.memfault.bort.shared

import com.memfault.bort.shared.JitterDelayProvider.ApplyJitter

fun interface JitterDelayConfiguration {
    fun applyJitter(): ApplyJitter
}
