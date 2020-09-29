package com.memfault.bort

import android.os.RemoteException
import com.memfault.dumpster.*
import com.memfault.bort.shared.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume


interface DumpsterServiceProvider {
    fun get(): IDumpster?
}

internal class DefaultDumpsterServiceProvider : DumpsterServiceProvider {
    override fun get(): IDumpster? {
        val dumpster: IDumpster? = IDumpster.Stub.asInterface(
            ServiceManagerProxy.getService(DUMPSTER_SERVICE_NAME)
        )
        if (dumpster == null) {
            Logger.w("Failed to get ${DUMPSTER_SERVICE_NAME}")
        }
        return dumpster
    }
}

/**
 * The lowest, valid service version.
 * @note This is not zero because IPC calls to unimplemented methods also seem to return zero.
 */
private const val MINIMUM_VALID_VERSION = 1

private class WrappedService(val service: IDumpster, val basicCommandTimeout: Long) {
    fun getVersion(): Int = service.getVersion()

    /**
     * Invokes a "basic command" on the remote service.
     * See MemfaultDumpster.cpp for command implementations and IDumpster.aidl for the interface.
     */
    suspend fun runBasicCommand(cmdId: Int): String? = withTimeoutOrNull(basicCommandTimeout) {
        suspendCancellableCoroutine<String?> { cont ->
            try {
                service.runBasicCommand(cmdId,
                    object : IDumpsterBasicCommandListener.Stub() {
                        override fun onFinished(statusCode: Int, output: String?) {
                            if (statusCode != 0) {
                                Logger.e("runBasicCommand $cmdId failed with status $statusCode")
                            }
                            cont.resume(output)
                        }

                        override fun onUnsupported() {
                            Logger.e("runBasicCommand $cmdId is not supported")
                            cont.resume(null)
                        }
                    })
            } catch (e: RemoteException) {
                cont.resume(null)
            }
        }
    }
}

class DumpsterClient(
    val serviceProvider: DumpsterServiceProvider = DefaultDumpsterServiceProvider(),
    val basicCommandTimeout: Long = 5000L
) {
    private fun getService(): IDumpster? = serviceProvider.get()

    /**
     * Runs a block with service matching versioning constraints.
     * @return The value returned by block, or null in case the service could not be found or did
     * not satisty the given constraints.
     */
    private inline fun <R> withService(minimumVersion: Int = MINIMUM_VALID_VERSION,
                                       block: WrappedService.() -> R): R? =
        serviceProvider.get()?.let {
            if (it.getVersion() >= minimumVersion) with(WrappedService(
                service = it,
                basicCommandTimeout = basicCommandTimeout
            ), block) else null
        }

    /**
     * Gets all system properties by running the 'getprop' program.
     * @return All system properties, or null in case they could not be retrieved.
     */
    suspend fun getprop(): Map<String, String>? = withService(minimumVersion = 1) {
        return runBasicCommand(IDumpster.CMD_ID_GETPROP)?.let {
            parseGetpropOutput(it)
        }
    }

    /**
     * Convenience helper to get the device serial number system property.
     * @return The device serial number, or null in case they could not be retrieved.
     */
    suspend fun getSerial(): String? = getprop()?.get("ro.serialno")
}
