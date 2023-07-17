package com.memfault.bort.ota

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.memfault.bort.ConfigureStrictMode
import com.memfault.bort.ota.lib.Event
import com.memfault.bort.ota.lib.IsAbDevice
import com.memfault.bort.ota.lib.Ota
import com.memfault.bort.ota.lib.State
import com.memfault.bort.ota.lib.Updater
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.LoggerSettings
import com.memfault.bort.shared.disableAppComponents
import com.memfault.bort.shared.isPrimaryUser
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltAndroidApp
open class OtaApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var updater: Updater
    @Inject lateinit var otaMode: IsAbDevice
    @Inject lateinit var configureStrictMode: ConfigureStrictMode
    private lateinit var appStateListenerJob: Job
    private lateinit var eventListenerJob: Job

    override fun onCreate() {
        super.onCreate()

        Logger.initTags(tag = "bort-ota")
        Logger.initSettings(
            LoggerSettings(
                eventLogEnabled = true,
                logToDisk = false,
                minLogcatLevel = LogLevel.DEBUG,
                minStructuredLevel = LogLevel.INFO,
                hrtEnabled = false,
            )
        )
        configureStrictMode.configure()

        if (!isPrimaryUser()) {
            Logger.w("bort-ota disabled for secondary user")
            disableAppComponents(applicationContext)
            System.exit(0)
        }

        // Listen to state changes for background workers, if an update is found in the background show a notification
        appStateListenerJob = CoroutineScope(Dispatchers.Main).launch {
            updater.updateState
                .collect { state ->
                    if (state is State.UpdateAvailable && state.showNotification.ifNull(true)) {
                        sendUpdateNotification(state.ota)
                    } else {
                        cancelUpdateNotification()
                    }
                }
        }

        // Don't use Dispatchers.Main here. This event gets emitted on boot completion and the Main dispatcher
        // will not be ready to dispatch the event.
        eventListenerJob = CoroutineScope(Dispatchers.Default).launch {
            updater.events
                .collect { event ->
                    when (event) {
                        is Event.RebootToUpdateFailed -> showUpdateCompleteNotification(success = false)
                        is Event.RebootToUpdateSucceeded -> showUpdateCompleteNotification(success = true)
                        else -> {}
                    }
                }
        }
    }

    private fun Boolean?.ifNull(ifNull: Boolean) = this ?: ifNull

    override fun onTerminate() {
        appStateListenerJob.cancel()
        super.onTerminate()
    }

    @SuppressLint("MissingPermission")
    private fun sendUpdateNotification(ota: Ota) {
        val notificationManager = NotificationManagerCompat.from(this)

        NotificationChannelCompat.Builder(UPDATE_AVAILABLE, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.update_available))
            .setDescription(getString(R.string.update_available))
            .build()
            .also { notificationManager.createNotificationChannel(it) }

        val intent = Intent(this, UpdateActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)

        NotificationCompat.Builder(this, UPDATE_AVAILABLE)
            .setSmallIcon(R.drawable.ic_baseline_system_update_24)
            .setContentTitle(getString(R.string.update_available))
            .setContentText(getString(R.string.software_version, ota.version))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            .also { notificationManager.notify(UPDATE_AVAILABLE_NOTIFICATION_ID, it) }
    }

    private fun cancelUpdateNotification() {
        NotificationManagerCompat.from(this)
            .cancel(UPDATE_AVAILABLE_NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission")
    private fun showUpdateCompleteNotification(success: Boolean) {
        val notificationManager = NotificationManagerCompat.from(this)

        NotificationChannelCompat.Builder(UPDATE_AVAILABLE, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.update_available))
            .setDescription(getString(R.string.update_available))
            .build()
            .also { notificationManager.createNotificationChannel(it) }

        val intent = Intent(this, UpdateActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)

        val title =
            if (success) getString(R.string.update_succeeded)
            else getString(R.string.update_failed)
        val contentText =
            if (success) getString(R.string.update_succeeded_content)
            else getString(R.string.update_failed_content)

        NotificationCompat.Builder(this, UPDATE_AVAILABLE)
            .setSmallIcon(R.drawable.ic_baseline_system_update_24)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            .also { notificationManager.notify(UPDATE_FINISHED_NOTIFICATION_ID, it) }
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }
}

private const val UPDATE_AVAILABLE = "update_available"
private const val UPDATE_AVAILABLE_NOTIFICATION_ID = 5001
private const val UPDATE_FINISHED_NOTIFICATION_ID = 5002
