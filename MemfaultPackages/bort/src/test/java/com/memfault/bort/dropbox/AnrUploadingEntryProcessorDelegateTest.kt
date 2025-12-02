package com.memfault.bort.dropbox

import android.os.DropBoxManager
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.memfault.bort.settings.OperationalCrashesExclusions
import com.memfault.bort.tokenbucket.TokenBucketStore
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnrUploadingEntryProcessorDelegateTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private val tokenBucketStore: TokenBucketStore = mockk(relaxed = true)
    private val operationalCrashesExclusions = OperationalCrashesExclusions { emptyList() }

    private val delegate = AnrUploadingEntryProcessorDelegate(
        tokenBucketStore = tokenBucketStore,
        operationalCrashesExclusions = operationalCrashesExclusions,
    )

    @Test
    fun entryInfoDefaults() = runTest {
        val file = tempFolder.newFile()
        file.writeText(ANR_CONTENT.trimIndent())

        val entry = DropBoxManager.Entry("data_app_anr", System.currentTimeMillis())

        val info = delegate.getEntryInfo(entry, file)

        assertThat(info.isCrash).isTrue()
        assertThat(info.isTrace).isTrue()
        assertThat(info.ignored).isFalse()
        assertThat(info.crashTag).isNull()
        assertThat(info.packageName).isEqualTo("com.example.app")
    }

    @Test
    fun `handles parse exception gracefully`() = runTest {
        val file = tempFolder.newFile()
        file.writeText("Invalid")

        val entry = DropBoxManager.Entry("data_app_anr", System.currentTimeMillis())

        val info = delegate.getEntryInfo(entry, file)

        assertThat(info.isCrash).isTrue()
        assertThat(info.isTrace).isTrue()
        assertThat(info.ignored).isFalse()
        assertThat(info.crashTag).isNull()
        assertThat(info.packageName).isNull()
    }

    companion object {
        private const val ANR_CONTENT = """
Process: com.example.app
PID: 12345
Flags: 0x28e83e46
Package: com.example.app v1.0.0 (1.0.0)
Foreground: Yes
Build: Android/aosp_arm64/generic_arm64:8.1.0/OC/root04302340:eng/test-keys

ANR in com.example.app (com.example.app/com.example.app.MainActivity)
PID: 12345
Reason: Input dispatching timed out
Load: 0.0 / 0.0 / 0.0
CPU usage from 0ms to 1000ms later:
  0% 12345/com.example.app: 0% user + 0% kernel
  0% TOTAL: 0% user + 0% kernel
"""
    }
}
