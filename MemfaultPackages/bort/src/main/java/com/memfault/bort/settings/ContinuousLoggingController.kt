package com.memfault.bort.settings

import com.memfault.bort.DumpsterClient
import javax.inject.Inject

class ContinuousLoggingController @Inject constructor(
    private val logcatSettings: LogcatSettings,
    private val bortEnabledProvider: BortEnabledProvider,
    private val dumpsterClient: DumpsterClient,
) {
    suspend fun configureContinuousLogging() {
        if (bortEnabledProvider.isEnabled() &&
            logcatSettings.dataSourceEnabled &&
            logcatSettings.collectionMode == LogcatCollectionMode.CONTINUOUS
        ) {
            dumpsterClient.startContinuousLogging(
                filterSpecs = logcatSettings.filterSpecs,
                continuousLogDumpThresholdBytes = logcatSettings.continuousLogDumpThresholdBytes,
                continuousLogDumpThresholdTime = logcatSettings.continuousLogDumpThresholdTime,
                continuousLogDumpWrappingTimeout = logcatSettings.continuousLogDumpWrappingTimeout,
            )
        } else {
            dumpsterClient.stopContinuousLogging()
        }
    }
}
