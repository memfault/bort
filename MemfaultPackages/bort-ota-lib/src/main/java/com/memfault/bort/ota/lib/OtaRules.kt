package com.memfault.bort.ota.lib

import android.os.RecoverySystem
import androidx.work.NetworkType
import com.memfault.bort.shared.Logger

object OtaRules {
    internal fun shouldAutoDownloadOtaUpdate(
        ota: Ota,
        otaRulesProvider: OtaRulesProvider,
    ): Boolean {
        val canDownloadNow = otaRulesProvider.downloadRules(ota).canDownloadNowAfterConstraintsSatisfied(ota)
        Logger.d("shouldAutoDownloadOtaUpdate: $ota canDownloadNow = $canDownloadNow")
        return canDownloadNow
    }

    internal fun shouldAutoInstallOtaUpdate(
        ota: Ota,
        otaRulesProvider: OtaRulesProvider,
    ): Boolean {
        val canInstallNow = otaRulesProvider.installRules(ota).canInstallNowAfterConstraintsSatisfied(ota)
        Logger.d("shouldAutoInstallOtaUpdate: $ota calInstallNow = $canInstallNow")
        return canInstallNow
    }
}

interface OtaRulesProvider {
    fun downloadRules(ota: Ota): DownloadOtaRules
    fun installRules(ota: Ota): InstallOtaRules
}

data class DownloadOtaRules(
    /**
     * Can this OTA binary be downloaded now?
     *
     * Run custom checks here.
     *
     * For recovery-based updates, this gates Bort downloading the binary.
     *
     * For A/B updates, this gates the call to [UpdateEngine.applyPayload], which will download the binary and apply it
     * to the inactive partition, but will not reboot the device.
     *
     * Bort will schedule a task which has all of the constraints configured below. Once that task runs, it will execute
     * [canDownloadNowAfterConstraintsSatisfied] to decide whether or not to download.
     */
    val canDownloadNowAfterConstraintsSatisfied: (Ota) -> Boolean,
    /**
     * Override network constraint?
     *
     * If [overrideNetworkConstraint] is null, the default constraint (configured in Memfault dashboard) is used.
     *
     * Set [overrideNetworkConstraint] to override the default. This constraint is used to schedule the OTA download
     * task.
     */
    val overrideNetworkConstraint: NetworkType?,
    /**
     * Set a https://developer.android.com/reference/androidx/work/Constraints#requiresStorageNotLow() constraint on the
     * task?
     */
    val requiresStorageNotLowConstraint: Boolean,
    /**
     * Set a https://developer.android.com/reference/androidx/work/Constraints#requiresBatteryNotLow() constraint on the
     * task?
     */
    val requiresBatteryNotLowConstraint: Boolean,
    /**
     * Set a https://developer.android.com/reference/androidx/work/Constraints#requiresCharging() constraint on the
     * task?
     */
    val requiresChargingConstraint: Boolean,
    /**
     * Optionally, the download worker for A/B updates can use a foreground service. This aims to keep the Bort OTA app
     * running for the duration of the download (past the 10 minutes which the WorkMamager job allows).
     *
     * If the Bort OTA app is killed while the download is in-progress, the download (managed by UpdateEngine) will
     * continue, but Bort won't be able to immediately reboot the device on completion. If Bort OTA is killed, it
     * will recover when restarted within 15 minutes by a periodic job, and continue the install process.
     *
     * If this is desired, return true here, otherwise false (in which case the job will run for up to 10 minutes).
     *
     * This is only used for A/B updates (Recovery-based updates always use a foreground service while the Bort OTA app
     * is downloading).
     */
    val useForegroundServiceForAbDownloads: Boolean,
)

data class InstallOtaRules(
    /**
     * Can this OTA binary be installed now?
     *
     * Run custom checks here.
     *
     * For recovery-based updates, this gates the call to [RecoverySystem.installPackage], which will reboot the device
     * to install the update.
     *
     * For A/B updates, this gates rebooting the device into the freshly updated partition (the update was already
     * written to the inactive partition when downloaded).
     *
     * Bort will schedule a task which has all of the constraints configured below. Once that task runs, it will execute
     * [canInstallNowAfterConstraintsSatisfied] to decide whether or not to install.
     */
    val canInstallNowAfterConstraintsSatisfied: (Ota) -> Boolean,
    /**
     * Set a https://developer.android.com/reference/androidx/work/Constraints#requiresStorageNotLow() constraint on the
     * task?
     */
    val requiresStorageNotLowConstraint: Boolean,
    /**
     * Set a https://developer.android.com/reference/androidx/work/Constraints#requiresBatteryNotLow() constraint on the
     * task?
     */
    val requiresBatteryNotLowConstraint: Boolean,
    /**
     * Set a https://developer.android.com/reference/androidx/work/Constraints#requiresCharging() constraint on the
     * task?
     */
    val requiresChargingConstraint: Boolean,
)
