package com.memfault.bort.settings

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.memfault.bort.BortJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class FetchedDeviceConfigContainerTest {
    private val response = """
    {
        "data": {
            "revision": 3344,
            "config": {
                "memfault": {
                    "bort": {
                        "sdk-settings": {
                            "battery_stats.command_timeout_ms": 60000,
                            "battery_stats.data_source_enabled": True,
                            "bort.min_log_level": 5,
                            "bort.min_structured_log_level": 3,
                            "bort.settings_update_interval_ms": 86400000,
                            "bug_report.collection_interval_ms": 86400000,
                            "bug_report.data_source_enabled": False,
                            "bug_report.first_bug_report_delay_after_boot_ms": 600000,
                            "bug_report.max_storage_bytes": 50000000,
                            "bug_report.max_stored_age_ms": 0,
                            "bug_report.max_upload_attempts": 3,
                            "bug_report.options.minimal": False,
                            "bug_report.periodic_rate_limiting_percent": 50,
                            "bug_report.request_rate_limiting_settings": {
                                "default_capacity": 3,
                                "default_period_ms": 1800000,
                                "max_buckets": 1
                            },
                            "data_scrubbing.rules": [],
                            "device_info.android_build_version_key": "",
                            "device_info.android_build_version_source": "build_fingerprint_only",
                            "device_info.android_device_serial_key": "ro.serialno",
                            "device_info.android_hardware_version_key": "ro.product.model",
                            "drop_box.anrs.rate_limiting_settings": {
                                "default_capacity": 10,
                                "default_period_ms": 900000,
                                "max_buckets": 1
                            },
                            "drop_box.data_source_enabled": True,
                            "drop_box.excluded_tags": [],
                            "drop_box.java_exceptions.rate_limiting_settings": {
                                "default_capacity": 4,
                                "default_period_ms": 900000,
                                "max_buckets": 100
                            },
                            "drop_box.kmsgs.rate_limiting_settings": {
                                "default_capacity": 10,
                                "default_period_ms": 900000,
                                "max_buckets": 1
                            },
                            "drop_box.structured_log.rate_limiting_settings": {
                                "default_capacity": 2,
                                "default_period_ms": 3600000,
                                "max_buckets": 1
                            },
                            "drop_box.tombstones.rate_limiting_settings": {
                                "default_capacity": 10,
                                "default_period_ms": 900000,
                                "max_buckets": 1
                            },
                            "file_upload_holding_area.max_stored_events_of_interest": 50,
                            "file_upload_holding_area.trailing_margin_ms": 300000,
                            "http_api.batch_mar_uploads": True,
                            "http_api.batched_mar_upload_period_ms": 3600000,
                            "http_api.call_timeout_ms": 0,
                            "http_api.connect_timeout_ms": 30000,
                            "http_api.device_base_url": "http://localhost:8000",
                            "http_api.files_base_url": "http://localhost:8000",
                            "http_api.read_timeout_ms": 0,
                            "http_api.upload_compression_enabled": True,
                            "http_api.upload_network_constraint_allow_metered_connection": True,
                            "http_api.use_mar_upload": False,
                            "http_api.write_timeout_ms": 0,
                            "logcat.collection_interval_ms": 900000,
                            "logcat.command_timeout_ms": 60000,
                            "logcat.data_source_enabled": True,
                            "logcat.filter_specs": [{"priority": "W", "tag": "*"}],
                            "logcat.kernel_oops.data_source_enabled": True,
                            "logcat.kernel_oops.rate_limiting_settings": {
                                "default_capacity": 3,
                                "default_period_ms": 21600000,
                                "max_buckets": 1
                            },
                            "logcat.process_selinux_violations": False,
                            "mar_file.rate_limiting_settings": {
                                "default_capacity": 500,
                                "default_period_ms": 3600000,
                                "max_buckets": 1
                            },
                            "metric_report.enabled": True,
                            "metrics.app_versions": [],
                            "metrics.collection_interval_ms": 3600000,
                            "metrics.data_source_enabled": True,
                            "metrics.max_num_app_versions": 50,
                            "metrics.reporter_collection_interval_ms": 600000,
                            "metrics.system_properties": ["ro.build.type"],
                            "ota.update_check_interval_ms": 43200000,
                            "package_manager.command_timeout_ms": 60000,
                            "reboot_events.data_source_enabled": True,
                            "reboot_events.rate_limiting_settings": {
                                "default_capacity": 5,
                                "default_period_ms": 900000,
                                "max_buckets": 1
                            },
                            "selinux_violation_events.data_source_enabled": false,
                            "selinux_violation_events.rate_limiting_settings": {
                                "default_capacity": 2,
                                "default_period_ms": 86400000,
                                "max_buckets": 15
                            },
                            "storage.max_client_server_file_transfer_storage_bytes": 50000000,
                            "structured_log.data_source_enabled": True,
                            "structured_log.dump_period_ms": 7200000,
                            "structured_log.max_message_size_bytes": 4096,
                            "structured_log.min_storage_threshold_bytes": 268435456,
                            "structured_log.num_events_before_dump": 1000,
                            "structured_log.rate_limiting_settings": {
                                "default_capacity": 1000,
                                "default_period_ms": 15000,
                                "max_buckets": 1
                            }
                        }
                    },
                    "sampling": {
                        "debugging.resolution": "normal",
                        "logging.resolution": "normal",
                        "monitoring.resolution": "normal"
                    }
                }
            }
        }
    }
    """.trimIndent()

    private val responseWithOther = """
    {
        "data": {
            "revision": 3344,
            "config": {
                "memfault": {
                    "bort": {
                        "sdk-settings": {
                            "battery_stats.command_timeout_ms": 60000,
                            "battery_stats.data_source_enabled": True,
                            "bort.event_log_enabled": True,
                            "bort.min_log_level": 5,
                            "bort.min_structured_log_level": 3,
                            "bort.settings_update_interval_ms": 86400000,
                            "bug_report.collection_interval_ms": 86400000,
                            "bug_report.data_source_enabled": False,
                            "bug_report.first_bug_report_delay_after_boot_ms": 600000,
                            "bug_report.max_storage_bytes": 50000000,
                            "bug_report.max_stored_age_ms": 0,
                            "bug_report.max_upload_attempts": 3,
                            "bug_report.options.minimal": False,
                            "bug_report.periodic_rate_limiting_percent": 50,
                            "bug_report.request_rate_limiting_settings": {
                                "default_capacity": 3,
                                "default_period_ms": 1800000,
                                "max_buckets": 1
                            },
                            "data_scrubbing.rules": [],
                            "device_info.android_build_version_key": "",
                            "device_info.android_build_version_source": "build_fingerprint_only",
                            "device_info.android_device_serial_key": "ro.serialno",
                            "device_info.android_hardware_version_key": "ro.product.model",
                            "drop_box.anrs.rate_limiting_settings": {
                                "default_capacity": 10,
                                "default_period_ms": 900000,
                                "max_buckets": 1
                            },
                            "drop_box.data_source_enabled": True,
                            "drop_box.excluded_tags": [],
                            "drop_box.java_exceptions.rate_limiting_settings": {
                                "default_capacity": 4,
                                "default_period_ms": 900000,
                                "max_buckets": 100
                            },
                            "drop_box.kmsgs.rate_limiting_settings": {
                                "default_capacity": 10,
                                "default_period_ms": 900000,
                                "max_buckets": 1
                            },
                            "drop_box.structured_log.rate_limiting_settings": {
                                "default_capacity": 2,
                                "default_period_ms": 3600000,
                                "max_buckets": 1
                            },
                            "drop_box.tombstones.rate_limiting_settings": {
                                "default_capacity": 10,
                                "default_period_ms": 900000,
                                "max_buckets": 1
                            },
                            "file_upload_holding_area.max_stored_events_of_interest": 50,
                            "file_upload_holding_area.trailing_margin_ms": 300000,
                            "http_api.batch_mar_uploads": True,
                            "http_api.batched_mar_upload_period_ms": 3600000,
                            "http_api.call_timeout_ms": 0,
                            "http_api.connect_timeout_ms": 30000,
                            "http_api.device_base_url": "http://localhost:8000",
                            "http_api.files_base_url": "http://localhost:8000",
                            "http_api.read_timeout_ms": 0,
                            "http_api.upload_compression_enabled": True,
                            "http_api.upload_network_constraint_allow_metered_connection": True,
                            "http_api.use_mar_upload": False,
                            "http_api.write_timeout_ms": 0,
                            "logcat.collection_interval_ms": 900000,
                            "logcat.command_timeout_ms": 60000,
                            "logcat.data_source_enabled": True,
                            "logcat.filter_specs": [{"priority": "W", "tag": "*"}],
                            "logcat.kernel_oops.data_source_enabled": True,
                            "logcat.kernel_oops.rate_limiting_settings": {
                                "default_capacity": 3,
                                "default_period_ms": 21600000,
                                "max_buckets": 1
                            },
                            "logcat.process_selinux_violations": False,
                            "mar_file.rate_limiting_settings": {
                                "default_capacity": 500,
                                "default_period_ms": 3600000,
                                "max_buckets": 1
                            },
                            "metric_report.enabled": True,
                            "metrics.app_versions": [],
                            "metrics.collection_interval_ms": 3600000,
                            "metrics.data_source_enabled": True,
                            "metrics.max_num_app_versions": 50,
                            "metrics.reporter_collection_interval_ms": 600000,
                            "metrics.system_properties": ["ro.build.type"],
                            "ota.update_check_interval_ms": 43200000,
                            "package_manager.command_timeout_ms": 60000,
                            "reboot_events.data_source_enabled": True,
                            "reboot_events.rate_limiting_settings": {
                                "default_capacity": 5,
                                "default_period_ms": 900000,
                                "max_buckets": 1
                            },
                            "selinux_violation_events.data_source_enabled": false,
                            "selinux_violation_events.rate_limiting_settings": {
                                "default_capacity": 2,
                                "default_period_ms": 86400000,
                                "max_buckets": 15
                            },
                            "storage.max_client_server_file_transfer_storage_bytes": 50000000,
                            "structured_log.data_source_enabled": True,
                            "structured_log.dump_period_ms": 7200000,
                            "structured_log.max_message_size_bytes": 4096,
                            "structured_log.min_storage_threshold_bytes": 268435456,
                            "structured_log.num_events_before_dump": 1000,
                            "structured_log.rate_limiting_settings": {
                                "default_capacity": 1000,
                                "default_period_ms": 15000,
                                "max_buckets": 1
                            }
                        }
                    },
                    "sampling": {
                        "debugging.resolution": "normal",
                        "logging.resolution": "normal",
                        "monitoring.resolution": "normal"
                    }
                },
                "square": {
                    "prop1": "val1"
                }
            }
        }
    }
    """.trimIndent()

    @Test
    fun deserializeResponse() {
        val result = BortJson.decodeFromString(DecodedDeviceConfig.serializer(), response)
        assertThat(result.revision).isEqualTo(3344)
        assertThat(result.memfault!!.bort.sdkSettings.batteryStatsDataSourceEnabled).isTrue()
        assertThat(result.memfault!!.sampling.debuggingResolution).isEqualTo("normal")
        assertThat(result.others.size).isEqualTo(0)

        val reEncoded = BortJson.encodeToString(DecodedDeviceConfig.serializer(), result)
        val reDecoded = BortJson.decodeFromString(DecodedDeviceConfig.serializer(), reEncoded)
        assertThat(reDecoded).isEqualTo(result)
    }

    @Test
    fun deserializeResponseWithOtherData() {
        val result = BortJson.decodeFromString(DecodedDeviceConfig.serializer(), responseWithOther)
        assertThat(result.revision).isEqualTo(3344)
        assertThat(result.memfault!!.bort.sdkSettings.batteryStatsDataSourceEnabled).isTrue()
        assertThat(result.memfault!!.sampling.debuggingResolution).isEqualTo("normal")
        assertThat(result.others.size).isEqualTo(1)
        val square = JsonObject(
            mapOf("prop1" to JsonPrimitive("val1")),
        )
        val others = JsonObject(
            mapOf("square" to square),
        )
        assertThat(result.others).isEqualTo(others)

        val reEncoded = BortJson.encodeToString(DecodedDeviceConfig.serializer(), result)
        val reDecoded = BortJson.decodeFromString(DecodedDeviceConfig.serializer(), reEncoded)
        assertThat(reDecoded).isEqualTo(result)
    }
}
