package com.memfault.bort.ota.lib

import android.app.Application
import android.os.RecoverySystem
import com.memfault.bort.IO
import com.memfault.bort.ota.lib.download.DownloadOtaService
import com.memfault.bort.shared.BuildConfig
import com.memfault.bort.shared.InternalMetric
import com.memfault.bort.shared.InternalMetric.Companion.OTA_INSTALL_RECOVERY
import com.memfault.bort.shared.InternalMetric.Companion.OTA_INSTALL_RECOVERY_FAILED
import com.memfault.bort.shared.InternalMetric.Companion.OTA_INSTALL_RECOVERY_VERIFICATION_FAILED
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * This interface abstracts recovery interactions. On real devices, the RealRecoveryInterface will use RecoverySystem
 * to interact with the system's recovery but this abstraction is useful for testing.
 */
interface RecoveryInterface {
    fun verifyOrThrow(path: File)
    fun install(path: File)
}

fun interface StartUpdateDownload : (String) -> Unit

@ContributesBinding(SingletonComponent::class)
class RealStartUpdatedownload @Inject constructor(
    private val application: Application,
) : StartUpdateDownload {
    override fun invoke(url: String) {
        DownloadOtaService.download(application, url)
    }
}

/**
 * An action handler that implements the recovery update flow. A recovery-based flow is a multi-step process that
 * requires:
 *  1) Checking for updates in remote endpoint
 *  2) Downloading the update
 *  3) Verifying the update package
 *  4) Install the update package
 */

class RecoveryBasedUpdateActionHandler @Inject constructor(
    private val recoveryInterface: RecoveryInterface,
    private val startUpdateDownload: StartUpdateDownload,
    private val metricLogger: MetricLogger,
    private val updater: Updater,
    private val scheduleDownload: ScheduleDownload,
    private val softwareUpdateChecker: SoftwareUpdateChecker,
    private val application: Application,
    private val otaRulesProvider: OtaRulesProvider,
    private val settingsProvider: SoftwareUpdateSettingsProvider,
    @IO private val ioCoroutineContext: CoroutineContext,
) : UpdateActionHandler {
    override fun initialize() = Unit

    override suspend fun handle(
        state: State,
        action: Action,
    ) {
        fun logActionNotAllowed() = Logger.i("Action $action not allowed in state $state")

        when (action) {
            is Action.CheckForUpdate -> {
                if (state.allowsUpdateCheck()) {
                    updater.setState(State.CheckingForUpdates)
                    val ota = softwareUpdateChecker.getLatestRelease()
                    if (ota == null) {
                        updater.setState(State.Idle)
                        updater.triggerEvent(Event.NoUpdatesAvailable)
                    } else {
                        handleUpdateAvailable(
                            updater = updater,
                            ota = ota,
                            action = action,
                            scheduleDownload = scheduleDownload,
                        )
                    }
                } else {
                    logActionNotAllowed()
                }
            }

            is Action.DownloadUpdate -> {
                if (state is State.UpdateAvailable) {
                    updater.setState(State.UpdateDownloading(state.ota))
                    startUpdateDownload(state.ota.url)
                } else {
                    logActionNotAllowed()
                }
            }

            is Action.DownloadProgress -> {
                if (state is State.UpdateDownloading) {
                    updater.setState(state.copy(progress = action.progress))
                } else {
                    logActionNotAllowed()
                }
            }

            is Action.DownloadCompleted -> {
                if (state is State.UpdateDownloading) {
                    if (verifyUpdate(File(action.updateFilePath))) {
                        updater.setState(State.ReadyToInstall(state.ota, action.updateFilePath))
                        val scheduleAutoInstall = BuildConfig.OTA_AUTO_INSTALL || (state.ota.isForced == true)
                        if (scheduleAutoInstall) {
                            OtaInstallWorker.schedule(application, otaRulesProvider, state.ota)
                        }
                    } else {
                        updater.setState(State.Idle)
                        updater.triggerEvent(Event.VerificationFailed)
                    }
                } else {
                    logActionNotAllowed()
                }
            }

            is Action.DownloadFailed -> {
                if (state is State.UpdateDownloading) {
                    updater.setState(State.UpdateAvailable(state.ota, showNotification = false))
                    updater.triggerEvent(Event.DownloadFailed)
                } else {
                    logActionNotAllowed()
                }
            }

            is Action.InstallUpdate -> {
                if (state is State.ReadyToInstall && state.path != null) {
                    updater.setState(
                        State.RebootedForInstallation(
                            state.ota,
                            updatingFromVersion = settingsProvider.get().currentVersion,
                        ),
                    )
                    if (!installUpdate(File(state.path))) {
                        // Back to square one, this should not happen, ever. At this point the update is verified
                        // and calling install will necessarily succeed. But go back to idle just in case.
                        updater.setState(State.Idle)
                    }
                    // Do nothing, at this point the device is scheduled to reboot.
                } else {
                    logActionNotAllowed()
                }
            }

            else -> {
                Logger.w("Unhandled action: $action")
            }
        }
    }

    private suspend fun installUpdate(updatePath: File): Boolean =
        withContext(ioCoroutineContext) {
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
                    continuation.resume(false) {}
                }
            }
        }

    private suspend fun verifyUpdate(
        updateFile: File,
    ) = withContext(ioCoroutineContext) {
        suspendCancellableCoroutine<Boolean> { continuation ->
            try {
                recoveryInterface.verifyOrThrow(updateFile)
                continuation.resume(true) {}
            } catch (ex: Exception) {
                Logger.i(TAG_RECOVERY_VERIFICATION_FAILED, mapOf(), ex)
                metricLogger.addMetric(InternalMetric(OTA_INSTALL_RECOVERY_VERIFICATION_FAILED))
                // If verification failed, the file is corrupted and there is nothing we can do, delete it
                updateFile.delete()
                continuation.resume(false) {}
            }
        }
    }
}

@ContributesBinding(SingletonComponent::class)
class RealRecoveryInterface @Inject constructor(
    private val context: Application,
) : RecoveryInterface {
    override fun verifyOrThrow(path: File) {
        RecoverySystem.verifyPackage(path, {}, null)
    }

    override fun install(path: File) {
        RecoverySystem.installPackage(context, path)
    }
}
