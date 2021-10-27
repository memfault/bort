package com.memfault.bort.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memfault.bort.Bort
import com.memfault.bort.BortSystemCapabilities
import com.memfault.bort.DeviceIdProvider
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.PendingBugReportRequestAccessor
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.dropbox.ProcessedEntryCursorProvider
import com.memfault.bort.ingress.IngressService
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.requester.PeriodicWorkRequester
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.JitterDelayProvider
import com.memfault.bort.shared.Logger
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.tokenbucket.TokenBucketStoreRegistry
import com.memfault.bort.uploader.FileUploadHoldingArea
import okhttp3.OkHttpClient

/** A receiver that only runs if the SDK is enabled. */
abstract class BortEnabledFilteringReceiver(
    actions: Set<String>
) : FilteringReceiver(actions) {
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
) : BroadcastReceiver() {
    protected lateinit var settingsProvider: SettingsProvider
    protected lateinit var bortEnabledProvider: BortEnabledProvider
    protected lateinit var okHttpClient: OkHttpClient
    protected lateinit var deviceIdProvider: DeviceIdProvider
    protected lateinit var deviceInfoProvider: DeviceInfoProvider
    protected lateinit var ingressService: IngressService
    protected lateinit var reporterServiceConnector: ReporterServiceConnector
    protected lateinit var pendingBugReportRequestAccessor: PendingBugReportRequestAccessor
    protected lateinit var fileUploadHoldingArea: FileUploadHoldingArea
    protected lateinit var periodicWorkRequesters: List<PeriodicWorkRequester>
    protected lateinit var tokenBucketStoreRegistry: TokenBucketStoreRegistry
    protected lateinit var rebootEventTokenBucketStore: TokenBucketStore
    protected lateinit var bugReportRequestsTokenBucketStore: TokenBucketStore
    protected lateinit var jitterDelayProvider: JitterDelayProvider
    protected lateinit var dropBoxProcessedEntryCursorProvider: ProcessedEntryCursorProvider
    protected lateinit var bortSystemCapabilities: BortSystemCapabilities
    protected lateinit var builtInMetricsStore: BuiltinMetricsStore
    protected lateinit var temporaryFileFactory: TemporaryFileFactory

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
        deviceInfoProvider = it.deviceInfoProvider
        ingressService = it.ingressService
        reporterServiceConnector = it.reporterServiceConnector
        pendingBugReportRequestAccessor = it.pendingBugReportRequestAccessor
        fileUploadHoldingArea = it.fileUploadHoldingArea
        periodicWorkRequesters = it.periodicWorkRequesters
        tokenBucketStoreRegistry = it.tokenBucketStoreRegistry
        rebootEventTokenBucketStore = it.rebootEventTokenBucketStore
        bugReportRequestsTokenBucketStore = it.bugReportRequestsTokenBucketStore
        jitterDelayProvider = it.jitterDelayProvider
        dropBoxProcessedEntryCursorProvider = it.dropBoxProcessedEntryCursorProvider
        bortSystemCapabilities = it.bortSystemCapabilities
        builtInMetricsStore = it.metrics
        temporaryFileFactory = it.temporaryFileFactory
    }

    abstract fun onIntentReceived(context: Context, intent: Intent, action: String)
}
