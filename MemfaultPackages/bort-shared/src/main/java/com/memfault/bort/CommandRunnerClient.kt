package com.memfault.bort

import android.os.ParcelFileDescriptor
import android.os.ParcelFileDescriptor.AutoCloseInputStream
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.runCatching
import com.memfault.bort.CommandRunnerMode.BortCreatesPipes
import com.memfault.bort.CommandRunnerMode.ReporterCreatesPipes
import com.memfault.bort.shared.CommandRunnerOptions
import com.memfault.bort.shared.ErrorResponse
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.MINIMUM_VALID_VERSION_REPORTER_CREATES_PIPES
import com.memfault.bort.shared.ReporterServiceMessage
import com.memfault.bort.shared.RunCommandContinue
import com.memfault.bort.shared.RunCommandResponse
import com.memfault.bort.shared.ServiceMessageReplyHandler
import com.memfault.bort.shared.result.StdResult
import java.io.Closeable
import java.io.FileInputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private val DEFAULT_INPUT_STREAM_TIMEOUT = 30.seconds
private val DEFAULT_RESPONSE_TIMEOUT = 5.seconds

interface CommandRunnerClientFactory {
    fun create(
        mode: CommandRunnerClient.StdErrMode = CommandRunnerClient.StdErrMode.NULL,
        timeout: Duration = CommandRunnerOptions.DEFAULT_TIMEOUT,
        reporterVersion: Int,
    ): CommandRunnerClient
}

sealed class CommandRunnerMode {
    /**
     * From reporter 4.8.0 onwards: reporter creates the pipe, and sends it to bort in [RunCommandContinue].
     */
    object ReporterCreatesPipes : CommandRunnerMode()

    /**
     * Until reporter 4.7.0: bort creates pipe and sends to reporter to stream into. Doesn't work on Android 13+.
     *
     * Only here for backwards compatibility (where bort is updated but reporter is not).
     */
    class BortCreatesPipes(val out: FileInputStream, val writeFd: ParcelFileDescriptor) : CommandRunnerMode()
}

class CommandRunnerClient(
    val options: CommandRunnerOptions,
    private val mode: CommandRunnerMode,
    private val autoCloseInputStreamFactory: (ParcelFileDescriptor) -> FileInputStream = ::AutoCloseInputStream,
) : Closeable {
    private val inputStream = CompletableDeferred<FileInputStream>()
    private val response = CompletableDeferred<RunCommandResponse>()

    inner class Invocation {
        suspend fun awaitInputStream(timeout: Duration = DEFAULT_INPUT_STREAM_TIMEOUT) =
            runCatching { withTimeout(timeout) { inputStream.await() } }

        suspend fun awaitResponse(timeout: Duration = DEFAULT_RESPONSE_TIMEOUT) =
            runCatching { withTimeout(timeout) { response.await() } }
    }

    companion object RealFactory : CommandRunnerClientFactory {
        override fun create(
            mode: StdErrMode,
            timeout: Duration,
            reporterVersion: Int,
        ): CommandRunnerClient {
            if (reporterVersion >= MINIMUM_VALID_VERSION_REPORTER_CREATES_PIPES) {
                return CommandRunnerClient(
                    options = CommandRunnerOptions(
                        outFd = null,
                        redirectErr = mode == StdErrMode.REDIRECT,
                        timeout = timeout,
                    ),
                    mode = ReporterCreatesPipes,
                )
            } else {
                // Backwards compatibility: reporter is older and doesn't support creating pipes.
                // Note: this does not work on Android 13+.
                val (readFd, writeFd) = ParcelFileDescriptor.createPipe()
                return CommandRunnerClient(
                    options = CommandRunnerOptions(
                        outFd = writeFd, redirectErr = mode == StdErrMode.REDIRECT, timeout = timeout
                    ),
                    mode = BortCreatesPipes(out = AutoCloseInputStream(readFd), writeFd = writeFd)
                )
            }
        }
    }

    override fun close() {
        when (mode) {
            is BortCreatesPipes -> {
                mode.writeFd.close()
                mode.out.close()
            }
            ReporterCreatesPipes -> Unit
        }
    }

    private suspend fun consume(channel: Channel<ReporterServiceMessage>) {
        for (message in channel) {
            when (message) {
                is RunCommandContinue -> {
                    when (mode) {
                        is BortCreatesPipes -> {
                            /**
                             * This deals with a peculiarity of ParcelFileDescriptor.createPipe:
                             * if the write end PFD of the pipe is closed before it is sent to the other process,
                             * an exception will happen when trying to send it over, while parcelling the PFD.
                             * If the PFD is *not* closed explicitly (or references to it still exist), reading
                             * from the input stream will block indefinitely!
                             */
                            mode.writeFd.close()
                            inputStream.complete(mode.out)
                        }
                        ReporterCreatesPipes -> {
                            if (message.pfd != null) {
                                inputStream.complete(autoCloseInputStreamFactory(message.pfd))
                            } else {
                                response.completeExceptionally(CancellationException("null pfd!"))
                            }
                        }
                    }
                }

                is RunCommandResponse -> response.complete(message)
                is ErrorResponse -> Logger.e("Error response: ${message.error}")
                else -> Logger.e("Unexpected response: $message")
            }
        }

        inputStream.completeExceptionally(CancellationException())
        response.completeExceptionally(CancellationException())
    }

    suspend fun <R> run(
        block: suspend (Invocation) -> StdResult<R>,
        sendRequest: suspend (CommandRunnerOptions) -> StdResult<ServiceMessageReplyHandler<ReporterServiceMessage>>
    ): StdResult<R> =
        use {
            sendRequest(options).andThen { replyHandler ->
                coroutineScope {
                    launch { consume(replyHandler.replyChannel) }
                    block(Invocation()).also { coroutineContext.cancelChildren() }
                }
            }
        }

    enum class StdErrMode {
        NULL,
        REDIRECT,
    }
}
