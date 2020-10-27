package com.memfault.bort.dropbox

import android.content.Context
import android.content.SharedPreferences
import android.os.DropBoxManager
import android.os.RemoteException
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.memfault.bort.DropBoxSettings
import com.memfault.bort.PREFERENCE_LAST_PROCESSED_DROPBOX_ENTRY_TIME_MILLIS
import com.memfault.bort.ReporterClient
import com.memfault.bort.ReporterServiceConnector
import com.memfault.bort.ServiceGetter
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.oneTimeWorkRequest
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider
import kotlinx.coroutines.delay

private const val WORK_UNIQUE_NAME_PERIODIC = "com.memfault.bort.work.DROPBOX_QUERY"
private const val DEFAULT_RETRY_DELAY_MILLIS: Long = 5000

interface DropBoxLastProcessedEntryProvider {
    var timeMillis: Long
}

class RealDropBoxLastProcessedEntryProvider(
    sharedPreferences: SharedPreferences
) : DropBoxLastProcessedEntryProvider, PreferenceKeyProvider<Long>(
    sharedPreferences = sharedPreferences,
    defaultValue = 0,
    preferenceKey = PREFERENCE_LAST_PROCESSED_DROPBOX_ENTRY_TIME_MILLIS
) {
    override var timeMillis
        get() = super.getValue()
        set(value) = super.setValue(value)
}

fun realDropBoxEntryProcessors(): Map<String, EntryProcessor> {
    return mapOf()
}

class DropBoxGetEntriesTask(
    private val reporterServiceConnector: ReporterServiceConnector,
    private val lastProcessedEntryProvider: DropBoxLastProcessedEntryProvider,
    private val entryProcessors: Map<String, EntryProcessor>,
    private val settings: DropBoxSettings,
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS
) : Task<Unit>() {
    override val maxAttempts: Int = 1
    override fun convertAndValidateInputData(inputData: Data): Unit = Unit
    override suspend fun doWork(worker: TaskRunnerWorker, input: Unit): TaskResult = doWork()

    suspend fun doWork() =
        if (!settings.dataSourceEnabled or entryProcessors.isEmpty()) TaskResult.SUCCESS
        else try {
            reporterServiceConnector.connect { getConnection ->
                if (!getConnection().dropBoxSetTagFilter(entryProcessors.keys.toList())) {
                    return@connect TaskResult.FAILURE
                }

                if (process(getConnection)) TaskResult.SUCCESS else TaskResult.FAILURE
            }
        } catch (e: RemoteException) {
            TaskResult.FAILURE
        }

    private suspend fun process(getConnection: ServiceGetter<ReporterClient>): Boolean {
        var previousWasNull = false
        while (true) {
            val (entry, success) = getConnection().dropBoxGetNextEntry(lastProcessedEntryProvider.timeMillis)
            if (!success) return false

            // In case entries are added in quick succession, we'd end up with one worker
            // task and one service create/destroy for *each* DropBox entry. To avoid the
            // overhead this incurs, lets' poll once more after a delay to see if there's
            // more coming before finishing this task:
            if (entry == null) {
                if (previousWasNull) {
                    return true
                }
                previousWasNull = true
                delay(retryDelayMillis)
                continue
            }
            previousWasNull = false

            processEntry(entry)
            lastProcessedEntryProvider.timeMillis = entry.timeMillis
        }
    }

    private fun processEntry(entry: DropBoxManager.Entry) {
        Logger.v("Processing DropBox entry... tag=${entry.tag}, timeMillis=${entry.timeMillis}")
        try {
            entryProcessors[entry.tag]?.process(entry)
        } catch (e: Exception) {
            // Broad try/catch to ensure that we *never* get into an exception loop
            Logger.e("Processing entry with tag '${entry.tag}' raised an exception", e)
        }
    }
}

fun enqueueDropBoxQueryTask(context: Context) {
    oneTimeWorkRequest<DropBoxGetEntriesTask>(
        workDataOf()
    ).also { workRequest ->
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                WORK_UNIQUE_NAME_PERIODIC,
                ExistingWorkPolicy.KEEP,
                workRequest
            )
    }
}
