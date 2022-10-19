package com.memfault.bort.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LogcatCollectionMode {
    /**
     * When set, logcat will run periodically using an interval defined by collectionInterval.
     */
    @SerialName("periodic")
    PERIODIC,

    /**
     * When set, logcat will run continuously in the background and periodically emit log snippets.
     */
    @SerialName("continuous")
    CONTINUOUS,
}
