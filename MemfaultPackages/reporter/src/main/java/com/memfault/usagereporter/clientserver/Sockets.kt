package com.memfault.usagereporter.clientserver

import java.lang.IllegalStateException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Coroutine wrappers for java channel nio methods.
 */

suspend fun AsynchronousSocketChannel.cConnect(host: String, port: Int) = suspendCancellableCoroutine<Unit> { cont ->
    connect(
        InetSocketAddress(host, port), Unit,
        object : CompletionHandler<Void, Unit> {
            override fun completed(result: Void?, attachment: Unit): Unit = run { cont.resume(Unit) }
            override fun failed(exc: Throwable, attachment: Unit): Unit = run { cont.resumeWithException(exc) }
        }
    )
}

suspend fun AsynchronousByteChannel.cWrite(data: ByteArray) = cWrite(ByteBuffer.wrap(data))

suspend fun AsynchronousByteChannel.cWrite(buffer: ByteBuffer) = suspendCancellableCoroutine<Int> { cont ->
    write(
        buffer, Unit,
        object : CompletionHandler<Int, Unit> {
            override fun completed(result: Int, attachment: Unit): Unit = run { cont.resume(result) }
            override fun failed(exc: Throwable, attachment: Unit): Unit = run { cont.resumeWithException(exc) }
        }
    )
}

suspend fun AsynchronousByteChannel.cRead(
    bytes: Int,
) = suspendCancellableCoroutine<ByteBuffer> { cont ->
    val buffer = ByteBuffer.allocate(bytes)
    read(
        buffer,
        Unit,
        object : CompletionHandler<Int, Unit> {
            override fun completed(result: Int, attachment: Unit): Unit =
                run {
                    if (result != bytes) {
                        cont.resumeWithException(IllegalStateException("Got $result bytes, expecting $bytes"))
                    } else cont.resume(buffer.apply { rewind() })
                }

            override fun failed(exc: Throwable, attachment: Unit): Unit = run { cont.resumeWithException(exc) }
        }
    )
}

suspend fun AsynchronousServerSocketChannel.cAccept() = suspendCancellableCoroutine<AsynchronousSocketChannel> { cont ->
    accept(
        Unit,
        object : CompletionHandler<AsynchronousSocketChannel, Unit> {
            override fun completed(result: AsynchronousSocketChannel, attachment: Unit): Unit = run {
                cont.resume(result)
            }
            override fun failed(exc: Throwable, attachment: Unit): Unit = run { cont.resumeWithException(exc) }
        }
    )
}
