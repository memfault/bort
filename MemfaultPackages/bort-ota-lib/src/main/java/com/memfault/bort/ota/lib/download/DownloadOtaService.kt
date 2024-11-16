package com.memfault.bort.ota.lib.download

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.system.Os
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.memfault.bort.Default
import com.memfault.bort.IO
import com.memfault.bort.ota.lib.Action
import com.memfault.bort.ota.lib.DEFAULT_STATE_PREFERENCE_FILE
import com.memfault.bort.ota.lib.DOWNLOAD_PROGRESS_KEY
import com.memfault.bort.ota.lib.OTA_PATH
import com.memfault.bort.ota.lib.PARAM_BYTES
import com.memfault.bort.ota.lib.PARAM_BYTES_PER_S
import com.memfault.bort.ota.lib.PARAM_DURATION_MS
import com.memfault.bort.ota.lib.PARAM_OFFSET
import com.memfault.bort.ota.lib.PARAM_URL
import com.memfault.bort.ota.lib.R
import com.memfault.bort.ota.lib.TAG_OTA_DOWNLOAD_COMPLETED
import com.memfault.bort.ota.lib.TAG_OTA_DOWNLOAD_ERROR
import com.memfault.bort.ota.lib.TAG_OTA_DOWNLOAD_STARTED
import com.memfault.bort.ota.lib.Updater
import com.memfault.bort.ota.lib.download.DownloadOtaService.OtaDownloadEvent.OtaDownloadComplete
import com.memfault.bort.ota.lib.download.DownloadOtaService.OtaDownloadEvent.OtaDownloadProgress
import com.memfault.bort.shared.InternalMetric
import com.memfault.bort.shared.InternalMetric.Companion.OTA_DOWNLOAD_START
import com.memfault.bort.shared.InternalMetric.Companion.OTA_DOWNLOAD_START_RESUMING
import com.memfault.bort.shared.InternalMetric.Companion.OTA_DOWNLOAD_SUCCESS_ERROR
import com.memfault.bort.shared.InternalMetric.Companion.OTA_DOWNLOAD_SUCCESS_SPEED
import com.memfault.bort.shared.InternalMetric.Companion.sendMetric
import com.memfault.bort.shared.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.appendingSink
import okio.buffer
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlin.math.min

private const val NOTIFICATION_CHANNEL_ID = "download_ota_notification"
const val NOTIFICATION_ID = 9001
private const val EXTRA_URL = "extra_url"

/**
 * A foreground service that downloads an OTA update to the expected folder. It shows a progress notification
 * and will issue update actions to the updater when the download succeeds or fails.
 */

@AndroidEntryPoint
class DownloadOtaService : LifecycleService() {
    @Inject lateinit var updater: Updater

    @IO @Inject
    lateinit var ioCoroutineContext: CoroutineContext

    @Default @Inject
    lateinit var defaultCoroutineContext: CoroutineContext
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notificationBuilder: NotificationCompat.Builder

    private val downloadProgressStore by lazy {
        DownloadProgressStore(
            getSharedPreferences(DEFAULT_STATE_PREFERENCE_FILE, Context.MODE_PRIVATE),
        )
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        val url = intent?.getStringExtra(EXTRA_URL)
        if (url == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val targetFile = File(OTA_PATH)

        // Show a user-facing notification and start in the foreground so that the system does not kill us
        notificationManager = NotificationManagerCompat.from(this)
        notificationBuilder = setupForegroundNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notificationBuilder.build(), FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notificationBuilder.build())
        }

        val startMs = SystemClock.elapsedRealtime()

