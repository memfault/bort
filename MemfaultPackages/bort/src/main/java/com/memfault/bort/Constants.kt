package com.memfault.bort

const val INTENT_ACTION_BUG_REPORT_REQUESTED = "com.memfault.intent.action.REQUEST_BUG_REPORT"
const val INTENT_ACTION_BUGREPORT_FINISHED = "com.memfault.intent.action.BUGREPORT_FINISHED"
const val INTENT_ACTION_BUG_REPORT_START = "com.memfault.intent.action.BUG_REPORT_START"
const val INTENT_ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
const val INTENT_ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"
const val INTENT_EXTRA_BUGREPORT_PATH = "com.memfault.intent.extra.BUGREPORT_PATH"
const val INTENT_EXTRA_SDK_EVENT_NAME = "com.memfault.intent.extra.SDK_EVENT_NAME"
const val APPLICATION_ID_MEMFAULT_USAGE_REPORTER = "com.memfault.usagereporter"

const val INTENT_ACTION_BORT_ENABLE = "com.memfault.intent.action.BORT_ENABLE"
const val INTENT_EXTRA_BORT_ENABLED = "com.memfault.intent.extra.BORT_ENABLED"

const val PREFERENCE_BORT_ENABLED = "com.memfault.preference.BORT_ENABLED"
const val PREFERENCE_DEVICE_ID = "com.memfault.preference.DEVICE_ID"
const val PREFERENCE_LAST_TRACKED_BOOT_COUNT = "com.memfault.preference.LAST_TRACKED_BOOT_COUNT"

// Keep in sync with DUMPSTER_SERVICE_NAME in MemfaultDumpster.cpp!
const val DUMPSTER_SERVICE_NAME = "memfault_dumpster"
