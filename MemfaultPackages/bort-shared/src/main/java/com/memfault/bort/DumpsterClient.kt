package com.memfault.bort

import android.os.PersistableBundle
import android.os.RemoteException
import com.memfault.bort.process.ProcessExecutor
import com.memfault.bort.shared.CONTINUOUS_LOG_DUMP_THRESHOLD_BYTES
import com.memfault.bort.shared.CONTINUOUS_LOG_DUMP_THRESHOLD_TIME_MS
import com.memfault.bort.shared.CONTINUOUS_LOG_DUMP_WRAPPING_TIMEOUT_MS
import com.memfault.bort.shared.CONTINUOUS_LOG_FILTER_SPECS
import com.memfault.bort.shared.CONTINUOUS_LOG_VERSION
import com.memfault.bort.shared.DUMPSTER_SERVICE_NAME
import com.memfault.bort.shared.LogcatFilterSpec
import com.memfault.bort.shared.Logger
import com.memfault.dumpster.IDumpster
import com.memfault.dumpster.IDumpsterBasicCommandListener
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FIELD
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.PROPERTY_SETTER
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface DumpsterServiceProvider {
    fun get(logIfMissing: Boolean = true): IDumpster?
}

@ContributesBinding(scope = SingletonComponent::class)
class DefaultDumpsterServiceProvider @Inject constructor() : DumpsterServiceProvider {
    override fun get(logIfMissing: Boolean): IDumpster? {
        val dumpster: IDumpster? = IDumpster.Stub.asInterface(
            ServiceManagerProxy.getService(DUMPSTER_SERVICE_NAME),
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
                    },
                )
            } catch (e: RemoteException) {
                cont.resume(null)
            }
        }
    }

    suspend fun startContinuousLogging(options: PersistableBundle) = withTimeoutOrNull(basicCommandTimeout) {
        suspendCancellableCoroutine<Unit> { cont ->
            try {
                service.startContinuousLogging(options)
                cont.resume(Unit)
            } catch (e: RemoteException) {
                Logger.e("exception while starting continuous logging", e)
                cont.resume(Unit)
            }
        }
    }

    suspend fun stopContinuousLogging() = withTimeoutOrNull(basicCommandTimeout) {
        suspendCancellableCoroutine<Unit> { cont ->
            try {
                service.stopContinuousLogging()
                cont.resume(Unit)
            } catch (e: RemoteException) {
                Logger.e("exception while stopping continuous logging", e)
                cont.resume(Unit)
            }
        }
    }
}

@Qualifier
@Retention(RUNTIME)
@Target(FIELD, VALUE_PARAMETER, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER)
annotation class BasicCommandTimeout

