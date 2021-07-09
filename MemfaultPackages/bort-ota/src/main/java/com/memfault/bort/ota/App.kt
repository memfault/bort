package com.memfault.bort.ota

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.memfault.bort.ota.lib.Ota
import com.memfault.bort.ota.lib.State
import com.memfault.bort.ota.lib.Updater
import com.memfault.bort.ota.lib.UpdaterProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

open class App : Application(), UpdaterProvider {
    lateinit var components: AppComponents
    lateinit var appStateListenerJob: Job

    override fun onCreate() {
        super.onCreate()

        components = createComponents(applicationContext)

        // Listen to state changes for background workers, if an update is found in the background show a notification
        appStateListenerJob = CoroutineScope(Dispatchers.Main).launch {
            updater().updateState
                .collect { state ->
                    if (state is State.UpdateAvailable && state.background) {
                        sendUpdateNotification(state.ota)
                    }
                }
        }
    }

    protected open fun createComponents(applicationContext: Context): AppComponents {
        val updater = Updater.create(applicationContext)
        return object : AppComponents {
            override fun updater(): Updater = updater
        }
    }

    override fun onTerminate() {
        appStateListenerJob.cancel()
        super.onTerminate()
    }

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

    override fun updater(): Updater = components.updater()
}

val Application.components get() = (applicationContext as App).components

private const val UPDATE_AVAILABLE = "update_available"
private const val UPDATE_AVAILABLE_NOTIFICATION_ID = 5001
