package com.memfault.bort.ota.lib

import android.content.Context
import android.os.RecoverySystem
import com.memfault.bort.ota.lib.download.DownloadOtaService
import com.memfault.bort.shared.InternalMetric
import com.memfault.bort.shared.InternalMetric.Companion.OTA_INSTALL_RECOVERY
import com.memfault.bort.shared.InternalMetric.Companion.OTA_INSTALL_RECOVERY_FAILED
import com.memfault.bort.shared.InternalMetric.Companion.OTA_INSTALL_RECOVERY_VERIFICATION_FAILED
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.SoftwareUpdateSettings
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * This interface abstracts recovery interactions. On real devices, the RealRecoveryInterface will use RecoverySystem
 * to interact with the system's recovery but this abstraction is useful for testing.
 */
interface RecoveryInterface {
    fun verifyOrThrow(path: File)
    fun install(path: File)
}

/**
 * An action handler that implements the recovery update flow. A recovery-based flow is a multi-step process that
 * requires:
 *  1) Checking for updates in remote endpoint
 *  2) Downloading the update
 *  3) Verifying the update package
 *  4) Install the update package
 */
class RecoveryBasedUpdateActionHandler(
    private val softwareUpdateChecker: SoftwareUpdateChecker,
    private val recoveryInterface: RecoveryInterface,
    private val startUpdateDownload: (url: String) -> Unit,
    private val currentSoftwareVersion: String,
    private val metricLogger: MetricLogger,
) : UpdateActionHandler {
    override suspend fun handle(
        state: State,
        action: Action,
        setState: suspend (state: State) -> Unit,
        triggerEvent: suspend (event: Event) -> Unit
    ) {
        when (action) {
            is Action.CheckForUpdate -> {
                if (state is State.Idle || state is State.UpdateFailed) {
                    setState(State.CheckingForUpdates)
                    val ota = softwareUpdateChecker.getLatestRelease()
                    if (ota == null) {
                        setState(State.Idle)
                        triggerEvent(Event.NoUpdatesAvailable)
                    } else {
                        setState(State.UpdateAvailable(ota, background = action.background))
                    }
                }
            }
            is Action.DownloadUpdate -> {
                if (state is State.UpdateAvailable) {
                    setState(State.UpdateDownloading(state.ota))
                    startUpdateDownload(state.ota.url)
                }
            }
            is Action.DownloadProgress -> {
                if (state is State.UpdateDownloading) {
                    setState(state.copy(progress = action.progress))
                }
            }
            is Action.DownloadCompleted -> {
                if (state is State.UpdateDownloading) {
                    if (verifyUpdate(File(action.updateFilePath))) {
                        setState(State.ReadyToInstall(state.ota, action.updateFilePath))
                    } else {
                        setState(State.Idle)
                        triggerEvent(Event.VerificationFailed)
                    }
                }
            }
            is Action.DownloadFailed -> {
                if (state is State.UpdateDownloading) {
                    setState(State.UpdateAvailable(state.ota))
                    triggerEvent(Event.DownloadFailed)
                }
            }
            is Action.InstallUpdate -> {
                if (state is State.ReadyToInstall && state.path != null) {
                    setState(State.RebootedForInstallation(state.ota, currentSoftwareVersion))
                    if (!installUpdate(File(state.path))) {
                        // Back to square one, this should not happen, ever. At this point the update is verified
                        // and calling install will necessarily succeed. But go back to idle just in case.
                        setState(State.Idle)
                    }
                    // Do nothing, at this point the device is scheduled to reboot.
                }
            }
        }
    }

    private suspend fun installUpdate(updatePath: File): Boolean =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                try {
                    metricLogger.addMetric(InternalMetric(OTA_INSTALL_RECOVERY, synchronous = true))
                    Logger.i(TAG_INSTALL_RECOVERY, mapOf())
                    recoveryInterface.install(updatePath)
                    continuation.resume(true) {}
                } catch (ex: Exception) {
                    // A verification failure is non-recoverable, the OTA file is likely damaged
                    Logger.i(TAG_INSTALL_RECOVERY_FAILED, mapOf(), ex)
                    metricLogger.addMetric(InternalMetric(OTA_INSTALL_RECOVERY_FAILED))
                    updatePath.delete()
                    ex.printStackTrace()
                    continuation.resume(false) {}
                }
            }
        }

    private suspend fun verifyUpdate(
        updateFile: File,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) = withContext(dispatcher) {
        suspendCancellableCoroutine<Boolean> { continuation ->
            try {
                recoveryInterface.verifyOrThrow(updateFile)
                continuation.resume(true) {}
            } catch (ex: Exception) {
                ex.printStackTrace()
                Logger.i(TAG_RECOVERY_VERIFICATION_FAILED, mapOf(), ex)
                metricLogger.addMetric(InternalMetric(OTA_INSTALL_RECOVERY_VERIFICATION_FAILED))
                // If verification failed, the file is corrupted and there is nothing we can do, delete it
                updateFile.delete()
                continuation.resume(false) {}
            }
        }
    }
}

class RealRecoveryInterface(
    private val context: Context
) : RecoveryInterface {
    override fun verifyOrThrow(path: File) {
        RecoverySystem.verifyPackage(path, {}, null)
    }

    override fun install(path: File) {
        RecoverySystem.installPackage(context, path)
    }
}

fun realRecoveryBasedUpdateActionHandler(
    context: Context,
    settings: SoftwareUpdateSettings,
): UpdateActionHandler {
    val metricLogger = RealMetricLogger(context)
    return RecoveryBasedUpdateActionHandler(
        softwareUpdateChecker = realSoftwareUpdateChecker(settings, metricLogger),
        startUpdateDownload = { url -> DownloadOtaService.download(context, url) },
        recoveryInterface = RealRecoveryInterface(context),
        currentSoftwareVersion = settings.currentVersion,
        metricLogger = metricLogger,
    )
}