class DumpsterClient @Inject constructor(
    private val serviceProvider: DumpsterServiceProvider,
    @BasicCommandTimeout private val basicCommandTimeout: Long,
    private val processExecutor: ProcessExecutor,
) {
    private fun getServiceSilently(): IDumpster? = serviceProvider.get(logIfMissing = false)

    /**
     * Runs a block with service matching versioning constraints.
     * @return The value returned by block, or null in case the service could not be found or did
     * not satisty the given constraints.
     */
    private inline fun <R> withService(
        minimumVersion: Int = IDumpster.VERSION_INITIAL,
        block: WrappedService.() -> R,
    ): R? =
        serviceProvider.get()?.let {
            if (it.getVersion() >= minimumVersion) {
                with(
                    WrappedService(
                        service = it,
                        basicCommandTimeout = basicCommandTimeout,
                    ),
                    block,
                )
            } else {
                null
            }
        }

    /**
     * Gets all system properties by running the 'getprop' program.
     * @return All system properties, or null in case they could not be retrieved.
     */
    suspend fun getprop(): Map<String, String>? =
        withService<Map<String, String>?>(minimumVersion = IDumpster.VERSION_INITIAL) {
            return runBasicCommand(IDumpster.CMD_ID_GETPROP)?.let {
                parseGetpropOutput(it)
            }
            // Bort Lite: fall back to getting whatever sysprops we are allowed to, locally.
        } ?: processExecutor.execute(listOf("/system/bin/getprop")) {
            parseGetpropOutput(it.reader().readText())
        }

    /**
     * @return All system property types, or null in case they could not be retrieved.
     */
    suspend fun getpropTypes(): Map<String, String>? =
        withService<Map<String, String>?>(minimumVersion = IDumpster.VERSION_GETPROP_TYPES) {
            return runBasicCommand(IDumpster.CMD_ID_GETPROP_TYPES)?.let {
                parseGetpropOutput(it)
            }
        } ?: processExecutor.execute(listOf("/system/bin/getprop", "-T")) {
            parseGetpropOutput(it.reader().readText())
        } ?: emptyMap() // Types aren't available on Android 8 - fails if we don't do this

    /**
     * Sets a system property (persist.system.memfault.bort.enabled) so that other components may enable / disable
     * themselves when bort enabled state changes.
     */
    suspend fun setBortEnabled(enabled: Boolean) {
        withService(minimumVersion = IDumpster.VERSION_BORT_ENABLED_PROPERTY) {
            runBasicCommand(
                if (enabled) {
                    IDumpster.CMD_ID_SET_BORT_ENABLED_PROPERTY_ENABLED
                } else {
                    IDumpster.CMD_ID_SET_BORT_ENABLED_PROPERTY_DISABLED
                },
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
                if (enabled) {
                    IDumpster.CMD_ID_SET_STRUCTURED_ENABLED_PROPERTY_ENABLED
                } else {
                    IDumpster.CMD_ID_SET_STRUCTURED_ENABLED_PROPERTY_DISABLED
                },
            )
        }
    }

    suspend fun startContinuousLogging(
        filterSpecs: List<LogcatFilterSpec>,
        continuousLogDumpThresholdBytes: Int,
        continuousLogDumpThresholdTime: Duration,
        continuousLogDumpWrappingTimeout: Duration,
    ) {
        withService(minimumVersion = IDumpster.VERSION_BORT_CONTINUOUS_LOGGING) {
            val bundle = PersistableBundle()
            val filterSpecFlags = filterSpecs.map { it.asFlag() }
            bundle.putInt(CONTINUOUS_LOG_VERSION, 1)
            bundle.putStringArray(CONTINUOUS_LOG_FILTER_SPECS, filterSpecFlags.toTypedArray())
            bundle.putInt(CONTINUOUS_LOG_DUMP_THRESHOLD_BYTES, continuousLogDumpThresholdBytes)
            bundle.putLong(
                CONTINUOUS_LOG_DUMP_THRESHOLD_TIME_MS,
                continuousLogDumpThresholdTime.inWholeMilliseconds -
                    CONTINUOUS_LOG_THRESHOLD_MARGIN.inWholeMilliseconds,
            )
            bundle.putLong(
                CONTINUOUS_LOG_DUMP_WRAPPING_TIMEOUT_MS,
                continuousLogDumpWrappingTimeout.inWholeMilliseconds,
            )
            startContinuousLogging(bundle)
        }
    }

    suspend fun stopContinuousLogging() {
        withService(minimumVersion = IDumpster.VERSION_BORT_CONTINUOUS_LOGGING) {
            stopContinuousLogging()
        }
    }

    /**
     * Gets the available version of the MemfaultDumpster service, or null if the service is not available.
     */
    fun availableVersion(): Int? {
        return getServiceSilently()?.version
    }

    companion object {
        /**
         * We apply a small margin to the dump threshold. Without this, if the dump threshold and wrapping timeout are
         * equal then we can miss capturing every other log file, because we are a tiny amount under the threshold.
         */
        private val CONTINUOUS_LOG_THRESHOLD_MARGIN = 3.seconds
    }
}

/**
 * Simply cache
 */
@Singleton
class DumpsterCapabilities @Inject constructor(
    private val dumpsterClient: DumpsterClient,
) {
    private val dumpsterVersion by lazy { dumpsterClient.availableVersion() ?: 0 }

    fun supportsContinuousLogging() = dumpsterVersion >= IDumpster.VERSION_BORT_CONTINUOUS_LOGGING
}
