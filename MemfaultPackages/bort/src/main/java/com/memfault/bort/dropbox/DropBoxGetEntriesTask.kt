package com.memfault.bort.dropbox

import android.app.Application
import android.content.Context
import android.os.DropBoxManager
import android.os.RemoteException
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.metrics.CrashHandler
import com.memfault.bort.oneTimeWorkRequest
import com.memfault.bort.periodicWorkRequest
import com.memfault.bort.requester.BortWorkInfo
import com.memfault.bort.requester.PeriodicWorkRequester
import com.memfault.bort.requester.asBortWorkInfo
import com.memfault.bort.settings.DropBoxSettings
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.Lazy
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

private const val WORK_TAG_PERIODIC = "DROPBOX_QUERY"
private const val WORK_TAG_ONE_OFF = "DROPBOX_POLL"
private const val WORK_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.DROPBOX_QUERY_PERIODIC"
private const val WORK_UNIQUE_NAME_ONE_OFF = "com.memfault.bort.work.DROPBOX_QUERY_ONE_OFF"
private const val DEFAULT_RETRY_DELAY_MILLIS: Long = 5000

fun interface DropBoxRetryDelay {
    suspend fun delay()
}

@ContributesBinding(SingletonComponent::class)
class DefaultDropBoxDelay @Inject constructor() : DropBoxRetryDelay {
    override suspend fun delay() = delay(DEFAULT_RETRY_DELAY_MILLIS)
}

class DropBoxGetEntriesTask @Inject constructor(
    private val cursorProvider: ProcessedEntryCursorProvider,
    private val entryProcessors: DropBoxEntryProcessors,
    private val settings: DropBoxSettings,
    private val retryDelay: DropBoxRetryDelay,
    private val dropBoxFilters: DropBoxFilters,
    private val processingMutex: DropboxProcessingMutex,
    private val dropBoxManager: Lazy<DropBoxManager>,
    private val crashHandler: CrashHandler,
) : Task<Unit> {
    override fun getMaxAttempts(input: Unit) = 1
    override fun convertAndValidateInputData(inputData: Data): Unit = Unit
    override suspend fun doWork(input: Unit): TaskResult = doWork()

    suspend fun doWork(): TaskResult {
        if (!settings.dataSourceEnabled or entryProcessors.map.isEmpty()) return TaskResult.SUCCESS

        // Use this periodic task to poke the crash-free-hours processor.
        crashHandler.process()

        // Use a lock, in case the periodic/one-off processing tasks overlap.
        processingMutex.processingMutex.withLock {
            val dropbox = dropBoxManager.get() ?: return TaskResult.FAILURE
            var previousWasNull = false
            var cursor = cursorProvider.makeCursor()
            while (true) {
                val entry = try {
                    dropbox.getNextEntry(null, cursor.timeMillis)
                } catch (e: RemoteException) {
                    Logger.w("Error getting dropbox entries", e)
                    return TaskResult.FAILURE
                } catch (e: SecurityException) {
                    Logger.w("Error getting dropbox entries", e)
                    return TaskResult.FAILURE
                }

                // In case entries are added in quick succession, we'd end up with one worker
                // task and one service create/destroy for *each* DropBox entry. To avoid the
                // overhead this incurs, let's poll once more after a delay to see if there's
                // more coming before finishing this task:
                if (entry == null) {
                    if (previousWasNull) {
                        return TaskResult.SUCCESS
                    }
                    previousWasNull = true
                    retryDelay.delay()
                    cursor = cursor.refresh()
                    continue
                }
                previousWasNull = false

                entry.use {
                    if (entry.tag in dropBoxFilters.tagFilter()) {
                        processEntry(entry)
                    }
                    cursor = cursor.next(entry.timeMillis)
                }
            }
        }
    }

    private suspend fun processEntry(entry: DropBoxManager.Entry) {
        Logger.v("Processing DropBox entry... tag=${entry.tag}, timeMillis=${entry.timeMillis}")
        try {
            entryProcessors.map[entry.tag]?.process(entry)
        } catch (e: Exception) {
            // Broad try/catch to ensure that we *never* get into an exception loop
            Logger.e("Processing entry with tag '${entry.tag}' raised an exception", e)
        }
    }
}

@Singleton
class DropboxProcessingMutex @Inject constructor() {
    val processingMutex = Mutex()
}

fun enqueueOneTimeDropBoxQueryTask(context: Context) {
    oneTimeWorkRequest<DropBoxGetEntriesTask>(
        workDataOf(),
    ) {
        addTag(WORK_TAG_ONE_OFF)
    }.also { workRequest ->
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                WORK_UNIQUE_NAME_ONE_OFF,
                // During tests, KEEP will fail to process files in quick succession, and REPLACE will cancel any jobs
                // in progress (losing that file) - so use APPEND_OR_REPLACE.
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                workRequest,
            )
    }
}

@ContributesMultibinding(SingletonComponent::class)
class DropboxRequester @Inject constructor(
    private val dropBoxSettings: DropBoxSettings,
    private val application: Application,
) : PeriodicWorkRequester() {
    override suspend fun startPeriodic(justBooted: Boolean, settingsChanged: Boolean) {
        periodicWorkRequest<DropBoxGetEntriesTask>(
            dropBoxSettings.pollingInterval,
            workDataOf(),
        ) {
            addTag(WORK_TAG_PERIODIC)
        }.also { workRequest ->
            WorkManager.getInstance(application)
                .enqueueUniquePeriodicWork(
                    WORK_UNIQUE_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest,
                )
        }
    }

    override fun cancelPeriodic() {
        WorkManager.getInstance(application)
            .cancelUniqueWork(WORK_UNIQUE_NAME_PERIODIC)
    }

    override suspend fun enabled(settings: SettingsProvider): Boolean = dropBoxSettings.dataSourceEnabled

    override suspend fun diagnostics(): BortWorkInfo = WorkManager.getInstance(application)
        .getWorkInfosForUniqueWorkFlow(WORK_UNIQUE_NAME_PERIODIC)
        .asBortWorkInfo("dropbox")

    override suspend fun parametersChanged(old: SettingsProvider, new: SettingsProvider): Boolean =
        old.dropBoxSettings.pollingInterval != new.dropBoxSettings.pollingInterval

    suspend fun isScheduledAt(settings: SettingsProvider): Duration? =
        if (enabled(settings)) dropBoxSettings.pollingInterval else null
}
