package com.memfault.usagereporter

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.*
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import androidx.preference.PreferenceManager
import com.memfault.bort.shared.*
import java.lang.Exception

typealias SendReply = (reply: ServiceMessage) -> Unit

interface DropBoxFilterSettingsProvider {
    var includedTags: Set<String>
}

class DropBoxMessageHandler(
    private val getDropBoxManager: () -> DropBoxManager?,
    private val filterSettingsProvider: DropBoxFilterSettingsProvider
) {
    fun handleSetTagFilterMessage(message: SetTagFilterRequest, sendReply: SendReply) {
        filterSettingsProvider.includedTags = message.includedTags.toSet()
        sendReply(SetTagFilterResponse())
    }

    fun handleGetNextEntryRequest(request: GetNextEntryRequest, sendReply: SendReply) {
        sendReply(when (val db = getDropBoxManager()) {
            null -> ErrorResponse("Failed to get DropBoxManager")
            else -> try {
                GetNextEntryResponse(findFirstMatchingEntry(db, request.lastTimeMillis))
            } catch (e: Exception) {
                ErrorResponse.fromException(e)
            }
        })
    }

    private fun findFirstMatchingEntry(db: DropBoxManager, lastTimeMillis: Long): DropBoxManager.Entry? {
        val includedTagsSet = filterSettingsProvider.includedTags
        var cursorTimeMillis = lastTimeMillis
        while (true) {
            val entry = db.getNextEntry(null, cursorTimeMillis)
            if (entry == null) return null
            if (entry.tag in includedTagsSet) return entry
            cursorTimeMillis = entry.timeMillis
        }
    }
}

// android.os.Message cannot be instantiated in unit tests. The odd code splitting & injecting is
// done to keep the toMessage() and fromMessage() out of the main body of code.
class ReporterServiceMessageHandler(
    private val dropBoxMessageHandler: DropBoxMessageHandler,
    private val serviceMessageFromMessage: (message: Message) -> ReporterServiceMessage,
    private val getSendReply: (message: Message) -> SendReply
) : Handler.Callback {
    override fun handleMessage(message: Message): Boolean {
        val serviceMessage = try {
            serviceMessageFromMessage(message)
        } catch (e: UnknownMessageException) {
            null
        }
        return handleServiceMessage(serviceMessage, message)
    }

    internal fun handleServiceMessage(
        serviceMessage: ReporterServiceMessage?,
        message: Message
    ): Boolean {
        Logger.v("Got serviceMessage: ${serviceMessage}")

        val sendReply: SendReply = {
            try {
                getSendReply(message)(it)
            } catch (e: RemoteException) {
                Logger.e("Failed to send reply: $it", e)
            }
        }
        when (serviceMessage) {
            is SetTagFilterRequest ->
                dropBoxMessageHandler.handleSetTagFilterMessage(serviceMessage, sendReply)
            is GetNextEntryRequest ->
                dropBoxMessageHandler.handleGetNextEntryRequest(serviceMessage, sendReply)
            null -> sendReply(ErrorResponse("Unknown Message: ${message}")).also {
                Logger.e("Unknown Message: ${message}")
            }
            else -> sendReply(ErrorResponse("Cannot handle: ${serviceMessage}")).also {
                Logger.e("Cannot handle: ${serviceMessage}")
            }
        }

        return true
    }
}

class ReporterService : Service() {
    private var messenger: Messenger? = null
    private lateinit var handlerThread: HandlerThread
    private lateinit var messageHandler: ReporterServiceMessageHandler

    override fun onCreate() {
        Logger.d("Creating ReporterService")
        handlerThread = HandlerThread("ReporterService", THREAD_PRIORITY_BACKGROUND).apply {
            start()
        }
        messageHandler = ReporterServiceMessageHandler(
            dropBoxMessageHandler = DropBoxMessageHandler(
                getDropBoxManager = ::getDropBoxManager,
                filterSettingsProvider = RealDropBoxFilterSettingsProvider(
                    PreferenceManager.getDefaultSharedPreferences(this)
                )
            ),
            serviceMessageFromMessage = ReporterServiceMessage.Companion::fromMessage,
            getSendReply = ::getSendReply
        )
    }

    override fun onDestroy() {
        Logger.d("Destroying ReporterService")
        handlerThread.quitSafely()
        Logger.test("Destroyed ReporterService")
    }

    private fun getSendReply(message: Message): SendReply {
        return { serviceMessage: ServiceMessage ->
                message.replyTo.send(serviceMessage.toMessage())
        }
    }

    private fun getDropBoxManager(): DropBoxManager? =
        this.getSystemService(DROPBOX_SERVICE) as DropBoxManager?

    override fun onBind(intent: Intent): IBinder? {
        Logger.d("ReporterService: onBind: $intent")
        return Messenger(Handler(handlerThread.looper, messageHandler)).also {
            messenger = it
        }.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Logger.d("ReporterService: onUnbind: $intent")
        messenger = null
        return false
    }
}

class RealDropBoxFilterSettingsProvider(
    sharedPreferences: SharedPreferences
) : DropBoxFilterSettingsProvider, PreferenceKeyProvider<Set<String>>(
    sharedPreferences = sharedPreferences,
    defaultValue = emptySet(),
    preferenceKey = PREFERENCE_DROPBOX_INCLUDED_ENTRY_TAGS
) {
    override var includedTags
        get() = super.getValue()
        set(value) = super.setValue(value)
}
