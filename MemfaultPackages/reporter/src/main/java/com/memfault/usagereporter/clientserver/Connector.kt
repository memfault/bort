package com.memfault.usagereporter.clientserver

import androidx.annotation.VisibleForTesting
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.shared.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import org.jetbrains.annotations.TestOnly
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

interface Connector {
    suspend fun run(scope: CoroutineScope)

    @VisibleForTesting
    fun close()
}

private val CONNECTED_METRIC =
    Reporting.report().counter(name = "clientserver_connected", sumInReport = true, internal = true)
private val DISCONNECTED_METRIC =
    Reporting.report().counter(name = "clientserver_disconnected", sumInReport = true, internal = true)

/**
 * Connect to a UsageRepoter soicket server, and send/receive files.
 *
 * Used when vendor.memfault.bort.client.server.mode is set to "client".
 */
@OptIn(FlowPreview::class)
class ClientConnector(
    private val port: Int,
    private val host: String,
    private val connectionHandler: ConnectionHandler,
    private val retryDelay: Duration,
) : Connector {
    var socket = AsynchronousSocketChannel.open()

    override suspend fun run(scope: CoroutineScope) {
        // This is just here so that we don't attempt to connect before the server socket is set up during E2E tests
        // (the retry would be too long for the test).
        delay(250.milliseconds)
        while (true) {
            try {
                Logger.d("ClientConnector: open on $port")
                socket.use {
                    socket.cConnect(host = host, port = port)
                    Logger.d("ClientConnector: connected")
                    CONNECTED_METRIC.increment()
                    connectionHandler.run(RealASCWrapper(socket), scope)
                }
            } catch (e: Exception) {
                // Any exception either was caused by, or triggers, disconnection, and a reconnection attempt.
                Logger.d("ClientConnector: Disconnected", e)
            }
            DISCONNECTED_METRIC.increment()

            // Delay before retrying
            delay(retryDelay)
            socket = AsynchronousSocketChannel.open()
        }
    }

    @TestOnly
    override fun close() {
        socket.close()
    }
}

/**
 * Run a UsageReporter socket server, to send/receive files.
 *
 * Used when vendor.memfault.bort.client.server.mode is set to "server".
 */
@OptIn(FlowPreview::class)
class ServerConnector(
    private val port: Int,
    private val connectionHandler: ConnectionHandler,
    private val retryDelay: Duration,
) : Connector {
    var socket = AsynchronousServerSocketChannel.open()

    override suspend fun run(scope: CoroutineScope) {
        while (true) {
            try {
                Logger.d("ServerConnector: open on $port")
                socket.use {
                    socket.bind(InetSocketAddress(port))
                    val serverSocket = socket.cAccept()
                    Logger.d("ServerConnector: connected")
                    CONNECTED_METRIC.increment()
                    serverSocket.use {
                        connectionHandler.run(RealASCWrapper(serverSocket), scope)
                    }
                }
            } catch (e: Exception) {
                // Any exception either was caused by, or triggers, disconnection, and a reconnection attempt.
                Logger.d("ServerConnector: Disconnected", e)
                DISCONNECTED_METRIC.increment()
            }
            // Delay before retrying
            delay(retryDelay)
            socket = AsynchronousServerSocketChannel.open()
        }
    }

    @TestOnly
    override fun close() {
        socket.close()
    }
}
