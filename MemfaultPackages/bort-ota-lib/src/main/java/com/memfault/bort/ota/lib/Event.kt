package com.memfault.bort.ota.lib

/**
 * One-off events that typically require user notification such as showing a notification or a Snackbar.
 */
sealed class Event {
    /**
     * The download has failed.
     */
    object DownloadFailed : Event()

    /**
     * The update has failed verification.
     */
    object VerificationFailed : Event()

    /**
     * There are no new available updates.
     */
    object NoUpdatesAvailable : Event()

    /**
     * The device rebooted and a new update was successful installed.
     */
    object RebootToUpdateSucceeded : Event()

    /**
     * The device rebooted and the update failed.
     */
    object RebootToUpdateFailed : Event()

    /**
     * The update has finished.
     */
    object UpdateFinished : Event()
}
