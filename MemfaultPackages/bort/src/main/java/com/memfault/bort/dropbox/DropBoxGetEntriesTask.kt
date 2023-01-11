package com.memfault.bort.dropbox

import android.content.Context
import android.os.DropBoxManager
import android.os.RemoteException
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.github.michaelbull.result.onFailure
import com.memfault.bort.ReporterClient
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.ServiceGetter
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.metrics.BuiltinMetricsStore
import com.memfault.bort.oneTimeWorkRequest
import com.memfault.bort.settings.BortEnabledProvider
import com.memfault.bort.settings.DropBoxSettings
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.delay

private const val WORK_TAG = "DROPBOX_QUERY"
private const val WORK_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.DROPBOX_QUERY"
private const val DEFAULT_RETRY_DELAY_MILLIS: Long = 5000

fun interface DropBoxRetryDelay {
    suspend fun delay()
}

@ContributesBinding(SingletonComponent::class)
class DefaultDropBoxDelay @Inject constructor() : DropBoxRetryDelay {
    override suspend fun delay() = delay(DEFAULT_RETRY_DELAY_MILLIS)
}

class DropBoxConfigureFilterSettings @Inject constructor(
    private val reporterServiceConnector: ReporterServiceConnector,
    private val entryProcessors: DropBoxEntryProcessors,
    private val settings: DropBoxSettings,
    private val bortEnabledProvider: BortEnabledProvider,
) {
    suspend fun configureFilterSettings() {
        val tags = if (bortEnabledProvider.isEnabled()) {
            entryProcessors.map.keys.subtract(settings.excludedTags).toList()
        } else {
            emptyList()
        }
        reporterServiceConnector.connect { getConnection ->
            getConnection()
                .dropBoxSetTagFilter(tags)
                .onFailure {
                    Logger.d("Failed to configure dropbox tags")
                }
        }
    }
}

class DropBoxGetEntriesTask @Inject constructor(
    private val reporterServiceConnector: ReporterServiceConnector,
    private val cursorProvider: ProcessedEntryCursorProvider,
    private val entryProcessors: DropBoxEntryProcessors,
    private val settings: DropBoxSettings,
    private val retryDelay: DropBoxRetryDelay,
    override val metrics: BuiltinMetricsStore,
) : Task<Unit>() {
    override val getMaxAttempts: () -> Int = { 1 }
    override fun convertAndValidateInputData(inputData: Data): Unit = Unit
    override suspend fun doWork(worker: TaskRunnerWorker, input: Unit): TaskResult = doWork()

    suspend fun doWork() =
        if (!settings.dataSourceEnabled or entryProcessors.map.isEmpty()) TaskResult.SUCCESS
        else try {
            reporterServiceConnector.connect { getConnection ->
                if (process(getConnection)) TaskResult.SUCCESS else TaskResult.FAILURE
            }
        } catch (e: RemoteException) {
            Logger.w("Error getting dropbox entry", e)
            TaskResult.FAILURE
        }

    private suspend fun process(getConnection: ServiceGetter<ReporterClient>): Boolean {
        var previousWasNull = false
        var cursor = cursorProvider.makeCursor()
        while (true) {
            val (entry, error) = getConnection().dropBoxGetNextEntry(cursor.timeMillis)
            if (error != null) return false

            // In case entries are added in quick succession, we'd end up with one worker
            // task and one service create/destroy for *each* DropBox entry. To avoid the
            // overhead this incurs, lets' poll once more after a delay to see if there's
            // more coming before finishing this task:
            if (entry == null) {
                if (previousWasNull) {
                    return true
                }
                previousWasNull = true
                retryDelay.delay()
                cursor = cursor.refresh()
                continue
            }
            previousWasNull = false

            entry.use {
                processEntry(entry)
                cursor = cursor.next(entry.timeMillis)
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

fun enqueueDropBoxQueryTask(context: Context) {
    oneTimeWorkRequest<DropBoxGetEntriesTask>(
        workDataOf()
    ) {
        addTag(WORK_TAG)
    }.also { workRequest ->
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                WORK_UNIQUE_NAME_PERIODIC,
                // During tests, KEEP will fail to process files in quick succession, and REPLACE will cancel any jobs
                // in progress (losing that file) - so use APPEND_OR_REPLACE.
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                workRequest
            )
    }
}
