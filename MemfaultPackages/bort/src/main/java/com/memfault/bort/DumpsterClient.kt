package com.memfault.bort

import android.os.RemoteException
import com.memfault.bort.shared.Logger
import com.memfault.dumpster.IDumpster
import com.memfault.dumpster.IDumpsterBasicCommandListener
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Qualifier
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FIELD
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.PROPERTY_SETTER
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

interface DumpsterServiceProvider {
    fun get(logIfMissing: Boolean = true): IDumpster?
}

@ContributesBinding(scope = SingletonComponent::class)
class DefaultDumpsterServiceProvider @Inject constructor() : DumpsterServiceProvider {
    override fun get(logIfMissing: Boolean): IDumpster? {
        val dumpster: IDumpster? = IDumpster.Stub.asInterface(
            ServiceManagerProxy.getService(DUMPSTER_SERVICE_NAME)
        )
        if (dumpster == null) {
            if (logIfMissing) {
                Logger.w("Failed to get $DUMPSTER_SERVICE_NAME")
            }
        }
        return dumpster
    }
}

private class WrappedService(val service: IDumpster, val basicCommandTimeout: Long) {
    fun getVersion(): Int = service.getVersion()

    /**
     * Invokes a "basic command" on the remote service.
     * See MemfaultDumpster.cpp for command implementations and IDumpster.aidl for the interface.
     */
    suspend fun runBasicCommand(cmdId: Int): String? = withTimeoutOrNull(basicCommandTimeout) {
        suspendCancellableCoroutine<String?> { cont ->
            try {
                service.runBasicCommand(
                    cmdId,
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
                    }
                )
            } catch (e: RemoteException) {
                cont.resume(null)
            }
        }
    }
}

@Qualifier
@Retention(RUNTIME)
@Target(FIELD, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
annotation class BasicCommandTimout

class DumpsterClient @Inject constructor(
    val serviceProvider: DumpsterServiceProvider,
    @BasicCommandTimout val basicCommandTimeout: Long,
) {
    private fun getServiceSilently(): IDumpster? = serviceProvider.get(logIfMissing = false)

    /**
     * Runs a block with service matching versioning constraints.
     * @return The value returned by block, or null in case the service could not be found or did
     * not satisty the given constraints.
     */
    private inline fun <R> withService(
        minimumVersion: Int = IDumpster.VERSION_INITIAL,
        block: WrappedService.() -> R
    ): R? =
        serviceProvider.get()?.let {
            if (it.getVersion() >= minimumVersion) with(
                WrappedService(
                    service = it,
                    basicCommandTimeout = basicCommandTimeout
                ),
                block
            ) else null
        }

    /**
     * Gets all system properties by running the 'getprop' program.
     * @return All system properties, or null in case they could not be retrieved.
     */
    suspend fun getprop(): Map<String, String>? = withService(minimumVersion = IDumpster.VERSION_INITIAL) {
        return runBasicCommand(IDumpster.CMD_ID_GETPROP)?.let {
            parseGetpropOutput(it)
        }
    }

    /**
     * Gets all system property types by running the 'getprop -T' program.
     * @return All system propertiy types, or null in case they could not be retrieved.
     */
    suspend fun getpropTypes(): Map<String, String>? = withService(minimumVersion = IDumpster.VERSION_GETPROP_TYPES) {
        return runBasicCommand(IDumpster.CMD_ID_GETPROP_TYPES)?.let {
            parseGetpropOutput(it)
        }
    }

    /**
     * Sets a system property (persist.system.memfault.bort.enabled) so that other components may enable / disable
     * themselves when bort enabled state changes.
     */
    suspend fun setBortEnabled(enabled: Boolean) {
        withService(minimumVersion = IDumpster.VERSION_BORT_ENABLED_PROPERTY) {
            runBasicCommand(
                if (enabled) IDumpster.CMD_ID_SET_BORT_ENABLED_PROPERTY_ENABLED
                else IDumpster.CMD_ID_SET_BORT_ENABLED_PROPERTY_DISABLED
            )
        }
    }

    /**
     * Sets a system property (persist.system.memfault.structured.enabled) so that the structured log daemon may
     * be started/stopped by init as needed.
     */
    suspend fun setStructuredLogEnabled(enabled: Boolean) {
        withService(minimumVersion = IDumpster.VERSION_BORT_ENABLED_PROPERTY) {
            runBasicCommand(
                if (enabled) IDumpster.CMD_ID_SET_STRUCTURED_ENABLED_PROPERTY_ENABLED
                else IDumpster.CMD_ID_SET_STRUCTURED_ENABLED_PROPERTY_DISABLED
            )
        }
    }

    /**
     * Gets the available version of the MemfaultDumpster service, or null if the service is not available.
     */
    fun availableVersion(): Int? {
        return getServiceSilently()?.version
    }
}
