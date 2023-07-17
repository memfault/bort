package com.memfault.bort.ota.lib

import android.app.Application
import com.memfault.bort.shared.InternalMetric
import com.memfault.bort.shared.InternalMetric.Companion.sendMetric
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

fun interface MetricLogger {
    fun addMetric(metric: InternalMetric)
}

@ContributesBinding(SingletonComponent::class)
class RealMetricLogger @Inject constructor(
    private val context: Application,
) : MetricLogger {
    override fun addMetric(metric: InternalMetric) = context.sendMetric(metric)
}

internal const val TAG_BOOT_COMPLETED = "ota.boot.completed"
internal const val PARAM_APP_VERSION_NAME = "appVersionName"
internal const val PARAM_APP_VERSION_CODE = "appVersionCode"
internal const val TAG_INSTALL_RECOVERY = "ota.install.recovery"
internal const val TAG_INSTALL_RECOVERY_FAILED = "ota.install.recovery.failed"
internal const val TAG_RECOVERY_VERIFICATION_FAILED = "ota.install.recovery.verification.failed"
internal const val TAG_OTA_DOWNLOAD_STARTED = "ota.download.started"
internal const val TAG_OTA_DOWNLOAD_COMPLETED = "ota.download.completed"
internal const val TAG_OTA_DOWNLOAD_ERROR = "ota.download.error"
internal const val PARAM_URL = "url"
internal const val PARAM_OFFSET = "offset"
internal const val PARAM_DURATION_MS = "durationMs"
internal const val PARAM_BYTES = "bytes"
internal const val PARAM_BYTES_PER_S = "bytes_per_s"
