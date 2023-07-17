package com.memfault.bort.ota.lib.download

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.os.SystemClock
import android.system.Os
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
import com.memfault.bort.shared.InternalMetric
import com.memfault.bort.shared.InternalMetric.Companion.OTA_DOWNLOAD_START
import com.memfault.bort.shared.InternalMetric.Companion.OTA_DOWNLOAD_START_RESUMING
import com.memfault.bort.shared.InternalMetric.Companion.OTA_DOWNLOAD_SUCCESS_ERROR
import com.memfault.bort.shared.InternalMetric.Companion.OTA_DOWNLOAD_SUCCESS_SPEED
import com.memfault.bort.shared.InternalMetric.Companion.sendMetric
import com.memfault.bort.shared.Logger
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import javax.inject.Inject
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.appendingSink
import okio.buffer

private const val NOTIFICATION_CHANNEL_ID = "download_ota_notification"
private const val NOTIFICATION_ID = 9001
private const val EXTRA_URL = "extra_url"

/**
 * A foreground service that downloads an OTA update to the expected folder. It shows a progress notification
 * and will issue update actions to the updater when the download succeeds or fails.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@AndroidEntryPoint
class DownloadOtaService : Service() {
    @Inject lateinit var updater: Updater
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var downloadJob: Job
    private lateinit var downloadProgressStore: DownloadProgressStore
    private var lastReportedPercentage = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        if (url == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val targetFile = File(OTA_PATH)

        // Show a user-facing notification and start in the foreground so that the system does not kill us
        notificationManager = NotificationManagerCompat.from(this)
        notificationBuilder = setupForegroundNotification()
        startForeground(NOTIFICATION_ID, notificationBuilder.build())

        downloadProgressStore = DownloadProgressStore(
            getSharedPreferences(DEFAULT_STATE_PREFERENCE_FILE, Context.MODE_PRIVATE)
        )
        val startMs = SystemClock.elapsedRealtime()

        downloadJob = download(url, targetFile) { bytesRead, contentLength ->
            val progressPercentage = (bytesRead.toFloat() / contentLength * 100).toInt()

            // Avoid flooding with state updates unless the integer percentage changed
            if (progressPercentage != lastReportedPercentage) {
                downloadProgressStore.store(DownloadProgress(url, bytesRead))
                setProgress(progressPercentage)
                lastReportedPercentage = progressPercentage
                runOnUpdater { perform(Action.DownloadProgress(progressPercentage)) }
            }
        }.apply {
            invokeOnCompletion { error ->
                @Suppress("DEPRECATION")
                stopForeground(true)
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
                        )
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
                        error
                    )
                    sendMetric(InternalMetric(OTA_DOWNLOAD_SUCCESS_ERROR))
                    downloadFailed(error)
                }
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (this::downloadJob.isInitialized && downloadJob.isActive) downloadJob.cancel()
        super.onDestroy()
    }

    private fun download(url: String, targetFile: File, progressCallback: ProgressCallback): Job =
        CoroutineScope(Dispatchers.IO).launch {
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
                )
            )
            sendMetric(InternalMetric(if (downloadOffset == 0L) OTA_DOWNLOAD_START else OTA_DOWNLOAD_START_RESUMING))

            val okHttp = OkHttpClient.Builder()
                .addNetworkInterceptor { chain ->
                    val originalResponse = chain.proceed(chain.request())
                    originalResponse.newBuilder()
                        .body(originalResponse.body?.withProgressCallback(progressCallback, downloadOffset))
                        .build()
                }
                .eventListener(object : EventListener() {
                    override fun callFailed(call: Call, ioe: IOException) {
                        cancel("Download failed", ioe)
                    }
                })
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=$downloadOffset-")
                .build()
            try {
                suspendCancellableCoroutine<Unit> { continuation ->
                    okHttp.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            continuation.resumeWithException(e)
                        }

                        override fun onResponse(call: Call, response: Response) {
                            response.use {
                                it.body?.let { body ->
                                    if (targetFile.exists() && body.contentLength() <= downloadOffset) {
                                        return continuation.resume(Unit) {}
                                    }

                                    truncateOrThrow(targetFile, downloadOffset)

                                    targetFile.appendingSink().buffer().use { sink ->
                                        sink.writeAll(body.source())
                                    }
                                    return continuation.resume(Unit) {}
                                }
                            }
                            return continuation.resumeWithException(
                                IllegalArgumentException("Response URL does not have a body")
                            )
                        }
                    })
                }
            } catch (ex: Exception) {
                cancel("Download failed", ex)
            }
        }

    private fun truncateOrThrow(targetFile: File, contentLength: Long) {
        try {
            RandomAccessFile(targetFile, "rw").use {
                Os.ftruncate(it.fd, contentLength)
                it.setLength(contentLength)
            }
        } catch (e: Exception) {
            throw IOException("Unable to truncate $targetFile to $contentLength", e)
        }
    }

    private fun setupForegroundNotification(): NotificationCompat.Builder {
        ensureNotificationChannel()
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).apply {
            setContentTitle(getString(R.string.download_notification_title))
            setSmallIcon(R.drawable.ic_baseline_cloud_download_24)
            setProgress(100, 0, true)
            priority = NotificationCompat.PRIORITY_LOW
        }
    }

    @SuppressLint("MissingPermission")
    private fun setProgress(progress: Int, max: Int = 100) {
        notificationManager.notify(
            NOTIFICATION_ID,
            notificationBuilder.apply {
                setProgress(max, progress, false)
            }.build()
        )
    }

    private fun ensureNotificationChannel() {
        NotificationChannelCompat.Builder(
            NOTIFICATION_CHANNEL_ID,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setName(getString(R.string.software_update_download))
            setDescription(getString(R.string.software_update_download_description))
        }.also {
            NotificationManagerCompat.from(this)
                .createNotificationChannel(it.build())
        }
    }

    @SuppressLint("MissingPermission")
    private fun downloadSuccessful(path: File = File(OTA_PATH)) {
        notificationManager.notify(
            NOTIFICATION_ID,
            notificationBuilder.apply {
                setContentTitle(getString(R.string.download_successful))
                setProgress(0, 0, false)
            }.build()
        )
        downloadProgressStore.store(null)
        runOnUpdater { perform(Action.DownloadCompleted(path.absolutePath)) }
    }

    @SuppressLint("MissingPermission")
    private fun downloadFailed(@Suppress("UNUSED_PARAMETER") cause: Throwable) {
        notificationManager.notify(
            NOTIFICATION_ID,
            notificationBuilder.apply {
                setContentTitle(getString(R.string.download_failed))
                setProgress(0, 0, false)
            }.build()
        )
        runOnUpdater { perform(Action.DownloadFailed) }
    }

    private fun runOnUpdater(block: suspend Updater.() -> Unit) {
        CoroutineScope(Dispatchers.Default).launch {
            block.invoke(updater)
        }
    }

    companion object {
        fun download(context: Context, url: String) {
            context.startForegroundService(
                Intent(context, DownloadOtaService::class.java).apply {
                    putExtra(EXTRA_URL, url)
                }
            )
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
                if (progress == null) remove(downloadProgressKey)
                else putString(downloadProgressKey, Json.encodeToString(DownloadProgress.serializer(), progress))
            }
            .apply()
    }

    fun get(): DownloadProgress? =
        try {
            Json.decodeFromString(
                DownloadProgress.serializer(),
                sharedPreferences.getString(downloadProgressKey, "") ?: ""
            )
        } catch (ex: SerializationException) {
            null
        }
}
