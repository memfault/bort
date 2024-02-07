package com.memfault.bort.settings

import androidx.work.Data
import com.memfault.bort.DeviceInfo
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.FakeDeviceInfoProvider
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.clientserver.CachedClientServerMode
import com.memfault.bort.clientserver.ClientDeviceInfoPreferenceProvider
import com.memfault.bort.clientserver.LinkedDeviceFileSender
import com.memfault.bort.settings.DeviceConfigUpdateService.DeviceConfigArgs
import com.memfault.bort.shared.CLIENT_SERVER_DEVICE_CONFIG_DROPBOX_TAG
import com.memfault.bort.shared.ClientServerMode
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.test.util.TestTemporaryFileFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
import java.util.UUID

class SettingsUpdateTaskTest {
    private lateinit var worker: TaskRunnerWorker
    private lateinit var settingsUpdateHandler: SettingsUpdateHandler
    private var clientServerMode = ClientServerMode.DISABLED
    private val cachedClientServerMode = object : CachedClientServerMode {
        override suspend fun get(): ClientServerMode = clientServerMode
    }
    private val linkedDeviceFileSender: LinkedDeviceFileSender = mockk(relaxed = true)
    private val temporaryFileFactory = TestTemporaryFileFactory
    private var clientDeviceInfo: DeviceConfigUpdateService.DeviceInfo? = null
    private val clientDeviceInfoPreferenceProvider = object : ClientDeviceInfoPreferenceProvider {
        override fun set(config: DeviceConfigUpdateService.DeviceInfo) {}
        override fun get(): DeviceConfigUpdateService.DeviceInfo? = clientDeviceInfo
    }

    @BeforeEach
    fun setup() {
        worker = mockk {
            every { id } returns UUID.randomUUID()
            every { inputData } returns Data.EMPTY
            every { runAttemptCount } returns 0
        }
        settingsUpdateHandler = mockk(relaxed = true)
    }

    @Test
    fun testValidResponse_settings() = runTest {
        val response = SETTINGS_FIXTURE.toSettings().copy(bortMinLogcatLevel = LogLevel.NONE.level)
        val settingsService = mockk<SettingsUpdateService> {
            coEvery { settings(any(), any(), any()) } answers {
                FetchedSettings.FetchedSettingsContainer(response)
            }
        }
        val deviceConfigService = mockk<DeviceConfigUpdateService>()
        val samplingConfig = mockk<CurrentSamplingConfig>()
        assertEquals(
            TaskResult.SUCCESS,
            SettingsUpdateTask(
                deviceInfoProvider = FakeDeviceInfoProvider(),
                settingsUpdateService = settingsService,
                metrics = mockk(relaxed = true),
                settingsUpdateHandler = settingsUpdateHandler,
                useDeviceConfig = { false },
                deviceConfigUpdateService = deviceConfigService,
                samplingConfig = samplingConfig,
                cachedClientServerMode = cachedClientServerMode,
                linkedDeviceFileSender = linkedDeviceFileSender,
                temporaryFileFactory = temporaryFileFactory,
                clientDeviceInfoPreferenceProvider = clientDeviceInfoPreferenceProvider,
            ).doWork(worker),
        )
        // Check that endpoint was called once, and nothing else
        coVerify(exactly = 1) { settingsService.settings(any(), any(), any()) }
        coVerify(exactly = 0) { deviceConfigService.deviceConfig(any()) }
        confirmVerified(settingsService)
        coVerify { settingsUpdateHandler.handleSettingsUpdate(response) }
        coVerify(exactly = 0) { linkedDeviceFileSender.sendFileToLinkedDevice(any(), any()) }
    }

    @Test
    fun testInvalidResponse_settings() = runTest {
        val settingsService = mockk<SettingsUpdateService> {
            coEvery { settings(any(), any(), any()) } throws SerializationException("invalid data")
        }
        val deviceConfigService = mockk<DeviceConfigUpdateService>()
        val samplingConfig = mockk<CurrentSamplingConfig>()
        assertEquals(
            TaskResult.SUCCESS,
            SettingsUpdateTask(
                deviceInfoProvider = FakeDeviceInfoProvider(),
                settingsUpdateService = settingsService,
                metrics = mockk(relaxed = true),
                settingsUpdateHandler = settingsUpdateHandler,
                useDeviceConfig = { false },
                deviceConfigUpdateService = deviceConfigService,
                samplingConfig = samplingConfig,
                cachedClientServerMode = cachedClientServerMode,
                linkedDeviceFileSender = linkedDeviceFileSender,
                temporaryFileFactory = temporaryFileFactory,
                clientDeviceInfoPreferenceProvider = clientDeviceInfoPreferenceProvider,
            ).doWork(worker),
        )

        // Check that endpoint was called once, and nothing else
        coVerify {
            settingsService.settings(any(), any(), any())
        }
        confirmVerified(settingsService)
        confirmVerified(settingsUpdateHandler)
    }

