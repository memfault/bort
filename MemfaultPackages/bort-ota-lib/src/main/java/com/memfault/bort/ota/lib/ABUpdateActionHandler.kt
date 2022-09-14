package com.memfault.bort.ota.lib

import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import android.os.UpdateEngine
import android.os.UpdateEngineCallback
import android.util.Log
import com.memfault.bort.shared.PreferenceKeyProvider
import com.memfault.bort.shared.SoftwareUpdateSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class ABUpdateActionHandler(
    private val androidUpdateEngine: AndroidUpdateEngine,
    private val softwareUpdateChecker: SoftwareUpdateChecker,
    private val setState: suspend (state: State) -> Unit,
    private val triggerEvent: suspend (event: Event) -> Unit,
    private val rebootDevice: () -> Unit,
    private val cachedOtaProvider: CachedOtaProvider,
    private val currentSoftwareVersion: String,
) : UpdateActionHandler {
    init {
        androidUpdateEngine.bind(object : AndroidUpdateEngineCallback {
            override fun onStatusUpdate(status: Int, percent: Float) {
                CoroutineScope(Dispatchers.Default).launch {
                    when (status) {
                        UPDATE_ENGINE_STATUS_DOWNLOADING -> {
                            val cachedOta = cachedOtaProvider.get()
                            if (cachedOta != null) {
                                setState(State.UpdateDownloading(cachedOta, (percent * 100).toInt()))
                            } else {
                                setState(State.Idle)
                            }
                        }
                        UPDATE_ENGINE_FINALIZING -> {
                            val cachedOta = cachedOtaProvider.get()
                            if (cachedOta != null) {
                                setState(State.Finalizing(cachedOta, (percent * 100).toInt()))
                            } else {
                                setState(State.Idle)
                            }
                        }
                        UPDATE_ENGINE_STATUS_IDLE ->
                            setState(State.Idle)
                        UPDATE_ENGINE_REPORTING_ERROR_EVENT ->
                            setState(State.Idle) // errors are reported in the method below
                        UPDATE_ENGINE_UPDATED_NEED_REBOOT -> {
                            val cachedOta = cachedOtaProvider.get()
                            if (cachedOta != null) {
                                setState(State.RebootNeeded(cachedOta))
                            } else {
                                setState(State.Idle)
                            }
                        }
                        // Note: this is not used in Android
                        UPDATE_ENGINE_STATUS_UPDATE_AVAILABLE -> {}
                        // Note: this is not used in streaming updates
                        UPDATE_ENGINE_STATUS_VERIFYING -> {}
                        // Note: this is not used in Android
                        UPDATE_ENGINE_STATUS_CHECKING_FOR_UPDATE -> {}
                        // Note: This is handled by the error callbacks below
                        UPDATE_ENGINE_ATTEMPTING_ROLLBACK ->
                            Log.v("Updater", "Attempting rollback $percent")
                        // Note: This is handled by the error callbacks below
                        UPDATE_ENGINE_DISABLED ->
                            Log.v("Updater", "Disabled $percent")
                    }
                }
            }

            override fun onPayloadApplicationComplete(errorCode: Int) {
                CoroutineScope(Dispatchers.Default).launch {
                    when (errorCode) {
                        UPDATE_ENGINE_ERROR_SUCCESS -> {}
                        UPDATE_ENGINE_ERROR_DOWNLOAD_TRANSFER_ERROR ->
                            triggerEvent(Event.DownloadFailed)
                        else ->
                            triggerEvent(Event.VerificationFailed)
                    }
                }
            }
        })
    }

    override suspend fun handle(
        state: State,
        action: Action,
    ) {
        when (action) {
            is Action.CheckForUpdate -> {
                if (state.allowsUpdateCheck()) {
                    setState(State.CheckingForUpdates)
                    val ota = softwareUpdateChecker.getLatestRelease()
                    if (ota == null) {
                        setState(State.Idle)
                        triggerEvent(Event.NoUpdatesAvailable)
                    } else {
                        cachedOtaProvider.set(ota)
                        setState(State.UpdateAvailable(ota, background = action.background))
                    }
                }
            }
            is Action.DownloadUpdate -> {
                if (state is State.UpdateAvailable) {
                    androidUpdateEngine.applyPayload(
                        state.ota.url,
                        state.ota.metadata["_MFLT_PAYLOAD_OFFSET"]?.toLong() ?: 0L,
                        state.ota.metadata["_MFLT_PAYLOAD_SIZE"]?.toLong() ?: 0L,
                        state.ota.metadata.map {
                            "${it.key}=${it.value}"
                        }.toTypedArray(),
                    )
                }
            }
            is Action.Reboot -> {
                val ota = cachedOtaProvider.get()
                if (state is State.RebootNeeded && ota != null) {
                    setState(State.RebootedForInstallation(ota, currentSoftwareVersion))
                    rebootDevice()
                } else {
                    setState(State.Idle)
                }
            }
            else -> {}
        }
    }
}

