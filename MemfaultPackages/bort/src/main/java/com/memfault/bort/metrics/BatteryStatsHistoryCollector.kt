package com.memfault.bort.metrics

import com.memfault.bort.TemporaryFileFactory
import com.memfault.bort.diagnostics.BortErrors
import com.memfault.bort.parsers.BatteryStatsHistoryParser
import com.memfault.bort.parsers.BatteryStatsParser
import com.memfault.bort.parsers.BatteryStatsReport
import com.memfault.bort.process.ProcessExecutor
import com.memfault.bort.settings.BatteryStatsSettings
import com.memfault.bort.shared.BatteryStatsCommand
import com.memfault.bort.shared.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RunBatteryStats @Inject constructor(
    private val processExecutor: ProcessExecutor,
) {
    suspend fun runBatteryStats(
        outputStream: OutputStream,
        batteryStatsCommand: BatteryStatsCommand,
        @Suppress("UNUSED_PARAMETER")
        timeout: Duration,
    ) {
        processExecutor.execute(batteryStatsCommand.toList()) { it.copyTo(outputStream) }
    }
}

data class BatteryStatsResult(
    val batteryStatsFileToUpload: File?,
    val batteryStatsHrt: Set<HighResTelemetry.Rollup>,
    val aggregatedMetrics: Map<String, JsonPrimitive>,
    val internalAggregatedMetrics: Map<String, JsonPrimitive>,
) {
    companion object {
        val EMPTY = BatteryStatsResult(
            batteryStatsFileToUpload = null,
            batteryStatsHrt = emptySet(),
            aggregatedMetrics = emptyMap(),
            internalAggregatedMetrics = emptyMap(),
        )
    }
}

class BatteryStatsHistoryCollector @Inject constructor(
    private val temporaryFileFactory: TemporaryFileFactory,
    private val nextBatteryStatsHistoryStartProvider: NextBatteryStatsHistoryStartProvider,
    private val runBatteryStats: RunBatteryStats,
    private val settings: BatteryStatsSettings,
    private val bortErrors: BortErrors,
) {
    suspend fun collect(limit: Duration): BatteryStatsResult {
        temporaryFileFactory.createTemporaryFile(
            "batterystats",
            suffix = ".txt",
        ).useFile { batteryStatsFile, preventDeletion ->
            nextBatteryStatsHistoryStartProvider.historyStart = runBatteryStatsWithLimit(
                initialHistoryStart = nextBatteryStatsHistoryStartProvider.historyStart,
                limit = limit,
                batteryStatsFile = batteryStatsFile,
            )

            if (settings.useHighResTelemetry) {
                val parser = BatteryStatsHistoryParser(batteryStatsFile, bortErrors)
                return parser.parseToCustomMetrics()
            } else {
                preventDeletion()
                return BatteryStatsResult(
                    batteryStatsFileToUpload = batteryStatsFile,
                    batteryStatsHrt = emptySet(),
                    aggregatedMetrics = emptyMap(),
                    internalAggregatedMetrics = emptyMap(),
                )
            }
        }
    }

    private suspend fun runBatteryStatsWithLimit(
        initialHistoryStart: Long,
        limit: Duration,
        batteryStatsFile: File,
    ): Long {
        check(limit > LIMIT_GRACE_MARGIN) { "limit too small: $limit" }

        var historyStart = initialHistoryStart
        for (attempts in 1..3) {
            val (hasTime: Boolean, nextHistoryStart: Long?) = runAndParseBatteryStats(
                batteryStatsFile,
                historyStart,
            )
            checkNotNull(nextHistoryStart) { "No history NEXT found!" }

            if (!hasTime) {
                // If there is no TIME command, it means the given --history-start value lies after the last item in
                // the history. This can happen if the history got reset / wiped.
                check(historyStart != 0L) { "Cursor already reset!" }
                historyStart = 0
                nextBatteryStatsHistoryStartProvider.historyStart = 0
                Logger.logEvent("batterystats", "reset")
                continue
            }

            val historyStartLimit = maxOf(0, nextHistoryStart - limit.inWholeMilliseconds)
            // Calling batterystats, causes a new item to get written, so NEXT will move further out every
            // time it is called with a historyStart that's before the last item. To avoid getting into an infinite
            // loop, take some margin when testing whether the historyStart meets the limit:
            val historyStartComparisonLimit = maxOf(0, historyStartLimit - LIMIT_GRACE_MARGIN.inWholeMilliseconds)
            if (historyStart < historyStartComparisonLimit) {
                // The NEXT time indicated we've got more data than the limit allows, run it again with the
                // --history-start set to (NEXT - limit + margin):
                historyStart = historyStartLimit
                Logger.i(
                    "batterystats historyStart < historyStartComparisonLimit: " +
                        "nextHistoryStart=$nextHistoryStart historyStartComparisonLimit=$historyStartComparisonLimit",
                )
                Logger.logEvent("batterystats", "limit")
                continue
            }

            return nextHistoryStart
        }

        throw Exception("Too many attempts")
    }

    private suspend fun runAndParseBatteryStats(
        batteryStatsFile: File,
        historyStart: Long,
    ): BatteryStatsReport =
        withContext(Dispatchers.IO) {
            batteryStatsFile.outputStream().use {
                runBatteryStats.runBatteryStats(
                    it,
                    BatteryStatsCommand(c = true, historyStart = historyStart),
                    settings.commandTimeout,
                )
            }
            batteryStatsFile.inputStream().use {
                return@withContext BatteryStatsParser(it).parse()
            }
        }
}

private val LIMIT_GRACE_MARGIN = 10.seconds
