package com.memfault.bort.ota

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.memfault.bort.ota.lib.Action
import com.memfault.bort.ota.lib.Event
import com.memfault.bort.ota.lib.Ota
import com.memfault.bort.ota.lib.State
import com.memfault.bort.ota.lib.Updater
import com.memfault.bort.ota.lib.UpdaterProvider
import com.memfault.bort.shared.BuildConfig
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.disableAppComponents
import com.memfault.bort.shared.isPrimaryUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

open class App : Application(), UpdaterProvider {
    lateinit var components: AppComponents
    private lateinit var appStateListenerJob: Job
    private lateinit var eventListenerJob: Job

    override fun onCreate() {
        super.onCreate()

        Logger.initTags(tag = "bort-ota", testTag = "bort-ota-test")

        if (!isPrimaryUser()) {
            Logger.w("bort-ota disabled for secondary user")
            disableAppComponents(applicationContext)
            System.exit(0)
        }

        components = createComponents(applicationContext)

        // Listen to state changes for background workers, if an update is found in the background show a notification
        appStateListenerJob = CoroutineScope(Dispatchers.Main).launch {
            updater().updateState
                .collect { state ->
                    if (state is State.UpdateAvailable && shouldAutoInstallOtaUpdate(state.ota, applicationContext)) {
                        updater().perform(Action.DownloadUpdate)
                    } else if (state is State.ReadyToInstall && shouldAutoInstallOtaUpdate(
                            state.ota,
                            applicationContext
                        )
                    ) {
                        updater().perform(Action.InstallUpdate)
                    } else if (state is State.RebootNeeded && shouldAutoInstallOtaUpdate(
                            state.ota,
                            applicationContext
                        )
                    ) {
                        updater().perform(Action.Reboot)
                    } else if (state is State.UpdateAvailable && state.background) {
                        sendUpdateNotification(state.ota)
                    } else {
                        cancelUpdateNotification()
                    }
                }
        }

        // Don't use Dispatchers.Main here. This event gets emitted on boot completion and the Main dispatcher
        // will not be ready to dispatch the event.
        eventListenerJob = CoroutineScope(Dispatchers.Default).launch {
            updater().events
                .collect { event ->
                    when (event) {
                        is Event.RebootToUpdateFailed -> showUpdateCompleteNotification(success = false)
                        is Event.RebootToUpdateSucceeded -> showUpdateCompleteNotification(success = true)
                        else -> {}
                    }
                }
        }
    }

    companion object {
        private fun shouldAutoInstallOtaUpdate(ota: Ota, context: Context): Boolean = shouldAutoInstallOtaUpdate(
            ota = ota,
            defaultValue = BuildConfig.OTA_AUTO_INSTALL,
            canInstallNow = ::custom_canAutoInstallOtaUpdateNow,
            context = context,
        )

        internal fun shouldAutoInstallOtaUpdate(
            ota: Ota,
            defaultValue: Boolean,
            canInstallNow: (Context) -> Boolean,
            context: Context,
        ): Boolean {
            Logger.d("shouldAutoInstallOtaUpdate: isForced = ${ota.isForced} default = $defaultValue")
            // isForced is optional in OTA response - fall back to default if not set.
            val autoInstall = ota.isForced ?: defaultValue
            return autoInstall && canInstallNow(context)
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

    private fun cancelUpdateNotification() {
        NotificationManagerCompat.from(this)
            .cancel(UPDATE_AVAILABLE_NOTIFICATION_ID)
    }

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

    override fun updater(): Updater = components.updater()
}

val Application.components get() = (applicationContext as App).components

private const val UPDATE_AVAILABLE = "update_available"
private const val UPDATE_AVAILABLE_NOTIFICATION_ID = 5001
private const val UPDATE_FINISHED_NOTIFICATION_ID = 5002