interface CachedOtaProvider {
    fun get(): Ota?
    fun set(ota: Ota?)
}

class SharedPreferenceCachedOtaProvider(sharedPreferences: SharedPreferences) :
    PreferenceKeyProvider<String>(sharedPreferences, EMPTY, CACHED_OTA_KEY), CachedOtaProvider {
    override fun get(): Ota? {
        val stored = getValue()
        return if (stored != EMPTY) Json { encodeDefaults = true }.decodeFromString(Ota.serializer(), stored)
        else null
    }

    override fun set(ota: Ota?) {
        if (ota == null) setValue(EMPTY)
        else setValue(Json { encodeDefaults = true }.encodeToString(Ota.serializer(), ota))
    }

    companion object {
        private const val EMPTY = ""
    }
}

// These must match platform values
const val UPDATE_ENGINE_STATUS_IDLE = 0
const val UPDATE_ENGINE_STATUS_CHECKING_FOR_UPDATE = 1
const val UPDATE_ENGINE_STATUS_UPDATE_AVAILABLE = 2
const val UPDATE_ENGINE_STATUS_DOWNLOADING = 3
const val UPDATE_ENGINE_STATUS_VERIFYING = 4
const val UPDATE_ENGINE_FINALIZING = 5
const val UPDATE_ENGINE_UPDATED_NEED_REBOOT = 6
const val UPDATE_ENGINE_REPORTING_ERROR_EVENT = 7
const val UPDATE_ENGINE_ATTEMPTING_ROLLBACK = 8
const val UPDATE_ENGINE_DISABLED = 9

// These two are relevant to our use cases, all others are verification errors (i.e. checksums, device state, etc).
const val UPDATE_ENGINE_ERROR_SUCCESS = 0
const val UPDATE_ENGINE_ERROR_DOWNLOAD_TRANSFER_ERROR = 9

interface AndroidUpdateEngine {
    fun bind(callback: AndroidUpdateEngineCallback)
    fun applyPayload(url: String, offset: Long, size: Long, metadata: Array<String>)
}

interface AndroidUpdateEngineCallback {
    fun onStatusUpdate(status: Int, percent: Float)
    fun onPayloadApplicationComplete(errorCode: Int)
}

class RealAndroidUpdateEngine : AndroidUpdateEngine {
    private val updateEngine = UpdateEngine()

    override fun bind(callback: AndroidUpdateEngineCallback) {
        updateEngine.bind(object : UpdateEngineCallback() {
            override fun onStatusUpdate(status: Int, percent: Float) {
                callback.onStatusUpdate(status, percent)
            }

            override fun onPayloadApplicationComplete(errorCode: Int) {
                callback.onPayloadApplicationComplete(errorCode)
            }
        })
    }

    override fun applyPayload(
        url: String,
        offset: Long,
        size: Long,
        metadata: Array<String>,
    ) {
        updateEngine.applyPayload(
            url,
            offset,
            size,
            metadata,
        )
    }
}

fun realABUpdateActionHandlerFactory(
    context: Context,
    androidUpdateEngine: AndroidUpdateEngine = RealAndroidUpdateEngine(),
) = object : UpdateActionHandlerFactory {
    override fun create(
        setState: suspend (state: State) -> Unit,
        triggerEvent: suspend (event: Event) -> Unit,
        settings: () -> SoftwareUpdateSettings,
    ): UpdateActionHandler = ABUpdateActionHandler(
        androidUpdateEngine = androidUpdateEngine,
        softwareUpdateChecker = realSoftwareUpdateChecker(settings, RealMetricLogger(context)),
        setState = setState,
        triggerEvent = triggerEvent,
        rebootDevice = {
            context.getSystemService(PowerManager::class.java)
                .reboot(null)
        },
        cachedOtaProvider = SharedPreferenceCachedOtaProvider(
            context.getSharedPreferences(DEFAULT_STATE_PREFERENCE_FILE, Context.MODE_PRIVATE)
        ),
        currentSoftwareVersion = settings().currentVersion,
    )
}