        lifecycleScope.launch(ioCoroutineContext) {
            download(url, targetFile)
                .distinctUntilChangedBy { event ->
                    when (event) {
                        // Avoid flooding with state updates unless the integer percentage changed
                        is OtaDownloadProgress -> event.progressPercentage
                        is OtaDownloadComplete -> event
                    }
                }
                .onEach { event ->
                    when (event) {
                        is OtaDownloadProgress -> {
                            downloadProgressStore.store(DownloadProgress(url, event.bytesRead))
                            setProgress(event.progressPercentage)
                        }

                        is OtaDownloadComplete -> {
                            val error = event.e
                            // Don't divide-by-zero
                            val durationMs = max(SystemClock.elapsedRealtime() - startMs, 1).toFloat()
                            // Get this before calling success/failure (which resets the store)
                            val bytesDownloaded = downloadProgressStore.get()?.downloaded ?: 0
                            val bytesPerSecond = (bytesDownloaded.toFloat() / durationMs) * 1000
                            if (error == null) {
                                Logger.i(
                                    TAG_OTA_DOWNLOAD_COMPLETED,
                                    mapOf(
                                        PARAM_URL to url,
                                        PARAM_DURATION_MS to durationMs,
                                        PARAM_BYTES to bytesDownloaded,
                                        PARAM_BYTES_PER_S to bytesPerSecond,
                                    ),
                                )
                                sendMetric(InternalMetric(OTA_DOWNLOAD_SUCCESS_SPEED, bytesPerSecond))
                                downloadSuccessful(targetFile)
                            } else {
                                Logger.i(
                                    TAG_OTA_DOWNLOAD_ERROR,
                                    mapOf(
                                        PARAM_URL to url,
                                        PARAM_DURATION_MS to durationMs,
                                        PARAM_BYTES to bytesDownloaded,
                                        PARAM_BYTES_PER_S to bytesPerSecond,
                                    ),
                                    error,
                                )
                                sendMetric(InternalMetric(OTA_DOWNLOAD_SUCCESS_ERROR))
                                downloadFailed(error)
                            }
                        }
                    }
                }
                .onCompletion {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                    stopSelf(startId)
                }
                .launchIn(this)
        }

