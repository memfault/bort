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
