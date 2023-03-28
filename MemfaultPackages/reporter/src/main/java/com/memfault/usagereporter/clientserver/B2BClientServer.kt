package com.memfault.usagereporter.clientserver

import android.content.Context
import android.os.DropBoxManager
import android.os.ParcelFileDescriptor
import androidx.annotation.VisibleForTesting
import com.memfault.bort.fileExt.deleteSilently
import com.memfault.bort.shared.BuildConfig
import com.memfault.bort.shared.ClientServerMode
import com.memfault.bort.shared.ClientServerMode.DISABLED
import com.memfault.bort.shared.Logger
import com.memfault.usagereporter.ReporterSettings
import com.memfault.usagereporter.clientserver.BortMessage.Companion.readMessages
import com.memfault.usagereporter.clientserver.BortMessage.SendFileMessage
import com.memfault.usagereporter.clientserver.RealB2BClientServer.Companion.start
import com.memfault.usagereporter.getDropBoxManager
import java.io.File
import java.nio.channels.AsynchronousSocketChannel
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.whileSelect

/**
 * Send/receive files to/from Bort running on another device, over a socket connection.
 *
 * Either [ClientServerMode.CLIENT] or [ClientServerMode.SERVER] - else this class is no-op (which is the normal
 * expected case for most installations).
 *
 * See [ClientServerMode] for how this is configured via system property.
 *
 * Host/port are configured in bort.properties.
 */
interface B2BClientServer {
    fun enqueueFile(dropboxTag: String, descriptor: ParcelFileDescriptor)

    companion object {
        fun create(clientServerMode: ClientServerMode, context: Context, reporterSettings: ReporterSettings) =
            when (clientServerMode) {
                DISABLED -> NoOpB2BClientServer
                else -> RealB2BClientServer(
                    clientServerMode = clientServerMode,
                    getDropBoxManager = context::getDropBoxManager,
                    uploadsDir = File(context.filesDir, "client-server-uploads"),
                    cacheDir = context.cacheDir,
                    port = BuildConfig.CLIENT_SERVER_PORT,
                    host = BuildConfig.CLIENT_SERVER_HOST,
                    reporterSettings = reporterSettings,
                ).also { it.start() }
            }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RealB2BClientServer(
    private val clientServerMode: ClientServerMode,
    private val getDropBoxManager: () -> DropBoxManager?,
    val uploadsDir: File,
    private val cacheDir: File,
    private val host: String,
    private val port: Int,
    private val retryDelay: Duration = 15.seconds,
    reporterSettings: ReporterSettings,
) : B2BClientServer {
    val uploadsQueue = RealSendfileQueue(uploadsDir, reporterSettings)
    val clientOrServer = create(clientServerMode)

    // Only used in E2E tests, where files are looped back using localhost.
    private val optionalLocalTestServer = localTestServer(clientServerMode)

    suspend fun start(scope: CoroutineScope) {
        Logger.d("ClientServer start: host=$host clientServerMode=$clientServerMode port=$port")
        uploadsQueue.pushOldestFile()
        // Run connector async
        scope.launch { clientOrServer.run(this) }
        // In E2E test mode only (otherwise no-op), also run a loopback server.
        scope.launch { optionalLocalTestServer?.run(this) }
    }

    /**
     * Write the file descriptor to disk, and enqueue for upload to remote Bort instance.
     */
    override fun enqueueFile(dropboxTag: String, descriptor: ParcelFileDescriptor) {
        val file = createFile(dropboxTag)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
            uploadsQueue.pushOldestFile()
        }
    }

    private fun createFile(dropboxTag: String) = File(uploadsDir, "${UUID.randomUUID()}.$dropboxTag").apply {
        uploadsDir.mkdirs()
        deleteSilently()
    }

    private fun create(clientServerMode: ClientServerMode) = when (clientServerMode) {
        DISABLED ->
            throw IllegalArgumentException("B2BClientServer should not be running with ClientServerMode.DISABLED")
        ClientServerMode.CLIENT -> ClientConnector(
            port = port,
            host = host,
            connectionHandler = ConnectionHandler(uploadsQueue, getDropBoxManager, cacheDir),
            retryDelay = retryDelay,
        )
        ClientServerMode.SERVER -> ServerConnector(
            port = port,
            connectionHandler = ConnectionHandler(uploadsQueue, getDropBoxManager, cacheDir),
            retryDelay = retryDelay,
        )
    }

    // Just for test env, where we run both on same device.
    private fun localTestServer(clientServerMode: ClientServerMode): Connector? =
        if (clientServerMode == ClientServerMode.CLIENT && host == LOCALHOST) {
            ServerConnector(
                port = port,
                connectionHandler = ConnectionHandler(
                    // No-Op Files = not sending files
                    NoOpSendfileQueue,
                    getDropBoxManager, cacheDir
                ),
                retryDelay = retryDelay,
            ).also { Logger.i("Using test client+server mode: client") }
        } else if (clientServerMode == ClientServerMode.SERVER && host == LOCALHOST) {
            ClientConnector(
                host = host,
                port = port,
                connectionHandler = ConnectionHandler(
                    // No-Op Files = not sending files
                    NoOpSendfileQueue,
                    getDropBoxManager, cacheDir
                ),
                retryDelay = retryDelay,
            ).also { Logger.i("Using test client+server mode: server") }
        } else null

    companion object {
        private const val LOCALHOST = "127.0.0.1"

        fun RealB2BClientServer.start() = CoroutineScope(Dispatchers.IO).launch { start(this) }
    }
}

object NoOpB2BClientServer : B2BClientServer {
    override fun enqueueFile(dropboxTag: String, descriptor: ParcelFileDescriptor) {
        Logger.w("Received file to forward, but not configured to forward files!")
    }
}

/**
 * Handles sending/receiving messages over an established socket connection.
 */
@ExperimentalCoroutinesApi
class ConnectionHandler(
    private val files: SendFileQueue,
    private val getDropBoxManager: () -> DropBoxManager?,
    private val tempDirectory: File,
) {
    /**
     * Handle this connection, until an error occurs - then an IOException is expected.
     */
    @FlowPreview
    suspend fun run(channel: AsynchronousSocketChannel, scope: CoroutineScope) {
        val incomingMessages = channel.readMessages(tempDirectory, scope)
        val filesChannel = files.nextFile.produceIn(scope)
        runChannels(channel, incomingMessages, filesChannel)
    }

    @VisibleForTesting
    suspend fun runChannels(
        channel: AsynchronousSocketChannel,
        incomingMessages: ReceiveChannel<BortMessage>,
        filesChannel: ReceiveChannel<File?>,
    ) {
        whileSelect {
            filesChannel.onReceiveCatching { result ->
                result.whileSelecting { file ->
                    file?.let {
                        channel.writeMessage(SendFileMessage(file, dropboxTag = file.extension))
                        file.deleteSilently()
                        files.pushOldestFile()
                    }
                }
            }
            incomingMessages.onReceiveCatching { result ->
                result.whileSelecting { message ->
                    when (message) {
                        is SendFileMessage -> {
                            getDropBoxManager()?.addFile(message.dropboxTag, message.file, 0)
                            message.file.deleteSilently()
                        }
                    }
                }
            }
        }
    }
}

/**
 * For use in a whileSelect block: handle the result if received successfully, else return false.
 */
suspend fun <E> ChannelResult<E>.whileSelecting(block: suspend (E) -> Unit): Boolean = try {
    block(getOrThrow())
    true
} catch (e: Exception) {
    false
}
