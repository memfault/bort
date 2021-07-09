package com.memfault.bort.settings

import androidx.work.Data
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.metrics.SETTINGS_CHANGED
import com.memfault.bort.metrics.metrics
import com.memfault.bort.shared.Logger
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.tokenbucket.takeSimple
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

typealias SettingsUpdateCallback = suspend (
    settings: SettingsProvider,
    fetchedSettingsUpdate: FetchedSettingsUpdate
) -> Unit

class SettingsUpdateTask(
    private val deviceInfoProvider: DeviceInfoProvider,
    private val settingsUpdateServiceFactory: () -> SettingsUpdateService,
    private val settingsProvider: SettingsProvider,
    private val storedSettingsPreferenceProvider: StoredSettingsPreferenceProvider,
    private val settingsUpdateCallback: SettingsUpdateCallback,
    private val tokenBucketStore: TokenBucketStore,
) : Task<Unit>() {
    override val getMaxAttempts: () -> Int = { 1 }

    override suspend fun doWork(worker: TaskRunnerWorker, input: Unit): TaskResult {
        if (!tokenBucketStore.takeSimple(tag = "settings")) {
            return TaskResult.FAILURE
        }
        return try {
            val deviceInfo = deviceInfoProvider.getDeviceInfo()
            val new = settingsUpdateServiceFactory().settings(
                deviceInfo.deviceSerial,
                deviceInfo.softwareVersion,
                deviceInfo.hardwareVersion,
            ).data

            val old = storedSettingsPreferenceProvider.get()
            val changed = old != new

            if (changed) {
                metrics()?.increment(SETTINGS_CHANGED)
                storedSettingsPreferenceProvider.set(new)

                // Force a reload on the next settings read
                settingsProvider.invalidate()

                settingsUpdateCallback(settingsProvider, FetchedSettingsUpdate(old = old, new = new))
            }

            Logger.test("Settings updated successfully (changed=$changed)")
            TaskResult.SUCCESS
        } catch (e: HttpException) {
            Logger.d("failed to fetch settings from remote endpoint", e)
            TaskResult.SUCCESS
        } catch (e: SerializationException) {
            Logger.d("failed to deserialize fetched settings from remote endpoint", e)
            TaskResult.SUCCESS
        }
    }

    override fun convertAndValidateInputData(inputData: Data) {
    }
}
