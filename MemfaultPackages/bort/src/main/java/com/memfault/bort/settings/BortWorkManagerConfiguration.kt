package com.memfault.bort.settings

interface BortWorkManagerConfiguration {
    /**
     * Configuration for [androidx.work.Configuration.Builder.setMinimumLoggingLevel].
     *
     * [Int] value should correspond to the [android.util.Log] log priority constants.
     */
    val logLevel: Int
}
