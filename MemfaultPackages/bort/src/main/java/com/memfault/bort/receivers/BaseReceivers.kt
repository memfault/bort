package com.memfault.bort.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memfault.bort.*
import com.memfault.bort.ingress.IngressService
import com.memfault.bort.shared.Logger
import okhttp3.OkHttpClient

/** A receiver that only runs if the SDK is enabled. */
abstract class BortEnabledFilteringReceiver(
    actions: Set<String>
): FilteringReceiver(actions) {
    override fun onIntentReceived(context: Context, intent: Intent, action: String) {
        if (!bortEnabledProvider.isEnabled()) {
            Logger.i("Bort not enabled, not running receiver")
            return
        }
        onReceivedAndEnabled(context, intent, action)
    }

    abstract fun onReceivedAndEnabled(context: Context, intent: Intent, action: String)
}

/** A receiver that filters intents for the specified actions. */
abstract class FilteringReceiver(
    private val actions: Set<String>
): BroadcastReceiver() {
    protected lateinit var settingsProvider: SettingsProvider
    protected lateinit var bortEnabledProvider: BortEnabledProvider
    protected lateinit var okHttpClient: OkHttpClient
    protected lateinit var deviceIdProvider: DeviceIdProvider
    protected lateinit var ingressService: IngressService

    override fun onReceive(context: Context?, intent: Intent?) {
        Logger.v("Received action=${intent?.action}")
        context ?: return
        intent ?: return
        intent.action?.let {
            if (!actions.contains(it)) {
                return
            }
            bind()
            Logger.v("Handling $it")
            onIntentReceived(context, intent, it)
            Logger.test("Handled $it")
        }
    }

    protected fun bind() = Bort.appComponents().also {
        settingsProvider = it.settingsProvider
        bortEnabledProvider = it.bortEnabledProvider
        okHttpClient = it.okHttpClient
        deviceIdProvider = it.deviceIdProvider
        ingressService = it.ingressService
    }

    abstract fun onIntentReceived(context: Context, intent: Intent, action: String)
}
