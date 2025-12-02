package com.memfault.bort.dropbox

import android.os.DropBoxManager
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.memfault.bort.settings.DropBoxSettings
import com.memfault.bort.settings.OperationalCrashesExclusions
import com.memfault.bort.tokenbucket.TokenBucketStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class JavaExceptionUploadingEntryProcessorDelegateTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private val javaExceptionTokenBucketStore: TokenBucketStore = mockk(relaxed = true)
    private val wtfTokenBucketStore: TokenBucketStore = mockk(relaxed = true)
    private val wtfTotalTokenBucketStore: TokenBucketStore = mockk(relaxed = true)
    private val operationalCrashesExclusions = OperationalCrashesExclusions { emptyList() }
    private val dropBoxSettings = mockk<DropBoxSettings> {
        every { ignoreCommonWtfs } returns true
        every { ignoredWtfs } returns emptySet()
    }

    private val delegate = JavaExceptionUploadingEntryProcessorDelegate(
        javaExceptionTokenBucketStore = javaExceptionTokenBucketStore,
        wtfTokenBucketStore = wtfTokenBucketStore,
        wtfTotalTokenBucketStore = wtfTotalTokenBucketStore,
        operationalCrashesExclusions = operationalCrashesExclusions,
        dropBoxSettings = dropBoxSettings,
    )

    enum class CommonWtfTestCase(
        val exceptionMessage: String,
        val tag: String,
    ) {
        NO_SERVICE_PUBLISHED(
            "No service published for: appwidget at android.app.SystemServiceRegistry.onServiceNotFound",
            "system_app_wtf",
        ),
        EXTRA_USER_HANDLE_MISSING(
            "EXTRA_USER_HANDLE missing or invalid, value=0",
            "system_server_wtf",
        ),
        FAILED_TO_READ_SYSTEMLOCALE(
            "Failed to read field SystemLocale",
            "data_app_wtf",
        ),
        BUG_NETWORK_AGENT_INFO(
            "BUG: NetworkAgentInfo",
            "system_app_wtf",
        ),
        ATTEMPT_TO_DECREMENT_ALARM_COUNT(
            "Attempt to decrement existing alarm count 0 by 1 for uid 1000",
            "system_server_wtf",
        ),
        REMOVED_TIME_TICK_ALARM(
            "Removed TIME_TICK alarm",
            "system_app_wtf",
        ),
        REQUESTING_NITS_NO_MAPPING(
            "requesting nits when no mapping exists",
            "system_server_wtf",
        ),
        COULD_NOT_OPEN_TRACE_PIPE(
            "Could not open /sys/kernel/tracing/instances/bootreceiver/trace_pipe",
            "system_app_wtf",
        ),
    }

    @Test
    fun `ignores common WTFs`(@TestParameter testCase: CommonWtfTestCase) = runTest {
        val file = tempFolder.newFile()
        file.writeText(
            wtfDropBoxContents(
                exceptionClass = "android.util.Log\$TerribleFailure",
                exceptionMessage = testCase.exceptionMessage,
            ),
        )

        val entry = mockDropBoxManagerEntry(testCase.tag, System.currentTimeMillis())
        val info = delegate.getEntryInfo(entry, file)

        assertThat(info.ignored).isTrue()
    }

    @Test
    fun `does not ignore WTF when ignoreCommonWtfs is false`() = runTest {
        every { dropBoxSettings.ignoreCommonWtfs } returns false
        every { dropBoxSettings.ignoredWtfs } returns emptySet()

        val file = tempFolder.newFile()
        file.writeText(
            wtfDropBoxContents(
                exceptionClass = "android.util.Log\$TerribleFailure",
                exceptionMessage =
                "No service published for: appwidget at android.app.SystemServiceRegistry.onServiceNotFound",
            ),
        )

        val entry = mockDropBoxManagerEntry("system_app_wtf", System.currentTimeMillis())
        val info = delegate.getEntryInfo(entry, file)

        assertThat(info.ignored).isFalse()
    }

    enum class CustomWtfTestCase(
        val exceptionClass: String,
        val exceptionMessage: String,
        val tag: String,
        val ignoredWtfs: Set<String>,
    ) {
        CUSTOM_WTF_PATTERN(
            exceptionClass = "CustomWtfPattern",
            exceptionMessage = "Some custom error",
            tag = "system_app_wtf",
            ignoredWtfs = setOf("CustomWtfPattern", "AnotherPattern.*"),
        ),
        WILDCARD_PATTERN_MATCHING_CLASS(
            exceptionClass = "CustomPatternSpecific",
            exceptionMessage = "Some error message",
            tag = "system_app_wtf",
            ignoredWtfs = setOf("CustomPattern.*"),
        ),
        WILDCARD_PATTERN_MATCHING_MESSAGE(
            exceptionClass = "android.util.Log\$TerribleFailure",
            exceptionMessage = "CustomMessage with specific details",
            tag = "system_app_wtf",
            ignoredWtfs = setOf("CustomMessage.*"),
        ),
    }

    @Test
    fun `ignores custom WTF patterns`(@TestParameter testCase: CustomWtfTestCase) = runTest {
        every { dropBoxSettings.ignoreCommonWtfs } returns true
        every { dropBoxSettings.ignoredWtfs } returns testCase.ignoredWtfs

        val file = tempFolder.newFile()
        file.writeText(
            wtfDropBoxContents(
                exceptionClass = testCase.exceptionClass,
                exceptionMessage = testCase.exceptionMessage,
            ),
        )

        val entry = mockDropBoxManagerEntry(testCase.tag, System.currentTimeMillis())
        val info = delegate.getEntryInfo(entry, file)

        assertThat(info.ignored).isTrue()
    }

    @Test
    fun `does not ignore non-WTF tags`() = runTest {
        val file = tempFolder.newFile()
        file.writeText(
            wtfDropBoxContents(
                exceptionClass = "android.util.Log\$TerribleFailure",
                exceptionMessage =
                "No service published for: appwidget at android.app.SystemServiceRegistry.onServiceNotFound",
            ),
        )

        val entry = mockDropBoxManagerEntry("data_app_crash", System.currentTimeMillis())
        val info = delegate.getEntryInfo(entry, file)

        assertThat(info.ignored).isFalse()
    }

    @Test
    fun `does not ignore WTF that does not match any patterns`() = runTest {
        val file = tempFolder.newFile()
        file.writeText(
            wtfDropBoxContents(
                exceptionClass = "android.util.Log\$TerribleFailure",
                exceptionMessage = "Random",
            ),
        )

        val entry = mockDropBoxManagerEntry("system_app_wtf", System.currentTimeMillis())
        val info = delegate.getEntryInfo(entry, file)

        assertThat(info.ignored).isFalse()
    }

    private fun mockDropBoxManagerEntry(tagValue: String, time: Long): DropBoxManager.Entry = mockk {
        every { tag } returns tagValue
        every { timeMillis } returns time
    }

    private fun wtfDropBoxContents(exceptionClass: String, exceptionMessage: String): String = """
Process: system_server
Subject: TestWtf
Build: Android/aosp_arm64/generic_arm64:8.1.0/OC/root04302340:eng/test-keys

$exceptionClass: $exceptionMessage
    at android.util.Log.wtf(Log.java:309)
    at android.util.Slog.wtf(Slog.java:94)
    at com.android.server.SystemServer.run(SystemServer.java:537)
    at com.android.server.SystemServer.main(SystemServer.java:357)
    at java.lang.reflect.Method.invoke(Native Method)
    at com.android.internal.os.RuntimeInit${'$'}MethodAndArgsCaller.run(RuntimeInit.java:492)
    at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:913)
    """.trimIndent()
}
