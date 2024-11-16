package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.IO
import com.memfault.bort.logcat.LogcatProcessor
import com.memfault.bort.settings.LogcatCollectionMode.CONTINUOUS
import com.memfault.bort.settings.LogcatSettings
import com.memfault.bort.shared.LogcatCommand
import com.memfault.bort.shared.LogcatFormat
import com.memfault.bort.shared.LogcatFormatModifier
import com.memfault.bort.tokenbucket.ContinuousLogFile
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

private const val DROPBOX_ENTRY_TAG = "memfault_clog"

@ContributesMultibinding(SingletonComponent::class)
class ContinuousLogcatEntryProcessor @Inject constructor(
    private val logcatSettings: LogcatSettings,
    @ContinuousLogFile private val tokenBucketStore: TokenBucketStore,
    private val logcatProcessor: LogcatProcessor,
    @IO private val ioDispatcher: CoroutineContext,
) : EntryProcessor() {
    override val tags: List<String> = listOf(DROPBOX_ENTRY_TAG)

    // Equivalent to what continuous logging produces
    private val continuousLogcatCommand = LogcatCommand(
        format = LogcatFormat.THREADTIME,
        formatModifiers = listOf(
            LogcatFormatModifier.NSEC,
            LogcatFormatModifier.UTC,
            LogcatFormatModifier.YEAR,
            LogcatFormatModifier.UID,
        ),
    )

    private fun allowedByRateLimit(): Boolean =
        tokenBucketStore.takeSimple(key = DROPBOX_ENTRY_TAG, tag = "continuous_log")

    override suspend fun process(entry: DropBoxManager.Entry) {
        withContext(ioDispatcher) {
            val stream = entry.inputStream ?: return@withContext
            stream.use {
                if (!logcatSettings.dataSourceEnabled) {
                    return@withContext
                }

                if (!allowedByRateLimit()) {
                    return@withContext
                }

                logcatProcessor.process(
                    inputStream = stream,
                    command = continuousLogcatCommand,
                    collectionMode = CONTINUOUS,
                )
            }
        }
    }
}
