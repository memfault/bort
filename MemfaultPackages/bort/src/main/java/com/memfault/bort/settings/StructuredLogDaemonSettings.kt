package com.memfault.bort.settings

import com.memfault.bort.time.BoxedDuration
import com.memfault.bort.time.DurationAsMillisecondsLong
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StructuredLogDaemonSettings(
    @SerialName("structured_log.data_source_enabled")
    val structuredLogDataSourceEnabled: Boolean,

    @SerialName("structured_log.dump_period_ms")
    @Serializable(with = DurationAsMillisecondsLong::class)
    val structuredLogDumpPeriod: BoxedDuration,

    @SerialName("structured_log.max_message_size_bytes")
    val structuredLogMaxMessageSizeBytes: Long,

    @SerialName("structured_log.min_storage_threshold_bytes")
    val structuredLogMinStorageThresholdBytes: Long,

    @SerialName("structured_log.num_events_before_dump")
    val structuredLogNumEventsBeforeDump: Long,

    @SerialName("structured_log.rate_limiting_settings")
    val structuredLogRateLimitingSettings: RateLimitingSettings,

    @SerialName("structured_log.metric_report_enabled")
    val structuredLogMetricReportEnabled: Boolean,

    @SerialName("structured_log.high_res_metrics_enabled")
    val structuredLogHighResMetricsEnabled: Boolean,
)
