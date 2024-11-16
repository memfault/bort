package com.memfault.bort.logcat

import com.memfault.bort.parsers.LogcatLine
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.time.BaseAbsoluteTime
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.components.SingletonComponent

internal data class StoragedDiskWearMetric(
    val version: String,
    val eol: Long,
    val lifetimeA: Long,
    val lifetimeB: Long,
)

@ContributesMultibinding(SingletonComponent::class)
@AssistedFactory
interface StoragedDiskWearLogcatDetectorFactory : LogcatLineProcessor.Factory {
    override fun create(): StoragedDiskWearLogcatDetector
}

class StoragedDiskWearLogcatDetector @AssistedInject constructor() : LogcatLineProcessor {

    private val emmcVersionProperty = Reporting.report().stringProperty("disk_wear.emmc_version")
    private val eolProperty = Reporting.report().numberProperty("disk_wear.eol")
    private val lifetimeAProperty = Reporting.report().numberProperty("disk_wear.lifetime_a")
    private val lifetimeBProperty = Reporting.report().numberProperty("disk_wear.lifetime_b")

    override suspend fun process(line: LogcatLine, packageManagerReport: PackageManagerReport) {
        val metric = detect(line.message)

        if (metric != null) {
            emmcVersionProperty.update(metric.version)
            eolProperty.update(metric.eol)
            lifetimeAProperty.update(metric.lifetimeA)
            lifetimeBProperty.update(metric.lifetimeB)
        }
    }

    override suspend fun finish(lastLogTime: BaseAbsoluteTime): Set<LogcatLineProcessorResult> = emptySet()

    internal fun detect(line: String?): StoragedDiskWearMetric? {
        if (line.isNullOrBlank()) return null

        return STORAGED_EMMC_REGEX.find(line)
            ?.let { match ->
                val version = match.groups["version"]?.value
                val eol = match.groups["eol"]?.value?.toLongOrNull()
                val lifetimeA = match.groups["lifetimeA"]?.value?.toLongOrNull()
                val lifetimeB = match.groups["lifetimeB"]?.value?.toLongOrNull()

                if (version != null && eol != null && lifetimeA != null && lifetimeB != null) {
                    StoragedDiskWearMetric(
                        version = version,
                        eol = eol,
                        lifetimeA = lifetimeA,
                        lifetimeB = lifetimeB,
                    )
                } else {
                    null
                }
            }
    }
    companion object {
        private val STORAGED_EMMC_REGEX = Regex(
            """storaged_emmc_info:\s\[(?<version>.+),(?<eol>[0-9]+),""" +
                """(?<lifetimeA>[0-9]{1,2}),(?<lifetimeB>[0-9]{1,2})\]""",
        )
    }
}
