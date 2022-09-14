package com.memfault.bort.settings

import androidx.work.Data
import com.memfault.bort.FakeDeviceInfoProvider
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.tokenbucket.TokenBucketMap
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response

class SettingsUpdateTaskTest {
    private lateinit var worker: TaskRunnerWorker
    private lateinit var settingsUpdateHandler: SettingsUpdateHandler

    @BeforeEach
    fun setup() {
        worker = mockk {
            every { id } returns UUID.randomUUID()
            every { inputData } returns Data.EMPTY
            every { runAttemptCount } returns 0
        }
        val slot = slot<(map: TokenBucketMap) -> Any>()
        settingsUpdateHandler = mockk(relaxed = true)
    }

    @Test
    fun testValidResponse() {
        val response = SETTINGS_FIXTURE.toSettings().copy(bortMinLogcatLevel = LogLevel.NONE.level)
        val service = mockk<SettingsUpdateService> {
            coEvery { settings(any(), any(), any()) } answers {
                FetchedSettings.FetchedSettingsContainer(response)
            }
        }
        val deviceConfigService = mockk<DeviceConfigUpdateService>()
        val useDeviceConfig = UseDeviceConfig { false }
        val samplingConfig = mockk<CurrentSamplingConfig>()
        runBlocking {
            assertEquals(
                TaskResult.SUCCESS,
                SettingsUpdateTask(
                    deviceInfoProvider = FakeDeviceInfoProvider(),
                    settingsUpdateService = service,
                    metrics = mockk(relaxed = true),
                    settingsUpdateHandler = settingsUpdateHandler,
                    useDeviceConfig = useDeviceConfig,
                    deviceConfigUpdateService = deviceConfigService,
                    samplingConfig = samplingConfig,
                ).doWork(worker)
            )
        }
        // Check that endpoint was called once, and nothing else
        coVerify {
            service.settings(any(), any(), any())
        }
        confirmVerified(service)
        coVerify { settingsUpdateHandler.handleSettingsUpdate(response) }
    }

    @Test
    fun testInvalidResponse() {
        val service = mockk<SettingsUpdateService> {
            coEvery { settings(any(), any(), any()) } throws SerializationException("invalid data")
        }
        val deviceConfigService = mockk<DeviceConfigUpdateService>()
        val useDeviceConfig = UseDeviceConfig { false }
        val samplingConfig = mockk<CurrentSamplingConfig>()
        runBlocking {
            assertEquals(
                TaskResult.SUCCESS,
                SettingsUpdateTask(
                    deviceInfoProvider = FakeDeviceInfoProvider(),
                    settingsUpdateService = service,
                    metrics = mockk(relaxed = true),
                    settingsUpdateHandler = settingsUpdateHandler,
                    useDeviceConfig = useDeviceConfig,
                    deviceConfigUpdateService = deviceConfigService,
                    samplingConfig = samplingConfig,
                ).doWork(worker)
            )

            // Check that endpoint was called once, and nothing else
            coVerify {
                service.settings(any(), any(), any())
            }
            confirmVerified(service)
            confirmVerified(settingsUpdateHandler)
        }
    }

    @Test
    fun testHttpException() {
        val service = mockk<SettingsUpdateService> {
            coEvery { settings(any(), any(), any()) } throws
                HttpException(Response.error<Any>(500, "".toResponseBody()))
        }
        val deviceConfigService = mockk<DeviceConfigUpdateService>()
        val useDeviceConfig = UseDeviceConfig { false }
        val samplingConfig = mockk<CurrentSamplingConfig>()
        runBlocking {
            assertEquals(
                TaskResult.SUCCESS,
                SettingsUpdateTask(
                    deviceInfoProvider = FakeDeviceInfoProvider(),
                    settingsUpdateService = service,
                    metrics = mockk(relaxed = true),
                    settingsUpdateHandler = settingsUpdateHandler,
                    useDeviceConfig = useDeviceConfig,
                    deviceConfigUpdateService = deviceConfigService,
                    samplingConfig = samplingConfig,
                ).doWork(worker)
            )

            // Check that endpoint was called once, and nothing else
            coVerify {
                service.settings(any(), any(), any())
            }
            confirmVerified(service)
            confirmVerified(settingsUpdateHandler)
        }
    }
}
