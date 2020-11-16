package com.memfault.bort

import com.memfault.bort.shared.ReporterServiceMessage
import com.memfault.bort.shared.ServiceMessageReplyHandler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.sendBlocking

class TestReplyHandler : ServiceMessageReplyHandler<ReporterServiceMessage> {
    override val replyChannel = Channel<ReporterServiceMessage>(Channel.UNLIMITED)

    companion object {
        fun withResponses(vararg messages: ReporterServiceMessage) =
            TestReplyHandler().apply {
                for (message in messages) {
                    replyChannel.sendBlocking(message)
                }
            }
    }
}
