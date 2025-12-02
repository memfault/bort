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
class KmsgUploadingEntryProcessorDelegateTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private val tokenBucketStore: TokenBucketStore = mockk(relaxed = true)
    private val operationalCrashesExclusions = object : OperationalCrashesExclusions {
        var exclusions = mutableListOf<String>()
        override fun invoke(): List<String> = exclusions
    }

    private val delegate = KmsgUploadingEntryProcessorDelegate(
        tokenBucketStore = tokenBucketStore,
        operationalCrashesExclusions = operationalCrashesExclusions,
    )

    @Test
    fun `normal reboot is not crash`() = runTest {
        val file = tempFolder.newFile()
        file.writeText(NORMAL_KMSG.trimIndent())

        val entry = DropBoxManager.Entry("SYSTEM_RECOVERY_KMSG", System.currentTimeMillis())

        val info = delegate.getEntryInfo(entry, file)

        assertThat(info.isCrash).isFalse()
    }

    @Test
    fun `kernel panic is crash`() = runTest {
        val file = tempFolder.newFile()
        file.writeText(KERNEL_PANIC_KMSG.trimIndent())

        val entry = DropBoxManager.Entry("SYSTEM_LAST_KMSG", System.currentTimeMillis())

        val info = delegate.getEntryInfo(entry, file)

        assertThat(info.isCrash).isTrue()
    }

    @Test
    fun `exclusions ignores random strings`() = runTest {
        operationalCrashesExclusions.exclusions += "kernel_panic"

        val file = tempFolder.newFile()
        file.writeText(KERNEL_PANIC_KMSG.trimIndent())

        val entry = DropBoxManager.Entry("SYSTEM_LAST_KMSG", System.currentTimeMillis())

        val info = delegate.getEntryInfo(entry, file)

        assertThat(info.isCrash).isTrue()
    }

    @Test
    fun `exclusions works`() = runTest {
        operationalCrashesExclusions.exclusions += "SYSTEM_LAST_KMSG"

        val file = tempFolder.newFile()
        file.writeText(KERNEL_PANIC_KMSG.trimIndent())

        val entry = DropBoxManager.Entry("SYSTEM_LAST_KMSG", System.currentTimeMillis())

        assertThat(entry.tag).isEqualTo("SYSTEM_LAST_KMSG")

        val info = delegate.getEntryInfo(entry, file)

        assertThat(info.isCrash).isFalse()
    }

    @Test
    fun entryInfoDefaults() = runTest {
        val file = tempFolder.newFile()
        file.writeText(KERNEL_PANIC_KMSG.trimIndent())

        val entry = DropBoxManager.Entry("SYSTEM_LAST_KMSG", System.currentTimeMillis())

        val info = delegate.getEntryInfo(entry, file)

        assertThat(info.isCrash).isTrue()
        assertThat(info.isTrace).isTrue()
        assertThat(info.ignored).isFalse()
        assertThat(info.crashTag).isEqualTo("panic")
        assertThat(info.packageName).isNull()
    }

    companion object {
        private const val NORMAL_KMSG = """
            isPrevious: true
            Build: OnePlus/OnePlus6/OnePlus6:8.1.0/OPM1.171019.011/06140300:user/release-keys
            Hardware: kvim3
            Revision: 0
            Bootloader: unknown
            Radio: 
            Kernel: Linux version 4.9.113 (leo@LeoHomePC) (gcc version 6.3.1 20170109 (Linaro GCC 6.3-2017.02) ) #3 SMP PREEMPT Wed Mar 24 17:23:14 CDT 2021

            1195.776913@0] xhci-hcd xhci-hcd.0.auto: WARN: buffer overrun event on endpoint

            Boot info:
            Last boot reason: reboot
        """

        private const val KERNEL_PANIC_KMSG = """
            isPrevious: true
            Build: OnePlus/OnePlus6/OnePlus6:8.1.0/OPM1.171019.011/06140300:user/release-keys
            Hardware: kvim3
            Revision: 0
            Bootloader: unknown
            Radio: 
            Kernel: Linux version 4.9.113 (leo@LeoHomePC) (gcc version 6.3.1 20170109 (Linaro GCC 6.3-2017.02) ) #3 SMP PREEMPT Wed Mar 24 17:23:14 CDT 2021

            1195.776913@0] xhci-hcd xhci-hcd.0.auto: WARN: buffer overrun event on endpoint

            Boot info:
            Last boot reason: kernel_panic,BUG
        """
    }
}
