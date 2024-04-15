package com.memfault.bort.ota

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.memfault.bort.Default
import com.memfault.bort.Main
import com.memfault.bort.ota.lib.Event
import com.memfault.bort.ota.lib.Ota
import com.memfault.bort.ota.lib.State
import com.memfault.bort.ota.lib.Updater
import com.memfault.bort.scopes.Scope
import com.memfault.bort.scopes.Scoped
import com.memfault.bort.scopes.coroutineScope
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@ContributesMultibinding(SingletonComponent::class)
class OtaNotifications
@Inject constructor(
    private val application: Application,
    @Main private val mainCoroutineContext: CoroutineContext,
    @Default private val defaultCoroutineContext: CoroutineContext,
    private val updater: Updater,
) : Scoped {

    override fun onEnterScope(scope: Scope) {
        scope.coroutineScope(mainCoroutineContext)
            // Listen to state changes for background workers, if an update is found in the background show a notification
            .launch {
                updater.updateState
                    .collect { state ->
                        if (state is State.UpdateAvailable && state.showNotification.ifNull(true)) {
                            sendUpdateAvailableNotification(state.ota)
                        } else {
                            cancelUpdateAvailableNotification()
                        }
                    }
            }

        // Don't use Dispatchers.Main here. This event gets emitted on boot completion and the Main dispatcher
        // will not be ready to dispatch the event.
        scope.coroutineScope(defaultCoroutineContext)
            .launch {
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

    override fun onExitScope() = Unit

    @SuppressLint("MissingPermission")
    private fun sendUpdateAvailableNotification(ota: Ota) {
        val notificationManager = NotificationManagerCompat.from(application)

        NotificationChannelCompat.Builder(UPDATE_AVAILABLE, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(application.getString(R.string.update_available))
            .setDescription(application.getString(R.string.update_available))
            .build()
            .also { notificationManager.createNotificationChannel(it) }

        val intent = Intent(application, UpdateActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(application, 0, intent, 0)

        NotificationCompat.Builder(application, UPDATE_AVAILABLE)
            .setSmallIcon(R.drawable.ic_baseline_system_update_24)
            .setContentTitle(application.getString(R.string.update_available))
            .setContentText(application.getString(R.string.software_version, ota.version))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            .also { notificationManager.notify(UPDATE_AVAILABLE_NOTIFICATION_ID, it) }

        Logger.test("sendUpdateAvailableNotification: ${ota.url}")
    }

    private fun cancelUpdateAvailableNotification() {
        NotificationManagerCompat.from(application)
            .cancel(UPDATE_AVAILABLE_NOTIFICATION_ID)

        Logger.test("cancelUpdateAvailableNotification")
    }

    @SuppressLint("MissingPermission")
    private fun showUpdateCompleteNotification(success: Boolean) {
        val notificationManager = NotificationManagerCompat.from(application)

        NotificationChannelCompat.Builder(UPDATE_AVAILABLE, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(application.getString(R.string.update_available))
            .setDescription(application.getString(R.string.update_available))
            .build()
            .also { notificationManager.createNotificationChannel(it) }

        val intent = Intent(application, UpdateActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(application, 0, intent, 0)

        val title =
            if (success) {
                application.getString(R.string.update_succeeded)
            } else {
                application.getString(R.string.update_failed)
            }
        val contentText =
            if (success) {
                application.getString(R.string.update_succeeded_content)
            } else {
                application.getString(R.string.update_failed_content)
            }

        NotificationCompat.Builder(application, UPDATE_AVAILABLE)
            .setSmallIcon(R.drawable.ic_baseline_system_update_24)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            .also { notificationManager.notify(UPDATE_FINISHED_NOTIFICATION_ID, it) }

        Logger.test("showUpdateCompleteNotification: $success")
    }

    private fun Boolean?.ifNull(ifNull: Boolean) = this ?: ifNull
}

private const val UPDATE_AVAILABLE = "update_available"
private const val UPDATE_AVAILABLE_NOTIFICATION_ID = 5001
private const val UPDATE_FINISHED_NOTIFICATION_ID = 5002
