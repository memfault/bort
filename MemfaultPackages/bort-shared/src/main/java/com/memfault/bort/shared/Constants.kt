package com.memfault.bort.shared

const val INTENT_ACTION_BUG_REPORT_START = "com.memfault.intent.action.BUG_REPORT_START"
const val INTENT_ACTION_DROPBOX_ENTRY_ADDED = "com.memfault.intent.action.DROPBOX_ENTRY_ADDED"

const val DROPBOX_ENTRY_ADDED_RECEIVER_QUALIFIED_NAME = "com.memfault.bort.receivers.DropBoxEntryAddedReceiver"

const val APPLICATION_ID_MEMFAULT_USAGE_REPORTER = "com.memfault.usagereporter"
const val REPORTER_SERVICE_QUALIFIED_NAME = "$APPLICATION_ID_MEMFAULT_USAGE_REPORTER.ReporterService"

const val PERMISSION_REPORTER_ACCESS = "$APPLICATION_ID_MEMFAULT_USAGE_REPORTER.permission.REPORTER_ACCESS"
