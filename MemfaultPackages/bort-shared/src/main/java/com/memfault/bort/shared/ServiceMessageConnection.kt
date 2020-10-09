package com.memfault.bort.shared

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.CompletableDeferred

/**
 * Cannot instantiate Message / Messenger in a unit test environment,
 * therefore, Messenger is wrapped so it can be mocked out in unit/integration tests:
 */
interface ServiceMessage {
    fun toMessage(messageFactory: () -> Message = { Message.obtain() }): Message
}

const val DEFAULT_REPLY_TIMEOUT_MILLIS: Long = 5000

abstract class ServiceMessageConnection<SM : ServiceMessage> {
    abstract suspend fun sendAndReceive(message: SM, timeoutMillis: Long = DEFAULT_REPLY_TIMEOUT_MILLIS): SM
}

internal class ReplyMessageHandler<SM : ServiceMessage>(
    looper: Looper,
    private val messageToServiceMessage: (message: Message) -> SM
) : Handler(looper) {
    private val reply = CompletableDeferred<SM>()
    override fun handleMessage(msg: Message) {
        reply.complete(messageToServiceMessage(msg))
    }
    suspend fun awaitReply() = reply.await()
}

class RealServiceMessageConnection<SM : ServiceMessage>(
    private val outboundMessenger: Messenger,
    private val inboundLooper: Looper,
    private val messageToServiceMessage: (message: Message) -> SM
) : ServiceMessageConnection<SM>() {
    override suspend fun sendAndReceive(message: SM, timeoutMillis: Long): SM {
        val replyHandler = ReplyMessageHandler(inboundLooper, messageToServiceMessage)
        val inboundMessenger = Messenger(replyHandler)
        outboundMessenger.send(message.toMessage().apply {
            replyTo = inboundMessenger
        })
        return replyHandler.awaitReply()
    }
}
