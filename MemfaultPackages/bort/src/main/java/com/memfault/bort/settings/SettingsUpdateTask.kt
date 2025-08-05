package com.memfault.bort.settings

import androidx.work.Data
import com.memfault.bort.Task
import com.memfault.bort.TaskResult
import com.memfault.bort.deviceconfig.ClientServerUpdateClientDeviceConfigUseCase
import com.memfault.bort.deviceconfig.FetchDeviceConfigUseCase
import javax.inject.Inject

class SettingsUpdateTask @Inject constructor(
    private val fetchDeviceConfigUseCase: FetchDeviceConfigUseCase,
    private val settingsUpdateHandler: SettingsUpdateHandler,
    private val clientServerUpdateClientDeviceConfigUseCase: ClientServerUpdateClientDeviceConfigUseCase,
) : Task<Unit> {
    override fun getMaxAttempts(input: Unit) = 1

    override suspend fun doWork(input: Unit): TaskResult {
        val deviceConfig = fetchDeviceConfigUseCase.get()

        if (deviceConfig != null) {
            settingsUpdateHandler.handleDeviceConfig(deviceConfig)

            // If we were able to get a device config for the current device, also update the client server client
            // device, if necessary.
            clientServerUpdateClientDeviceConfigUseCase.update()
        }

        return TaskResult.SUCCESS
    }

    override fun convertAndValidateInputData(inputData: Data) = Unit
}
