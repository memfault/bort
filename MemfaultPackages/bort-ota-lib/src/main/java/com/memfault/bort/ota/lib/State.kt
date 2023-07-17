package com.memfault.bort.ota.lib

import kotlinx.serialization.Serializable

/**
 * Possible states issued by the updater.
 *
 * Note when changing this: consider that it is serialized + persisted. Only add new fields if they have default values.
 */
@Serializable
sealed class State {
    /**
     * The Updater is Idle.
     */
    @Serializable
    object Idle : State()

    /**
     * The updater is currently checking the remote endpoint for updates.
     */
    @Serializable
    object CheckingForUpdates : State()

    /**
     * There is an update available.
     * @param ota The update metadata.
     * @param background The update was found by a background job.
     */
    @Serializable
    data class UpdateAvailable(val ota: Ota, val showNotification: Boolean? = null) : State()

    /**
     * The update is downloading.
     * @param ota The update metadata.
     * @param progress The download progress.
     */
    @Serializable
    data class UpdateDownloading(val ota: Ota, val progress: Int = 0) : State()

    /**
     * The update is ready to install.
     * @param ota The update metadata.
     * @param path The path to the update file. Depending on the handler implementation, it may be null. Depending on the handler implementation, it may be null. Depending on the handler implementation, it may be null. Depending on the handler implementation, it may be null.
     */
    @Serializable
    data class ReadyToInstall(val ota: Ota, val path: String? = null) : State()

    /**
     * The device was rebooted to install the update.
     * @param ota The update metadata.
     * @param updatingFromVersion The version we are updating from.
     */
    @Serializable
    data class RebootedForInstallation(val ota: Ota, val updatingFromVersion: String) : State()

    /**
     * The update has failed.
     * @param ota The update metadata.
     * @param message A description on why the update failed.
     */
    @Serializable
    data class UpdateFailed(val ota: Ota, val message: String) : State()

    /**
     * The update is finalizing (preparing partitions, optimizing applications). Only used in A/B flows.
     * @param ota The update metadata.
     * @param progress The finalization process
     */
    @Serializable
    data class Finalizing(val ota: Ota, val progress: Int = 0) : State()

    /**
     * The update has finished and the device must reboot. Only used in A/B flows.
     * @param ota The update metadata.
     */
    @Serializable
    data class RebootNeeded(val ota: Ota) : State()
}

fun State.allowsUpdateCheck() = when (this) {
    State.Idle, is State.UpdateAvailable, is State.UpdateFailed -> true
    else -> false
}
