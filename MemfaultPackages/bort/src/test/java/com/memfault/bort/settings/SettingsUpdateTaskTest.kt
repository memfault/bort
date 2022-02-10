package com.memfault.bort.settings

import androidx.work.Data
import com.memfault.bort.FakeDeviceInfoProvider
import com.memfault.bort.TaskResult
import com.memfault.bort.TaskRunnerWorker
import com.memfault.bort.shared.LogLevel
import com.memfault.bort.tokenbucket.TokenBucket
import com.memfault.bort.tokenbucket.TokenBucketMap
import com.memfault.bort.tokenbucket.TokenBucketStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
    private lateinit var settingsProvider: DynamicSettingsProvider
    private lateinit var storedSettingsPreferenceProvider: StoredSettingsPreferenceProvider
    private lateinit var tokenBucketStore: TokenBucketStore
    val fetchedSettingsUpdateSlot = slot<FetchedSettingsUpdate>()
    val callback: SettingsUpdateCallback = mockk {
        coEvery { onSettingsUpdated(any(), capture(fetchedSettingsUpdateSlot)) } returns Unit
    }

    @BeforeEach
    fun setup() {
        settingsProvider = mockk {
            every { invalidate() } returns Unit
        }
        storedSettingsPreferenceProvider = mockk {
            every { set(any()) } returns Unit
            every { get() } returns SETTINGS_FIXTURE.toSettings()
        }
        worker = mockk {
            every { id } returns UUID.randomUUID()
            every { inputData } returns Data.EMPTY
            every { runAttemptCount } returns 0
        }
        val slot = slot<(map: TokenBucketMap) -> Any>()
        tokenBucketStore = mockk {
            every { edit(capture(slot)) } answers {
                val map = mockk<TokenBucketMap>()
                val bucket = mockk<TokenBucket>()
                every {
                    bucket.take(any(), tag = "settings")
                } returns true
                every {
                    map.upsertBucket(any(), any(), any())
                } returns bucket
                slot.captured(map)
            }
        }
    }

    @Test
    fun testValidResponse() {
        val response = SETTINGS_FIXTURE.toSettings().copy(bortMinLogcatLevel = LogLevel.NONE.level)
        val service = mockk<SettingsUpdateService> {
            coEvery { settings(any(), any(), any()) } answers {
                FetchedSettings.FetchedSettingsContainer(SETTINGS_FIXTURE.toSettings())
            } andThen {
                FetchedSettings.FetchedSettingsContainer(response)
            }
        }

        runBlocking {
            assertEquals(
                TaskResult.SUCCESS,
                SettingsUpdateTask(
                    FakeDeviceInfoProvider(),
                    service,
                    settingsProvider,
                    storedSettingsPreferenceProvider,
                    callback,
                    tokenBucketStore,
                    mockk(relaxed = true),
                ).doWork(worker)
            )
            // The first call returns the same stored fixture and thus set() won't be called
            verify {
                storedSettingsPreferenceProvider.get()
            }
            confirmVerified(storedSettingsPreferenceProvider)

            // The second one will trigger the update
            assertEquals(
                TaskResult.SUCCESS,
                SettingsUpdateTask(
                    FakeDeviceInfoProvider(),
                    service,
                    settingsProvider,
                    storedSettingsPreferenceProvider,
                    callback,
                    tokenBucketStore,
                    mockk(relaxed = true),
                ).doWork(worker)
            )
            // Check that endpoint was called once, and nothing else
            coVerify {
                service.settings(any(), any(), any())
            }
            confirmVerified(service)

            // Check that settings was invalidated after a remote update
            coVerify {
                storedSettingsPreferenceProvider.get()
                storedSettingsPreferenceProvider.set(
                    response
                )
                settingsProvider.invalidate()
                callback.onSettingsUpdated(any(), any())
            }
            confirmVerified(settingsProvider)
            assertEquals(fetchedSettingsUpdateSlot.captured.old, SETTINGS_FIXTURE.toSettings())
            assertEquals(fetchedSettingsUpdateSlot.captured.new, response)
        }
    }

    @Test
    fun testInvalidResponse() {
        val service = mockk<SettingsUpdateService> {
            coEvery { settings(any(), any(), any()) } throws SerializationException("invalid data")
        }

        runBlocking {
            assertEquals(
                TaskResult.SUCCESS,
                SettingsUpdateTask(
                    FakeDeviceInfoProvider(),
                    service,
                    settingsProvider,
                    storedSettingsPreferenceProvider,
                    callback,
                    tokenBucketStore,
                    mockk(relaxed = true),
                ).doWork(worker)
            )

            // Check that endpoint was called once, and nothing else
            coVerify {
                service.settings(any(), any(), any())
            }
            confirmVerified(service)
            confirmVerified(settingsProvider)
        }
    }

    @Test
    fun testHttpException() {
        val service = mockk<SettingsUpdateService> {
            coEvery { settings(any(), any(), any()) } throws
                HttpException(Response.error<Any>(500, "".toResponseBody()))
        }

        runBlocking {
            assertEquals(
                TaskResult.SUCCESS,
                SettingsUpdateTask(
                    FakeDeviceInfoProvider(),
                    service,
                    settingsProvider,
                    storedSettingsPreferenceProvider,
                    callback,
                    tokenBucketStore,
                    mockk(relaxed = true),
                ).doWork(worker)
            )

            // Check that endpoint was called once, and nothing else
            coVerify {
                service.settings(any(), any(), any())
            }
            confirmVerified(service)
            confirmVerified(settingsProvider)
        }
    }
}
