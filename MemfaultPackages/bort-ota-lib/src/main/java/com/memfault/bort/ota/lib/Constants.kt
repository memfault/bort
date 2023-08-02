package com.memfault.bort.ota.lib

const val OTA_PATH = "/data/ota_package/ota.zip"
const val BORT_SOFTWARE_UPDATE_SETTINGS_PROVIDER = "com.memfault.bort.softwareupdate.settings"
const val OTA_SETTINGS_COLUMN_LEGACY = 0
const val OTA_SETTINGS_COLUMN_FULL = 1

const val DEFAULT_STATE_PREFERENCE_FILE = "updater_preferences"
const val STATE_KEY = "com.memfault.bort.updater.state"
const val CACHED_OTA_KEY = "com.memfault.bort.updater.cached.ota"
const val DOWNLOAD_PROGRESS_KEY = "com.memfault.bort.updater.download_progress"

const val PERIODIC_UPDATE_WORK = "com.memfault.bort.updater.periodic_update_check_work"
const val SCHEDULED_DOWNLOAD_WORK = "com.memfault.bort.updater.download_work"
const val SCHEDULED_INSTALL_WORK = "com.memfault.bort.updater.install_work"
const val PERIODIC_DOWNLOAD_CHECK = "com.memfault.bort.updater.periodic_download_completion_check_work"