    @Test
    fun testHttpException_settings() = runTest {
        val settingsService = mockk<SettingsUpdateService> {
            coEvery { settings(any(), any(), any()) } throws
                HttpException(Response.error<Any>(500, "".toResponseBody()))
        }
        val deviceConfigService = mockk<DeviceConfigUpdateService>()
        val samplingConfig = mockk<CurrentSamplingConfig>()
        assertEquals(
            TaskResult.SUCCESS,
            SettingsUpdateTask(
                deviceInfoProvider = FakeDeviceInfoProvider(),
                settingsUpdateService = settingsService,
                metrics = mockk(relaxed = true),
                settingsUpdateHandler = settingsUpdateHandler,
                useDeviceConfig = { false },
                deviceConfigUpdateService = deviceConfigService,
                samplingConfig = samplingConfig,
                cachedClientServerMode = cachedClientServerMode,
                linkedDeviceFileSender = linkedDeviceFileSender,
                temporaryFileFactory = temporaryFileFactory,
                clientDeviceInfoPreferenceProvider = clientDeviceInfoPreferenceProvider,
            ).doWork(worker),
        )

        // Check that endpoint was called once, and nothing else
        coVerify {
            settingsService.settings(any(), any(), any())
        }
        confirmVerified(settingsService)
        confirmVerified(settingsUpdateHandler)
    }

    @Test
    fun testValidResponse_deviceConfig() = runTest {
        val settingsService = mockk<SettingsUpdateService>()
        val settings = SETTINGS_FIXTURE.toSettings().copy(bugReportMaxUploadAttempts = 73)
        val deviceConfigResponse = DecodedDeviceConfig(
            revision = 1,
            completedRevision = 1,
            memfault = FetchedDeviceConfigContainer.Memfault(
                bort = FetchedDeviceConfigContainer.Bort(
                    sdkSettings = settings,
                ),
                sampling = FetchedDeviceConfigContainer.Sampling(),
            ),
            others = JsonObject(emptyMap()),
        )
        val deviceConfigService = mockk<DeviceConfigUpdateService> {
            coEvery { deviceConfig(any()) } answers { deviceConfigResponse }
        }
        val samplingConfig = mockk<CurrentSamplingConfig>(relaxed = true)

        assertEquals(
            TaskResult.SUCCESS,
            SettingsUpdateTask(
                deviceInfoProvider = FakeDeviceInfoProvider(),
                settingsUpdateService = settingsService,
                metrics = mockk(relaxed = true),
                settingsUpdateHandler = settingsUpdateHandler,
                useDeviceConfig = { true },
                deviceConfigUpdateService = deviceConfigService,
                samplingConfig = samplingConfig,
                cachedClientServerMode = cachedClientServerMode,
                linkedDeviceFileSender = linkedDeviceFileSender,
                temporaryFileFactory = temporaryFileFactory,
                clientDeviceInfoPreferenceProvider = clientDeviceInfoPreferenceProvider,
            ).doWork(worker),
        )
        coVerify(exactly = 0) { settingsService.settings(any(), any(), any()) }
        coVerify(exactly = 1) { deviceConfigService.deviceConfig(any()) }
        confirmVerified(settingsService)
        coVerify { settingsUpdateHandler.handleSettingsUpdate(settings) }
        coVerify {
            samplingConfig.update(
                SamplingConfig(
                    revision = 1,
                    debuggingResolution = SamplingConfig.DEFAULT_DEBUGGING,
                    loggingResolution = SamplingConfig.DEFAULT_LOGGING,
                    monitoringResolution = SamplingConfig.DEFAULT_MONITORING,
                ),
                completedRevision = 1,
            )
        }
        coVerify(exactly = 0) { linkedDeviceFileSender.sendFileToLinkedDevice(any(), any()) }
    }

