package com.memfault.bort

const val INTENT_ACTION_BUG_REPORT_REQUESTED = "com.memfault.intent.action.REQUEST_BUG_REPORT"
const val INTENT_ACTION_BUGREPORT_FINISHED = "com.memfault.intent.action.BUGREPORT_FINISHED"
const val INTENT_EXTRA_BUGREPORT_PATH = "com.memfault.intent.extra.BUGREPORT_PATH"

const val INTENT_ACTION_BORT_ENABLE = "com.memfault.intent.action.BORT_ENABLE"
const val INTENT_EXTRA_BORT_ENABLED = "com.memfault.intent.extra.BORT_ENABLED"

const val PREFERENCE_BORT_ENABLED = "com.memfault.preference.BORT_ENABLED"
const val PREFERENCE_DEVICE_ID = "com.memfault.preference.DEVICE_ID"
const val PREFERENCE_LAST_TRACKED_BOOT_COUNT = "com.memfault.preference.LAST_TRACKED_BOOT_COUNT"
const val PREFERENCE_LAST_TRACKED_LINUX_BOOT_ID = "com.memfault.preference.LAST_TRACKED_LINUX_BOOT_ID"
const val PREFERENCE_LAST_PROCESSED_DROPBOX_ENTRY_TIME_MILLIS =
    "com.memfault.preference.LAST_PROCESSED_DROPBOX_ENTRY_TIME_MILLIS"
const val PREFERENCE_LAST_PROCESSED_DROPBOX_PENDING_TIME_CHANGE =
    "com.memfault.preference.LAST_PROCESSED_DROPBOX_PENDING_TIME_CHANGE"
const val PREFERENCE_LAST_HEARTBEAT_END_TIME_JSON =
    "com.memfault.preference.PREFERENCE_LAST_HEARTBEAT_END_TIME_JSON"
const val PREFERENCE_NEXT_BATTERYSTATS_HISTORY_START_TIME =
    "com.memfault.preference.PREFERENCE_NEXT_BATTERYSTATS_HISTORY_START_TIME"
const val PREFERENCE_NEXT_LOGCAT_START_TIME_JSON =
    "com.memfault.preference.PREFERENCE_NEXT_LOGCAT_START_TIME_JSON"
const val PREFERENCE_NEXT_LOGCAT_ID =
    "com.memfault.preference.PREFERENCE_NEXT_LOGCAT_ID"

const val PREFERENCE_PENDING_BUG_REPORT_REQUEST_OPTIONS = "com.memfault.preference.PENDING_BUG_REPORT_REQUEST_OPTIONS"

const val PREFERENCE_FETCHED_SDK_SETTINGS =
    "com.memfault.preference.PREFERENCE_FETCHED_SDK_SETTINGS"

const val DEFAULT_SETTINGS_ASSET_FILENAME = "settings.json"

// Keep in sync with DUMPSTER_SERVICE_NAME in MemfaultDumpster.cpp!
const val DUMPSTER_SERVICE_NAME = "memfault_dumpster"

const val METRICS_PREFERENCE_FILE_NAME = "metrics"
const val FILE_UPLOAD_HOLDING_AREA_PREFERENCE_FILE_NAME = "file_upload_hold"
const val TOKEN_STORE_PREFERENCE_FILE_NAME_TEMPLATE = "tokens_%s"
