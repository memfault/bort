package com.memfault.bort.shared

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.github.michaelbull.result.Result
import com.memfault.bort.shared.result.failure
import com.memfault.bort.shared.result.success
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Cannot instantiate Message / Messenger in a unit test environment,
 * therefore, Messenger is wrapped so it can be mocked out in unit/integration tests:
 */
interface ServiceMessage {
    fun toMessage(messageFactory: () -> Message = { Message.obtain() }): Message
}

val DEFAULT_REPLY_TIMEOUT: Duration = 5.seconds

interface ServiceMessageReplyHandler<SM : ServiceMessage> {
    val replyChannel: Channel<SM>
}

abstract class ServiceMessageConnection<SM : ServiceMessage> {
    abstract fun sendAndGetReplyHandler(message: SM): ServiceMessageReplyHandler<SM>
    suspend fun sendAndReceive(
        message: SM,
        timeout: Duration = DEFAULT_REPLY_TIMEOUT,
    ): Result<SM, Exception> = try {
        Result.success(
            withTimeout(timeout) {
                sendAndGetReplyHandler(message).replyChannel.receive()
            },
        )
    } catch (e: TimeoutCancellationException) {
        Result.failure(e)
    }
}

// RunCommandRequests have 2 replies per interaction:
private const val REPLY_CHANNEL_CAPACITY = 2

internal class ReplyMessageHandler<SM : ServiceMessage>(
    looper: Looper,
    private val messageToServiceMessage: (message: Message) -> SM,
) : Handler(looper), ServiceMessageReplyHandler<SM> {

    override val replyChannel = Channel<SM>(REPLY_CHANNEL_CAPACITY)
    override fun handleMessage(msg: Message) {
        messageToServiceMessage(msg).also { serviceMessage ->
            Logger.v("Got serviceMessage: $serviceMessage")
            if (!replyChannel.trySend(serviceMessage).isSuccess) {
                Logger.e("Failed to offer message to channel: $serviceMessage")
            }
        }
    }
}

class RealServiceMessageConnection<SM : ServiceMessage>(
    private val outboundMessenger: Messenger,
    private val inboundLooper: Looper,
    private val messageToServiceMessage: (message: Message) -> SM,
) : ServiceMessageConnection<SM>() {
    override fun sendAndGetReplyHandler(message: SM): ServiceMessageReplyHandler<SM> {
        val replyHandler = ReplyMessageHandler(inboundLooper, messageToServiceMessage)
        val inboundMessenger = Messenger(replyHandler)
        outboundMessenger.send(
            message.toMessage().apply {
                replyTo = inboundMessenger
            },
        )
        return replyHandler
    }
}