    @Test
    fun testValidResponse_deviceConfig_clientServer() = runTest {
        val settingsService = mockk<SettingsUpdateService>()
        clientServerMode = ClientServerMode.SERVER

        val settings_server = SETTINGS_FIXTURE.toSettings().copy(bugReportMaxUploadAttempts = 73)
        val deviceConfigResponse_server = DecodedDeviceConfig(
            revision = 2,
            completedRevision = 2,
            memfault = FetchedDeviceConfigContainer.Memfault(
                bort = FetchedDeviceConfigContainer.Bort(
                    sdkSettings = settings_server,
                ),
                sampling = FetchedDeviceConfigContainer.Sampling(
                    debuggingResolution = "low",
                    loggingResolution = "low",
                    monitoringResolution = "low",
                ),
            ),
            others = JsonObject(emptyMap()),
        )
        val deviceInfo_server = DeviceInfo(
            deviceSerial = "server_serial",
            hardwareVersion = "server_hwver",
            softwareVersion = "server_swver",
        )

        val settings_client = SETTINGS_FIXTURE.toSettings().copy(structuredLogNumEventsBeforeDump = 12345)
        val deviceConfigResponse_client = DecodedDeviceConfig(
            revision = 3,
            completedRevision = 3,
            memfault = FetchedDeviceConfigContainer.Memfault(
                bort = FetchedDeviceConfigContainer.Bort(
                    sdkSettings = settings_client,
                ),
                sampling = FetchedDeviceConfigContainer.Sampling(
                    debuggingResolution = "high",
                    loggingResolution = "high",
                    monitoringResolution = "high",
                ),
            ),
            others = JsonObject(emptyMap()),
        )
        val deviceInfo_client = DeviceInfo(
            deviceSerial = "client_serial",
            hardwareVersion = "client_hwver",
            softwareVersion = "client_swver",
        )

        val deviceInfoProvider = object : DeviceInfoProvider {
            override suspend fun getDeviceInfo(): DeviceInfo = deviceInfo_server
        }
        val deviceConfigArgs_server = DeviceConfigArgs(deviceInfo_server.asDeviceConfigInfo())
        val deviceConfigArgs_client = DeviceConfigArgs(deviceInfo_client.asDeviceConfigInfo())
        clientDeviceInfo = deviceConfigArgs_client.device
        val deviceConfigService = mockk<DeviceConfigUpdateService> {
            coEvery { deviceConfig(deviceConfigArgs_server) } answers { deviceConfigResponse_server }
            coEvery { deviceConfig(deviceConfigArgs_client) } answers { deviceConfigResponse_client }
        }
        val samplingConfig = mockk<CurrentSamplingConfig>(relaxed = true)
        assertEquals(
            TaskResult.SUCCESS,
            SettingsUpdateTask(
                deviceInfoProvider = deviceInfoProvider,
                settingsUpdateService = settingsService,
                metrics = mockk(relaxed = true),
                settingsUpdateHandler = settingsUpdateHandler,
                useDeviceConfig = { true },
                deviceConfigUpdateService = deviceConfigService,
                samplingConfig = samplingConfig,
                cachedClientServerMode = cachedClientServerMode,
                linkedDeviceFileSender = linkedDeviceFileSender,
                temporaryFileFactory = temporaryFileFactory,
                clientDeviceInfoPreferenceProvider = clientDeviceInfoPreferenceProvider,
            ).doWork(worker),
        )
        coVerify(exactly = 0) { settingsService.settings(any(), any(), any()) }
        coVerify(exactly = 1) {
            deviceConfigService.deviceConfig(deviceConfigArgs_server)
            deviceConfigService.deviceConfig(deviceConfigArgs_client)
        }
        confirmVerified(settingsService)
        coVerify { settingsUpdateHandler.handleSettingsUpdate(settings_server) }
        coVerify {
            samplingConfig.update(
                SamplingConfig(
                    revision = 2,
                    debuggingResolution = Resolution.LOW,
                    loggingResolution = Resolution.LOW,
                    monitoringResolution = Resolution.LOW,
                ),
                completedRevision = 2,
            )
        }
        coVerify(exactly = 1) {
            linkedDeviceFileSender.sendFileToLinkedDevice(
                any(),
                CLIENT_SERVER_DEVICE_CONFIG_DROPBOX_TAG,
            )
        }
    }
}