        return START_NOT_STICKY
    }

    private fun download(
        url: String,
        targetFile: File,
    ): Flow<OtaDownloadEvent> = callbackFlow {
        val previousProgress = downloadProgressStore.get()

        // If we previous stored download progress with the same URL and the file exists, maybe we can resume it
        val downloadOffset =
            if (previousProgress?.url == url && targetFile.exists()) {
                min(previousProgress.downloaded, targetFile.length())
            } else {
                targetFile.delete()
                0
            }

        Logger.i(
            TAG_OTA_DOWNLOAD_STARTED,
            mapOf(
                PARAM_URL to url,
                PARAM_OFFSET to downloadOffset,
            ),
        )
        sendMetric(InternalMetric(if (downloadOffset == 0L) OTA_DOWNLOAD_START else OTA_DOWNLOAD_START_RESUMING))

        val okHttp = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder()
                    .body(
                        originalResponse.body?.withProgressCallback(
                            { bytesRead: Long, contentLength: Long ->
                                trySend(OtaDownloadProgress(bytesRead = bytesRead, contentLength = contentLength))
                                    .onFailure { exception ->
                                        Logger.e("Could not update progress", exception)
                                    }
                            },
                            downloadOffset,
                        ),
                    )
                    .build()
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$downloadOffset-")
            .build()

        val call = okHttp.newCall(request)

        call.enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    trySendBlocking(OtaDownloadComplete(e))
                        .onFailure { exception ->
                            Logger.e("Could not complete from onFailure", exception)
                        }
                    channel.close()
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    val event = if (response.isSuccessful) {
                        response.body?.use { body ->
                            if (targetFile.exists() && body.contentLength() <= downloadOffset) {
                                OtaDownloadComplete()
                            } else {
                                truncateOrThrow(targetFile, downloadOffset)

                                targetFile.appendingSink().buffer().use { sink ->
                                    sink.writeAll(body.source())
                                }
                            }
                            OtaDownloadComplete()
                        } ?: OtaDownloadComplete(IllegalArgumentException("Response URL does not have a body"))
                    } else {
                        OtaDownloadComplete(IllegalArgumentException("Response was not successful"))
                    }

                    trySendBlocking(event)
                        .onFailure { exception ->
                            Logger.e("Could not complete from onResponse", exception)
                        }
                    channel.close()
                }
            },
        )

        awaitClose { call.cancel() }
    }
        .buffer(capacity = UNLIMITED)

    private fun truncateOrThrow(
        targetFile: File,
        contentLength: Long,
    ) {
        try {
            RandomAccessFile(targetFile, "rw").use {
                Os.ftruncate(it.fd, contentLength)
                it.setLength(contentLength)
            }
        } catch (e: Exception) {
            throw IOException("Unable to truncate $targetFile to $contentLength", e)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun setProgress(
        progress: Int,
        max: Int = 100,
    ) {
        notificationManager.notify(
            NOTIFICATION_ID,
            notificationBuilder.apply {
                setProgress(max, progress, false)
            }.build(),
        )
        withContext(defaultCoroutineContext) {
            updater.perform(Action.DownloadProgress(progress))
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun downloadSuccessful(targetFile: File) {
        notificationManager.notify(
            NOTIFICATION_ID,
            notificationBuilder.apply {
                setContentTitle(getString(R.string.download_successful))
                setProgress(0, 0, false)
            }.build(),
        )
        downloadProgressStore.store(null)
        withContext(defaultCoroutineContext) {
            updater.perform(Action.DownloadCompleted(targetFile.absolutePath))
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun downloadFailed(@Suppress("UNUSED_PARAMETER") cause: Throwable) {
        notificationManager.notify(
            NOTIFICATION_ID,
            notificationBuilder.apply {
                setContentTitle(getString(R.string.download_failed))
                setProgress(0, 0, false)
            }.build(),
        )
        withContext(defaultCoroutineContext) {
            updater.perform(Action.DownloadFailed)
        }
    }

    private sealed class OtaDownloadEvent {
        data class OtaDownloadComplete(val e: Exception? = null) : OtaDownloadEvent()
        data class OtaDownloadProgress(
            val bytesRead: Long,
            val contentLength: Long,
        ) : OtaDownloadEvent() {
            val progressPercentage = (bytesRead.toFloat() / contentLength * 100).toInt()
        }
    }

    companion object {
        fun download(
            context: Context,
            url: String,
        ) {
            context.startForegroundService(
                Intent(context, DownloadOtaService::class.java).apply {
                    putExtra(EXTRA_URL, url)
                },
            )
        }

        private fun ensureNotificationChannel(context: Context) {
            NotificationChannelCompat.Builder(
                NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setName(context.getString(R.string.software_update_download))
                setDescription(context.getString(R.string.software_update_download_description))
            }.also {
                NotificationManagerCompat.from(context)
                    .createNotificationChannel(it.build())
            }
        }

        fun setupForegroundNotification(context: Context): NotificationCompat.Builder {
            ensureNotificationChannel(context)
            return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID).apply {
                setContentTitle(context.getString(R.string.download_notification_title))
                setSmallIcon(R.drawable.ic_baseline_cloud_download_24)
                setProgress(100, 0, true)
                priority = NotificationCompat.PRIORITY_LOW
            }
        }
    }
}

@Serializable
data class DownloadProgress(
    val url: String,
    val downloaded: Long,
)

class DownloadProgressStore(
    private val sharedPreferences: SharedPreferences,
    private val downloadProgressKey: String = DOWNLOAD_PROGRESS_KEY,
) {
    fun store(progress: DownloadProgress?) {
        sharedPreferences.edit()
            .apply {
                if (progress == null) {
                    remove(downloadProgressKey)
                } else {
                    putString(downloadProgressKey, Json.encodeToString(DownloadProgress.serializer(), progress))
                }
            }
            .apply()
    }

    fun get(): DownloadProgress? =
        try {
            Json.decodeFromString(
                DownloadProgress.serializer(),
                sharedPreferences.getString(downloadProgressKey, "") ?: "",
            )
        } catch (ex: SerializationException) {
            null
        }
}
