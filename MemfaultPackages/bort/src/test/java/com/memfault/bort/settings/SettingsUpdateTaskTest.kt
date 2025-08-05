package com.memfault.bort.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.DeviceInfo
import com.memfault.bort.DeviceInfoProvider
import com.memfault.bort.FakeDeviceInfoProvider
import com.memfault.bort.clientserver.CachedClientServerMode
import com.memfault.bort.clientserver.ClientDeviceInfoPreferenceProvider
import com.memfault.bort.clientserver.LinkedDeviceFileSender
import com.memfault.bort.deviceconfig.DefaultMemfaultFetchDeviceConfigUseCase
import com.memfault.bort.deviceconfig.RealClientServerUpdateClientDeviceConfigUseCase
import com.memfault.bort.settings.DeviceConfigUpdateService.DeviceConfigArgs
import com.memfault.bort.shared.CLIENT_SERVER_DEVICE_CONFIG_DROPBOX_TAG
import com.memfault.bort.shared.ClientServerMode
import com.memfault.bort.test.util.TestTemporaryFileFactory
import com.memfault.bort.uploader.mockTaskRunnerWorker
import com.memfault.bort.uploader.mockWorkerFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException

@RunWith(RobolectricTestRunner::class)
class SettingsUpdateTaskTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var settingsUpdateHandler: SettingsUpdateHandler
    private var clientServerMode = ClientServerMode.DISABLED
    private val cachedClientServerMode = CachedClientServerMode { clientServerMode }
    private val linkedDeviceFileSender: LinkedDeviceFileSender = mockk(relaxed = true)
    private val temporaryFileFactory = TestTemporaryFileFactory
    private var clientDeviceInfo: DeviceConfigUpdateService.DeviceInfo? = null
    private val clientDeviceInfoPreferenceProvider = object : ClientDeviceInfoPreferenceProvider {
        override fun set(config: DeviceConfigUpdateService.DeviceInfo) {}
        override fun get(): DeviceConfigUpdateService.DeviceInfo? = clientDeviceInfo
    }

    @Before
    fun setup() {
        settingsUpdateHandler = mockk(relaxed = true)
    }

    @Test
    fun testInvalidResponse() = runTest {
        val deviceConfigService = mockk<DeviceConfigUpdateService> {
            coEvery { deviceConfig(any()) } throws SerializationException("invalid data")
        }
        val task = SettingsUpdateTask(
            fetchDeviceConfigUseCase = DefaultMemfaultFetchDeviceConfigUseCase(
                deviceInfoProvider = FakeDeviceInfoProvider(),
                deviceConfigUpdateService = deviceConfigService,
            ),
            settingsUpdateHandler = settingsUpdateHandler,
            clientServerUpdateClientDeviceConfigUseCase = RealClientServerUpdateClientDeviceConfigUseCase(
                deviceConfigUpdateService = deviceConfigService,
                cachedClientServerMode = cachedClientServerMode,
                linkedDeviceFileSender = linkedDeviceFileSender,
                temporaryFileFactory = temporaryFileFactory,
                clientDeviceInfoPreferenceProvider = clientDeviceInfoPreferenceProvider,
            ),
        )
        val worker = mockTaskRunnerWorker<SettingsUpdateTask>(
            context,
            mockWorkerFactory(settingsUpdate = task),
            Data.EMPTY,
        )
        assertThat(worker.doWork()).isEqualTo(Result.success())

        // Check that endpoint was called once, and nothing else
        coVerify {
            deviceConfigService.deviceConfig(any())
        }
        confirmVerified(deviceConfigService)
        confirmVerified(settingsUpdateHandler)
    }

    @Test
    fun testHttpException() = runTest {
        val deviceConfigService = mockk<DeviceConfigUpdateService> {
            coEvery { deviceConfig(any()) } throws
                HttpException(Response.error<Any>(500, "".toResponseBody()))
        }
        val task = SettingsUpdateTask(
            fetchDeviceConfigUseCase = DefaultMemfaultFetchDeviceConfigUseCase(
                deviceInfoProvider = FakeDeviceInfoProvider(),
                deviceConfigUpdateService = deviceConfigService,
            ),
            settingsUpdateHandler = settingsUpdateHandler,
            clientServerUpdateClientDeviceConfigUseCase = RealClientServerUpdateClientDeviceConfigUseCase(
                deviceConfigUpdateService = deviceConfigService,
                cachedClientServerMode = cachedClientServerMode,
                linkedDeviceFileSender = linkedDeviceFileSender,
                temporaryFileFactory = temporaryFileFactory,
                clientDeviceInfoPreferenceProvider = clientDeviceInfoPreferenceProvider,
            ),
        )
        val worker = mockTaskRunnerWorker<SettingsUpdateTask>(
            context,
            mockWorkerFactory(settingsUpdate = task),
            Data.EMPTY,
        )
        assertThat(worker.doWork()).isEqualTo(Result.success())

        // Check that endpoint was called once, and nothing else
        coVerify {
            deviceConfigService.deviceConfig(any())
        }
        confirmVerified(deviceConfigService)
        confirmVerified(settingsUpdateHandler)
    }

    @Test
    fun testSocketTimeoutException() = runTest {
        val deviceConfigService = mockk<DeviceConfigUpdateService> {
            coEvery { deviceConfig(any()) } throws
                SocketTimeoutException("failed to connect to /192.168.0.226")
        }
        val task = SettingsUpdateTask(
            fetchDeviceConfigUseCase = DefaultMemfaultFetchDeviceConfigUseCase(
                deviceInfoProvider = FakeDeviceInfoProvider(),
                deviceConfigUpdateService = deviceConfigService,
            ),
            settingsUpdateHandler = settingsUpdateHandler,
            clientServerUpdateClientDeviceConfigUseCase = RealClientServerUpdateClientDeviceConfigUseCase(
                deviceConfigUpdateService = deviceConfigService,
                cachedClientServerMode = cachedClientServerMode,
                linkedDeviceFileSender = linkedDeviceFileSender,
                temporaryFileFactory = temporaryFileFactory,
                clientDeviceInfoPreferenceProvider = clientDeviceInfoPreferenceProvider,
            ),
        )
        val worker = mockTaskRunnerWorker<SettingsUpdateTask>(
            context,
            mockWorkerFactory(settingsUpdate = task),
            Data.EMPTY,
        )
        assertThat(worker.doWork()).isEqualTo(Result.success())

        // Check that endpoint was called once, and nothing else
        coVerify {
            deviceConfigService.deviceConfig(any())
        }
        confirmVerified(deviceConfigService)
        confirmVerified(settingsUpdateHandler)
    }

    @Test
    fun testValidResponse() = runTest {
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
        val task = SettingsUpdateTask(
            fetchDeviceConfigUseCase = DefaultMemfaultFetchDeviceConfigUseCase(
                deviceInfoProvider = FakeDeviceInfoProvider(),
                deviceConfigUpdateService = deviceConfigService,
            ),
            settingsUpdateHandler = settingsUpdateHandler,
            clientServerUpdateClientDeviceConfigUseCase = RealClientServerUpdateClientDeviceConfigUseCase(
                deviceConfigUpdateService = deviceConfigService,
                cachedClientServerMode = cachedClientServerMode,
                linkedDeviceFileSender = linkedDeviceFileSender,
                temporaryFileFactory = temporaryFileFactory,
                clientDeviceInfoPreferenceProvider = clientDeviceInfoPreferenceProvider,
            ),
        )
        val worker = mockTaskRunnerWorker<SettingsUpdateTask>(
            context,
            mockWorkerFactory(settingsUpdate = task),
            Data.EMPTY,
        )
        assertThat(worker.doWork()).isEqualTo(Result.success())

        coVerify(exactly = 1) { deviceConfigService.deviceConfig(any()) }
        coVerify { settingsUpdateHandler.handleDeviceConfig(deviceConfigResponse) }
        coVerify(exactly = 0) { linkedDeviceFileSender.sendFileToLinkedDevice(any(), any()) }
    }

    @Test
    fun testValidResponse_clientServer() = runTest {
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
        val task = SettingsUpdateTask(
            fetchDeviceConfigUseCase = DefaultMemfaultFetchDeviceConfigUseCase(
                deviceInfoProvider = deviceInfoProvider,
                deviceConfigUpdateService = deviceConfigService,
            ),
            settingsUpdateHandler = settingsUpdateHandler,
            clientServerUpdateClientDeviceConfigUseCase = RealClientServerUpdateClientDeviceConfigUseCase(
                deviceConfigUpdateService = deviceConfigService,
                cachedClientServerMode = cachedClientServerMode,
                linkedDeviceFileSender = linkedDeviceFileSender,
                temporaryFileFactory = temporaryFileFactory,
                clientDeviceInfoPreferenceProvider = clientDeviceInfoPreferenceProvider,
            ),
        )
        val worker = mockTaskRunnerWorker<SettingsUpdateTask>(
            context,
            mockWorkerFactory(settingsUpdate = task),
            Data.EMPTY,
        )
        assertThat(worker.doWork()).isEqualTo(Result.success())

        coVerify(exactly = 1) {
            deviceConfigService.deviceConfig(deviceConfigArgs_server)
            deviceConfigService.deviceConfig(deviceConfigArgs_client)
        }
        coVerify { settingsUpdateHandler.handleDeviceConfig(deviceConfigResponse_server) }
        coVerify(exactly = 1) {
            linkedDeviceFileSender.sendFileToLinkedDevice(
                any(),
                CLIENT_SERVER_DEVICE_CONFIG_DROPBOX_TAG,
            )
        }
    }
}
