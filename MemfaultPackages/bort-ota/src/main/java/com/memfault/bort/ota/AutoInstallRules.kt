package com.memfault.bort.ota

import android.app.Application
import android.os.RecoverySystem
import com.memfault.bort.ota.lib.DownloadOtaRules
import com.memfault.bort.ota.lib.InstallOtaRules
import com.memfault.bort.ota.lib.Ota
import com.memfault.bort.ota.lib.OtaRulesProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@Suppress("UNUSED_PARAMETER")
@ContributesBinding(SingletonComponent::class)
class AutoInstallRules @Inject constructor(
    private val context: Application,
) : OtaRulesProvider {
    /**
     * Custom rules defining whether an OTA update can be auto-downloaded right now on this device.
     *
     * Populate this function to define custom logic based on device state, time window, etc.
     *
     * For recovery-based updates, this gates Bort downloading the binary.
     *
     * For A/B updates, this gates the call to [UpdateEngine.applyPayload], which will download the binary and apply it
     * to the inactive partition, but will not reboot the device.
     *
     * Bort will schedule a task which has all of the constraints configured below. Once that task runs, it will execute
     * [canDownloadNowAfterConstraintsSatisfied] to decide whether or not to download.
     */
    fun canAutoDownloadOtaUpdateNow(ota: Ota): Boolean {
        // TODO Customize this method to add and custom logic which determines whether an OTA update can be downloaded
        // right now on this device.
        return true
    }

    /**
     * Configure constraints for automatically downloading OTA updates.
     */
    override fun downloadRules(ota: Ota): DownloadOtaRules {
        return DownloadOtaRules(
            // This refers to the method above.
            canDownloadNowAfterConstraintsSatisfied = this::canAutoDownloadOtaUpdateNow,
            // These constraints must be satisfied before the [canAutoDownloadOtaUpdateNow] runs.
            overrideNetworkConstraint = null,
            requiresStorageNotLowConstraint = true,
            requiresBatteryNotLowConstraint = false,
            requiresChargingConstraint = false,
        )
    }

    /**
     * Custom rules defining whether an OTA update can be auto-installed right now on this device.
     *
     * Populate this function to define custom logic based on device state, time window, etc.
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
    fun canAutoInstallOtaUpdateNow(ota: Ota): Boolean {
        // TODO Customize this method to add and custom logic which determines whether an OTA update can be installed
        // right now on this device.
        return true
    }

    override fun installRules(ota: Ota): InstallOtaRules {
        return InstallOtaRules(
            // This refers to the method above.
            canInstallNowAfterConstraintsSatisfied = this::canAutoInstallOtaUpdateNow,
            // These constraints must be satisfied before the [canAutoDownloadOtaUpdateNow] runs.
            requiresStorageNotLowConstraint = false,
            requiresBatteryNotLowConstraint = true,
            requiresChargingConstraint = false,
        )
    }
}
