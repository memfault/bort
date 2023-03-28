package com.memfault.usagereporter.clientserver

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

/**
 * Write to channel, using multiple writes if required.
 *
 * Multiple writes unlikely to be needed in reality, but helps us test our cRead implementation.
 *
 * @return number of bytes written.
 */
suspend fun AsynchronousByteChannel.cWrite(data: ByteArray) = cWrite(ByteBuffer.wrap(data))

/**
 * Write to channel, using multiple writes if required.
 *
 * Multiple writes unlikely to be needed in reality, but helps us test our cRead implementation.
 *
 * @return number of bytes written.
 */
suspend fun AsynchronousByteChannel.cWrite(buffer: ByteBuffer): Int {
    while (buffer.hasRemaining()) {
        val wrote = cWriteChunk(buffer)
        if (wrote < 1) break
    }
    return buffer.position()
}

/**
 * Perform a single write.
 */
private suspend fun AsynchronousByteChannel.cWriteChunk(buffer: ByteBuffer) = suspendCancellableCoroutine<Int> { cont ->
    write(
        buffer, Unit,
        object : CompletionHandler<Int, Unit> {
            override fun completed(result: Int, attachment: Unit): Unit = run { cont.resume(result) }
            override fun failed(exc: Throwable, attachment: Unit): Unit = run { cont.resumeWithException(exc) }
        }
    )
}

/**
 * Read [bytes] bytes from the channel, using multiple reads if required.
 */
suspend fun AsynchronousByteChannel.cRead(
    bytes: Int,
): ByteBuffer {
    val buffer = ByteBuffer.allocate(bytes)
    while (buffer.hasRemaining()) {
        val read = cReadChunk(buffer)
        if (read < 1) break
    }
    if (buffer.hasRemaining()) throw IllegalStateException("Got ${buffer.position()} bytes, expecting $bytes")
    buffer.rewind()
    return buffer
}

/**
 * Perform a single read.
 */
private suspend fun AsynchronousByteChannel.cReadChunk(
    buffer: ByteBuffer
) = suspendCancellableCoroutine<Int> { cont ->
    read(
        buffer,
        Unit,
        object : CompletionHandler<Int, Unit> {
            override fun completed(result: Int, attachment: Unit): Unit =
                run {
                    cont.resume(result)
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
