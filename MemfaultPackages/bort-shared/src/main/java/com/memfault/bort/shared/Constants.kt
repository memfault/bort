package com.memfault.bort.shared

const val INTENT_ACTION_BUG_REPORT_START = "com.memfault.intent.action.BUG_REPORT_START"

const val INTENT_EXTRA_BUG_REPORT_MINIMAL_MODE_BOOL = "com.memfault.intent.extra.BUG_REPORT_MINIMAL_MODE_BOOL"
const val INTENT_EXTRA_BUG_REPORT_REQUEST_ID = "com.memfault.intent.extra.BUG_REPORT_REQUEST_ID"
const val BUG_REPORT_REQUEST_ID_MAXIMUM_LENGTH = 40
const val INTENT_EXTRA_BUG_REPORT_REQUEST_REPLY_RECEIVER = "com.memfault.intent.extra.BUG_REPORT_REQUEST_REPLY_RECEIVER"
const val INTENT_EXTRA_BUG_REPORT_REQUEST_TIMEOUT_MS = "com.memfault.intent.extra.BUG_REPORT_REQUEST_TIMEOUT_MS"

const val INTENT_ACTION_BUG_REPORT_REQUEST_REPLY = "com.memfault.intent.action.BUG_REPORT_REQUEST_REPLY"
const val INTENT_EXTRA_BUG_REPORT_REQUEST_STATUS = "com.memfault.intent.extra.BUG_REPORT_REQUEST_STATUS"

const val INTENT_ACTION_DROPBOX_ENTRY_ADDED = "com.memfault.intent.action.DROPBOX_ENTRY_ADDED"

const val DROPBOX_ENTRY_ADDED_RECEIVER_QUALIFIED_NAME = "com.memfault.bort.receivers.DropBoxEntryAddedReceiver"

const val APPLICATION_ID_MEMFAULT_USAGE_REPORTER = "com.memfault.usagereporter"
const val REPORTER_SERVICE_QUALIFIED_NAME = "$APPLICATION_ID_MEMFAULT_USAGE_REPORTER.ReporterService"

const val PERMISSION_REPORTER_ACCESS = "$APPLICATION_ID_MEMFAULT_USAGE_REPORTER.permission.REPORTER_ACCESS"

const val INTENT_ACTION_OTA_SETTINGS_CHANGED = "com.memfault.intent.action.OTA_SETTINGS_CHANGED"
const val OTA_RECEIVER_CLASS = "com.memfault.bort.ota.lib.BootCompleteReceiver"

const val CLIENT_SERVER_FILE_UPLOAD_DROPBOX_TAG = "memfault_file_upload"
const val CLIENT_SERVER_SETTINGS_UPDATE_DROPBOX_TAG = "memfault_settings_update"

// Keep in sync with DUMPSTER_SERVICE_NAME in MemfaultDumpster.cpp!
const val DUMPSTER_SERVICE_NAME = "memfault_dumpster"

const val BASIC_COMMAND_TIMEOUT_MS: Long = 5_000

const val DEFAULT_SETTINGS_ASSET_FILENAME = "settings.json"

const val CONTINUOUS_LOG_CONFIG_VERSION = 1
const val CONTINUOUS_LOG_VERSION = "version"
const val CONTINUOUS_LOG_FILTER_SPECS = "filterSpecs"
const val CONTINUOUS_LOG_DUMP_THRESHOLD_BYTES = "dumpThresholdBytes"
const val CONTINUOUS_LOG_DUMP_THRESHOLD_TIME_MS = "dumpThresholdTimeMs"
const val CONTINUOUS_LOG_DUMP_WRAPPING_TIMEOUT_MS = "dumpWrappingTimeoutMs"
