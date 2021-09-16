package com.memfault.bort.ota.lib

import android.content.Context
import android.content.SharedPreferences
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.SoftwareUpdateSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Possible states issued by the updater.
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
    data class UpdateAvailable(val ota: Ota, val background: Boolean = false) : State()

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

/**
 * An Factory for {@link UpdateActionHandler}.
 */
interface UpdateActionHandlerFactory {
    fun create(
        setState: suspend (state: State) -> Unit,
        triggerEvent: suspend (event: Event) -> Unit,
        settings: () -> SoftwareUpdateSettings,
    ): UpdateActionHandler
}

/**
 * An update action handler takes actions that manipulate the state and may issue events. Most work is done
 * in action handlers. The only current implementation is RecoveryBasedUpdateActionHandler which handles recovery-based
 * updates. Seamless (a/b) updates will have their own implementation in a future update.
 */
interface UpdateActionHandler {
    suspend fun handle(
        state: State,
        action: Action,
    )
}

/**
 * An interface for persisting state. Android applications may be killed at any point by the system but ideally
 * we would want to keep the update state where applicable (i.e. if there's a download ready or waiting for user
 * input).
 */
interface StateStore {
    fun store(state: State)
    fun read(): State?
}

class SharedPreferencesStateStore(
    private val sharedPreferences: SharedPreferences,
    private val stateKey: String = STATE_KEY,
) : StateStore {
    override fun store(state: State) {
        val serialized = Json { encodeDefaults = true }.encodeToString(state)
        sharedPreferences.edit()
            .putString(stateKey, serialized)
            .apply()
    }

    override fun read(): State? =
        sharedPreferences.getString(stateKey, null)?.let { serializedState ->
            try {
                Json.decodeFromString<State>(serializedState)
            } catch (ex: SerializationException) {
                null
            }
        }
}

/**
 * The updater keeps the update state and events, delegating all action handling to the passed handler.
 */
class Updater private constructor(
    actionHandlerFactory: UpdateActionHandlerFactory,
    private val stateStore: StateStore,
    private val settingsProvider: SoftwareUpdateSettingsProvider?,
    context: Context,
) {
    private val _updateState = MutableStateFlow<State>(State.Idle)
    val updateState: StateFlow<State> = _updateState

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events

    private var _settings: SoftwareUpdateSettings = fetchSettings(context)

    private val actionHandler = actionHandlerFactory.create(
        setState = ::setState,
        triggerEvent = _events::emit,
        settings = { _settings }
    )

    init {
        _updateState.value = stateStore.read() ?: State.Idle
    }

    fun settings() = _settings

    fun updateSettings(context: Context) {
        _settings = fetchSettings(context)
    }

    private fun fetchSettings(context: Context): SoftwareUpdateSettings {
        return (settingsProvider ?: BortSoftwareUpdateSettingsProvider(context.contentResolver)).settings()
            ?: throw IllegalStateException(
                "Could not read config from Bort, " +
                    "this is likely an integration issue, please contact Memfault support"
            )
    }

    suspend fun perform(action: Action) {
        actionHandler.handle(
            state = updateState.value,
            action = action,
        )
    }

    fun setState(state: State) {
        stateStore.store(state)
        _updateState.value = state
    }

    suspend fun triggerEvent(event: Event) {
        _events.emit(event)
    }

    companion object {
        fun create(
            context: Context,
            actionHandlerFactory: UpdateActionHandlerFactory? = null,
            stateStore: StateStore? = null,
            settingsProvider: SoftwareUpdateSettingsProvider? = null
        ): Updater {
            Logger.minStructuredLevel = LogLevel.INFO
            Logger.minLogcatLevel = LogLevel.DEBUG
            Logger.TAG = "bort-ota"

            val store = stateStore ?: SharedPreferencesStateStore(
                context.getSharedPreferences(
                    DEFAULT_STATE_PREFERENCE_FILE, Context.MODE_PRIVATE
                )
            )

            val handlerFactory = actionHandlerFactory ?: createDefaultActionHandlerFactory(context)

            return Updater(
                actionHandlerFactory = handlerFactory,
                stateStore = store,
                settingsProvider = settingsProvider,
                context = context,
            )
        }

        private fun createDefaultActionHandlerFactory(
            context: Context,
        ): UpdateActionHandlerFactory =
            if (isAbDevice(context)) realABUpdateActionHandlerFactory(context)
            else realRecoveryBasedUpdateActionHandlerFactory(context)

        private fun isAbDevice(context: Context): Boolean =
            !SystemPropertyProxy.get(context, "ro.boot.slot_suffix").isNullOrBlank() ||
                SystemPropertyProxy.get(context, "ro.build.ab_update") == "true"
    }
}

/*

* *
 * Applications are expected to implement this interface and keep an instance of the updater for as long as the
 * application lives. This allows components that are instantiated by Android (i.e. the DownloadOtaService) to
 * access the updater.
 */
interface UpdaterProvider {
    fun updater(): Updater
}

/**
 * Gets the updater instance from the application context. The application is expected to keep an instance and
 * implement UpdateProvider.
 */
fun Context.updater(): Updater {
    if (this.applicationContext is UpdaterProvider) return (this.applicationContext as UpdaterProvider).updater()
    else throw IllegalArgumentException("Application context does not implement UpdaterProvider")
}
