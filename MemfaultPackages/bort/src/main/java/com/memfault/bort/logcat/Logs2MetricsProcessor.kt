package com.memfault.bort.logcat

import com.memfault.bort.logcat.Logs2MetricsRuleType.CountMatching
import com.memfault.bort.logcat.Logs2MetricsRuleType.Distribution
import com.memfault.bort.logcat.Logs2MetricsRuleType.StringProperty
import com.memfault.bort.logcat.Logs2MetricsRuleType.SumMatching
import com.memfault.bort.logcat.Logs2MetricsRuleType.Unknown
import com.memfault.bort.parsers.LogcatLine
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.reporting.NumericAgg.MAX
import com.memfault.bort.reporting.NumericAgg.MEAN
import com.memfault.bort.reporting.NumericAgg.MIN
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.settings.Logs2MetricsRules
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.BaseAbsoluteTime
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.components.SingletonComponent

@ContributesMultibinding(SingletonComponent::class)
@AssistedFactory
interface Logs2MetricsProcessorFactory : LogcatLineProcessor.Factory {
    override fun create(): Logs2MetricsProcessor
}

class Logs2MetricsProcessor @AssistedInject constructor(
    private val logs2MetricsRules: Logs2MetricsRules,
) : LogcatLineProcessor {
    private val rules by lazy { logs2MetricsRules() }

    override suspend fun process(
        line: LogcatLine,
        packageManagerReport: PackageManagerReport,
    ) {
        rules.forEach { rule ->
            if (line.tag == null || line.priority == null || line.message == null || line.logTime == null) {
                return@forEach
            }
            if (rule.filter.tag != line.tag || rule.filter.priority != line.priority) {
                return@forEach
            }
            val match = rule.regex.matchEntire(line.message) ?: return@forEach

            // If there are placeholders in the metric key (e.g. "oomkill_$1") then we need to replace them with
            // the appropriate match group from the rule.pattern. If the first match group was e.g. "systemd"
            // the metric key should ultimately be "oomkill_systemd".
            val key = try {
                NAME_REGEX.replace(rule.metricName) { p ->
                    val replaceWithIndex = p.groups[NAME_REGEX_GROUP_INDEX]?.value?.toIntOrNull()
                    if (replaceWithIndex == null) {
                        return@replace match.value
                    }
                    // Replace with result n from the line matcher.
                    match.groups[replaceWithIndex]?.value ?: match.value
                }
            } catch (e: IndexOutOfBoundsException) {
                Logger.w("Error processing rule: $rule", e)
                return@forEach
            }

            when (rule.type) {
                CountMatching ->
                    Reporting.report().event(name = key, countInReport = true)
                        .add(timestamp = line.logTime.toEpochMilli(), value = line.message)

                SumMatching -> {
                    if (match.groupValues.size == 2) {
                        val count = match.groupValues.getOrNull(1)?.toInt()
                        if (count != null) {
                            Reporting.report().counter(name = key, sumInReport = true)
                                .incrementBy(by = count, timestamp = line.logTime.toEpochMilli())
                        }
                    }
                }

                Distribution -> {
                    if (match.groupValues.size == 2) {
                        val value = match.groupValues.getOrNull(1)?.toDoubleOrNull()
                        if (value != null) {
                            Reporting.report().distribution(name = key, aggregations = listOf(MIN, MEAN, MAX))
                                .record(value = value, timestamp = line.logTime.toEpochMilli())
                        }
                    }
                }

                StringProperty -> {
                    if (match.groupValues.size == 2) {
                        val value = match.groupValues.getOrNull(1)
                        if (value != null) {
                            Reporting.report().stringProperty(name = key)
                                .update(value = value, timestamp = line.logTime.toEpochMilli())
                        }
                    }
                }

                Unknown -> Unit
            }
        }
    }

    override suspend fun finish(lastLogTime: BaseAbsoluteTime): Set<LogcatLineProcessorResult> = emptySet()

    companion object {
        /** Matches any replacement placeholder in the metric key (e.g. $1, $2) */
        private val NAME_REGEX = Regex("\\$(\\d+)")
        private const val NAME_REGEX_GROUP_INDEX = 1
    }
}
