package com.memfault.bort.android

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BroadcastReceiversTest {

    private lateinit var application: Application
    private lateinit var receiver: CapturingSlot<BroadcastReceiver>
    private lateinit var intentFilter: CapturingSlot<IntentFilter>

    @BeforeEach
    fun setUp() {
        receiver = slot()
        intentFilter = slot()
        application = mockk {
            coEvery { registerReceiver(capture(receiver), capture(intentFilter)) } returns Intent()
            coEvery { unregisterReceiver(any<BroadcastReceiver>()) } returns Unit
        }
    }

    @Test
    fun sendIntent() = runTest {
        val first = async(UnconfinedTestDispatcher(testScheduler)) {
            application.registerForIntents("TEST").first()
        }

        val intent: Intent = mockk {
            every { action } returns "TEST"
        }
        receiver.captured.onReceive(mockk(), intent)

        Assertions.assertEquals(first.await(), intent)
    }

    @Test
    fun unregisters() = runTest {
        val first = async(UnconfinedTestDispatcher(testScheduler)) {
            application.registerForIntents("com.memfault.bort.TEST").firstOrNull()
        }

        first.cancel()

        coVerify { application.unregisterReceiver(receiver.captured) }
    }
}
