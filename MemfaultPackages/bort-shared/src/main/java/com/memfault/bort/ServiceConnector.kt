package com.memfault.bort

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.memfault.bort.shared.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

typealias ServiceGetter<S> = suspend () -> S

/**
 * Helper that exposes a coroutines-based API to bind and connect to Binder services.
 */
abstract class ServiceConnector<S>(val context: Context, val componentName: ComponentName) {
    // Protects binding related shared state (vars: clientCount, bound)
    private val bindMutex = Mutex()
    private var clientCount = 0
    var bound = false
        private set

    val hasClients: Boolean
        get() = clientCount > 0

    // Protects service related shared state (vars: service)
    val serviceLock = ReentrantLock()
    private var service: CompletableDeferred<S> = CompletableDeferred<S>().also {
        it.completeExceptionally(Exception("Initial"))
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            Logger.d("onServiceConnected: $className")
            service.complete(createServiceWithBinder(binder))
        }

        override fun onServiceDisconnected(className: ComponentName) {
            Logger.e("onServiceDisonnected: $className")

            // Should not happen, but just in case onServiceDisconnected got called before onServiceConnected,
            // ensure the Deferred is completed:
            service.completeExceptionally(RemoteException("Service $className disconnected"))

            // Poke in a new Deferred for the next onServiceConnected callback:
            replaceServiceDeferred()
        }

        override fun onBindingDied(name: ComponentName?) {
            Logger.e("onBindingDied: $name")
            service.completeExceptionally(RemoteException("Binding for $name died"))
            // Should never happen because the service is part of a system app.
        }

        override fun onNullBinding(name: ComponentName?) {
            Logger.e("onNullBinding: $name")
            service.completeExceptionally(RemoteException("Null binding for $name"))
            // Programmer error in the service. Don't bother trying to recover.
        }
    }

    abstract fun createServiceWithBinder(binder: IBinder): S

    private fun replaceServiceDeferred() {
        serviceLock.withLock {
            if (!service.isCompleted) {
                if (bound) {
                    throw IllegalStateException("Should not replace non-completed deferred service")
                } else {
                    // Happens when connect() got called again while the service connection from a previous connect()
                    // call had not been connected yet and the connect block had already returned and triggered
                    // unbinding of the service again. There *should not* be anything awaiting the CompletableDeferred,
                    // but let's complete it for good measure:
                    service.completeExceptionally(Exception("ReplaceServiceDeferred"))
                }
            }
            service = CompletableDeferred()
        }
    }

    private fun bind() {
        check(!bound) { "bind() called but already bound!" }

        replaceServiceDeferred()

        bound = Intent().apply {
            component = componentName
        }.let { intent ->
            try {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (e: SecurityException) {
                // RemoteException constructor doesn't take "cause"
                throw RemoteException("Failed to bind $componentName due to $e")
            }
        }
        if (!bound) {
            throw RemoteException("Failed to bind $componentName")
        }
    }

    private fun unbind() {
        check(bound) { "unbind() called but already unbound!" }

        context.unbindService(connection)
        bound = false
    }

    // Awaits the _latest_ service future:
    private suspend fun getService() = service.await()

    suspend fun <R> connect(block: suspend (getService: ServiceGetter<S>) -> R): R {
        bindMutex.withLock {
            // First client to use connect, bind the service:
            if (clientCount++ == 0) {
                try {
                    bind()
                } catch (e: RemoteException) {
                    --clientCount
                    throw e
                }
            }
        }

        val result = try {
            block(this::getService)
        } finally {
            bindMutex.withLock {
                // Last client to use connect, unbind the service:
                if (--clientCount == 0) {
                    unbind()
                }
            }
        }

        return result
    }
}
