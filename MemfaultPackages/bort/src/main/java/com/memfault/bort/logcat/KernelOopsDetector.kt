package com.memfault.bort.logcat

import androidx.annotation.VisibleForTesting
import com.memfault.bort.logcat.LogcatLineProcessorResult.ContainsOops
import com.memfault.bort.parsers.LogcatLine
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.time.BaseAbsoluteTime
import com.memfault.bort.tokenbucket.KernelOops
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.HandleEventOfInterest
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.components.SingletonComponent
import java.time.Instant

private const val OOPS_TOKEN_START = "------------[ cut here ]------------"

interface LogcatLineProcessor {
    suspend fun process(line: LogcatLine, packageManagerReport: PackageManagerReport)
    suspend fun finish(lastLogTime: BaseAbsoluteTime): Set<LogcatLineProcessorResult>

    interface Factory {
        fun create(): LogcatLineProcessor
    }
}

enum class LogcatLineProcessorResult {
    ContainsOops,
}

@ContributesMultibinding(SingletonComponent::class)
@AssistedFactory
interface KernelOopsDetectorFactory : LogcatLineProcessor.Factory {
    override fun create(): KernelOopsDetector
}

class KernelOopsDetector @AssistedInject constructor(
    @KernelOops private val tokenBucketStore: TokenBucketStore,
    private val handleEventOfInterest: HandleEventOfInterest,
    private val logcatSettings: LogcatSettings,
) : LogcatLineProcessor {
    @VisibleForTesting var foundOops: Boolean = false

    @VisibleForTesting var oopsTimestamp: Instant? = null

    /**
     * Called for every logcat line, including separators
     */
    override suspend fun process(line: LogcatLine, packageManagerReport: PackageManagerReport) {
        if (!logcatSettings.kernelOopsDataSourceEnabled) return
        if (foundOops) return
        if (line.buffer != "kernel") return
        if (line.message != OOPS_TOKEN_START) return
        foundOops = true
        oopsTimestamp = line.logTime
    }

    /**
     * Called at the end of processing a logcat file
     */
    override suspend fun finish(lastLogTime: BaseAbsoluteTime): Set<LogcatLineProcessorResult> {
        if (!logcatSettings.kernelOopsDataSourceEnabled) return emptySet()
        if (!foundOops) return emptySet()
        if (!tokenBucketStore.takeSimple(tag = "oops")) return emptySet()
        handleEventOfInterest.handleEventOfInterest(lastLogTime)
        return setOf(ContainsOops)
    }
}
