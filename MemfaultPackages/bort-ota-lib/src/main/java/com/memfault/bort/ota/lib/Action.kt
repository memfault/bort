package com.memfault.bort.ota.lib

/**
 * Possible actions issued to the updater which trigger state changes. Actions can be issued by any state listener
 * or by internal components (i.e. the downloader of a recovery Ota will trigger actions when the download is complete).
 */
sealed class Action {
    /**
     * Start checking for updates.
     * @param background True if the update check was requested in a background (non-user facing) context
     */
    data class CheckForUpdate(val background: Boolean = false) : Action()

    /**
     * Start downloading the update.
     */
    object DownloadUpdate : Action()

    /**
     * The download of the update file was completed.
     */
    data class DownloadCompleted(val updateFilePath: String) : Action()

    /**
     * The download of the update file has reached a certain progress.
     */
    data class DownloadProgress(val progress: Int) : Action()

    /**
     * The download of the update file has failed.
     */
    object DownloadFailed : Action()

    /**
     * Request installation of the current update.
     */
    object InstallUpdate : Action()

    /**
     * Request a device reboot
     */
    object Reboot : Action()
}
