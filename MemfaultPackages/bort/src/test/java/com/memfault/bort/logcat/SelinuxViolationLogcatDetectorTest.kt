package com.memfault.bort.logcat

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.memfault.bort.parsers.Package
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.settings.RateLimitingSettings
import com.memfault.bort.settings.SelinuxViolationSettings
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.time.boxed
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueUpload
import com.memfault.bort.uploader.HandleEventOfInterest
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.time.Duration.Companion.days

@RunWith(TestParameterInjector::class)
class SelinuxViolationLogcatDetectorTest {
    private val combinedTimeProvider: CombinedTimeProvider = mockk()
    private val enqueueUpload: EnqueueUpload = mockk()
    private val handleEventOfInterest: HandleEventOfInterest = mockk()
    private val settingsProvider: SettingsProvider = mockk {
        coEvery { selinuxViolationSettings } answers {
            object : SelinuxViolationSettings {
                override val dataSourceEnabled: Boolean = true
                override val rateLimitingSettings: RateLimitingSettings = RateLimitingSettings(
                    defaultCapacity = 25,
                    defaultPeriod = 1.days.boxed(),
                    maxBuckets = 1,
                )
            }
        }
    }
    private val tokenBucketStore: TokenBucketStore = mockk()

    private val detector = SelinuxViolationLogcatDetector(
        combinedTimeProvider = combinedTimeProvider,
        enqueueUpload = enqueueUpload,
        handleEventOfInterest = handleEventOfInterest,
        settingsProvider = settingsProvider,
        tokenBucketStore = tokenBucketStore,
    )

    @Test
    fun test_selinux_parser(@TestParameter testCase: TestCase) {
        val selinuxViolation = detector.parse(
            message = testCase.logcatLineAfterTag,
            uid = testCase.uid,
            timestamp = Instant.now(),
            packageManagerReport = PackageManagerReport(testCase.packages),
        )

        assertThat(selinuxViolation).isNotNull()
        assertThat(testCase.expectedAction).isEqualTo(selinuxViolation?.action)
        assertThat(testCase.expectedSourceContext).isEqualTo(selinuxViolation?.sourceContext)
        assertThat(testCase.expectedTargetContext).isEqualTo(selinuxViolation?.targetContext)
        assertThat(testCase.expectedTargetClass).isEqualTo(selinuxViolation?.targetClass)
        assertThat(testCase.expectedApp).isEqualTo(selinuxViolation?.app)
        assertThat(testCase.expectedComm).isEqualTo(selinuxViolation?.comm)
        assertThat(testCase.expectedName).isEqualTo(selinuxViolation?.name)
        assertThat(testCase.expectedPackageName).isEqualTo(selinuxViolation?.packageName)
        assertThat(testCase.expectedPackageVersionName).isEqualTo(selinuxViolation?.packageVersionName)
        assertThat(testCase.expectedPackageVersionCode).isEqualTo(selinuxViolation?.packageVersionCode)
    }

    enum class TestCase(
        val logcatLineAfterTag: String,
        val uid: Int = 1,
        val packages: List<Package> = emptyList(),
        val expectedAction: String? = null,
        val expectedSourceContext: String? = null,
        val expectedTargetContext: String? = null,
        val expectedTargetClass: String? = null,
        val expectedApp: String? = null,
        val expectedComm: String? = null,
        val expectedName: String? = null,
        val expectedMarMetadataTitle: String? = null,
        val expectedPackageName: String? = null,
        val expectedPackageVersionName: String? = null,
        val expectedPackageVersionCode: Long? = null,
    ) {
        /* ...values = */
        TestCase1(
            logcatLineAfterTag = """type=1400 audit(0.0:93): avc: denied { dac_override } """ +
                """for capability=1 scontext=u:r:logpersist:s0 tcontext=u:r:logpersist:s0 """ +
                """tclass=capability permissive=0 b/132911257""",
            expectedAction = "dac_override",
            expectedSourceContext = "logpersist",
            expectedTargetContext = "logpersist",
            expectedTargetClass = "capability",
            expectedApp = null,
            expectedComm = null,
            expectedName = null,
            expectedMarMetadataTitle = "Denied dac_override for logpersist (null)",
        ),
        TestCase2(
            logcatLineAfterTag = """type=1400 audit(0.0:18867): avc: denied { associate } for """ +
                """name="globalAlert" scontext=u:object_r:proc_net:s0 """ +
                """tcontext=u:object_r:proc:s0 tclass=filesystem permissive=1""",
            expectedAction = "associate",
            expectedSourceContext = "proc_net",
            expectedTargetContext = "proc",
            expectedTargetClass = "filesystem",
            expectedApp = null,
            expectedComm = null,
            expectedName = "globalAlert",
            expectedMarMetadataTitle = "Denied associate for proc_net (globalAlert)",
        ),
        TestCase3(
            logcatLineAfterTag = """type=1400 audit(0.0:17449): avc: denied { open } for """ +
                """comm="m.webview_shell" path="/proc/vmstat" dev="proc" ino=4026532167 """ +
                """scontext=u:r:untrusted_app_27:s0:c118,c256,c512,c768 """ +
                """tcontext=u:object_r:proc_vmstat:s0 tclass=file permissive=1 app=org.chromium.webview_shell""",
            expectedAction = "open",
            expectedSourceContext = "untrusted_app_27",
            expectedTargetContext = "proc_vmstat",
            expectedTargetClass = "file",
            expectedApp = "org.chromium.webview_shell",
            expectedComm = "m.webview_shell",
            expectedName = null,
            expectedMarMetadataTitle = "Denied open for org.chromium.webview_shell (m.webview_shell)",
        ),
        TestCase4(
            logcatLineAfterTag = """type=1400 audit(0.0:17449): avc: denied { open } for """ +
                """comm="m.webview_shell" path="/proc/vmstat" dev="proc" ino=4026532167 """ +
                """scontext=u:r:untrusted_app_27:s0:c118,c256,c512,c768 """ +
                """tcontext=u:object_r:proc_vmstat:s0 tclass=file permissive=1""",
            expectedAction = "open",
            expectedSourceContext = "untrusted_app_27",
            expectedTargetContext = "proc_vmstat",
            expectedTargetClass = "file",
            expectedApp = null,
            expectedComm = "m.webview_shell",
            expectedName = null,
            expectedMarMetadataTitle = "Denied open for untrusted_app_ (m.webview_shell)",
        ),
        TestCase5(
            logcatLineAfterTag = """type=1400 audit(0.0:17449):  avc:  denied  { find } for """ +
                """interface=vendor.somc.hardware.swiqi::IHidlSwiqi pid=19688 """ +
                """scontext=u:r:dumpstate:s0 tcontext=u:object_r:hal_swiqi_hwservice:s0 """ +
                """tclass=hwservice_manager permissive=0""",
            expectedAction = "find",
            expectedSourceContext = "dumpstate",
            expectedTargetContext = "hal_swiqi_hwservice",
            expectedTargetClass = "hwservice_manager",
            expectedApp = null,
            expectedComm = null,
            expectedName = null,
            expectedMarMetadataTitle = "Denied find for dumpstate (null)",
        ),
        TestCase6(
            logcatLineAfterTag = """type=1400 audit(0.0:217): avc: denied { read write } for """ +
                """path="socket:[307668]" dev="sockfs" """ +
                """ino=307668 scontext=u:r:hal_dumpstate_impl:s0 """ +
                """tcontext=u:r:dumpstate:s0 tclass=unix_stream_socket permissive=0""",
            expectedAction = "read write",
            expectedSourceContext = "hal_dumpstate_impl",
            expectedTargetContext = "dumpstate",
            expectedTargetClass = "unix_stream_socket",
            expectedApp = null,
            expectedComm = null,
            expectedName = null,
            expectedMarMetadataTitle = "Denied read write for hal_dumpstate_impl (null)",
        ),

        // If the UID matches a Package, use the Package.
        TestCase7(
            logcatLineAfterTag = """type=1400 audit(0.0:217): avc: denied { read write } for """ +
                """path="socket:[307668]" dev="sockfs" """ +
                """ino=307668 scontext=u:r:hal_dumpstate_impl:s0 """ +
                """tcontext=u:r:dumpstate:s0 tclass=unix_stream_socket permissive=0""",
            uid = 1,
            packages = listOf(
                Package(
                    id = "com.memfault.test.nomatch",
                    userId = 2,
                    versionName = "version_name_nomatch",
                    versionCode = 2,
                ),
                Package(
                    id = "com.memfault.test.match",
                    userId = 1,
                    versionName = "version_name",
                    versionCode = 1,
                ),
            ),
            expectedAction = "read write",
            expectedSourceContext = "hal_dumpstate_impl",
            expectedTargetContext = "dumpstate",
            expectedTargetClass = "unix_stream_socket",
            expectedApp = null,
            expectedComm = null,
            expectedName = null,
            expectedMarMetadataTitle = "Denied read write for hal_dumpstate_impl (null)",
            expectedPackageName = "com.memfault.test.match",
            expectedPackageVersionName = "version_name",
            expectedPackageVersionCode = 1,
        ),

        // If the app matches a Package's id, use the Package.
        TestCase8(
            logcatLineAfterTag = """type=1400 audit(0.0:17449): avc: denied { open } for """ +
                """comm="m.webview_shell" path="/proc/vmstat" dev="proc" ino=4026532167 """ +
                """scontext=u:r:untrusted_app_27:s0:c118,c256,c512,c768 """ +
                """tcontext=u:object_r:proc_vmstat:s0 tclass=file permissive=1 app=com.memfault.test.match""",
            uid = 1,
            packages = listOf(
                Package(
                    id = "com.memfault.test.nomatch",
                    userId = 2,
                    versionName = "version_name_nomatch",
                    versionCode = 2,
                ),
                Package(
                    id = "com.memfault.test.match",
                    userId = 1,
                    versionName = "version_name",
                    versionCode = 1,
                ),
            ),
            expectedAction = "open",
            expectedSourceContext = "untrusted_app_27",
            expectedTargetContext = "proc_vmstat",
            expectedTargetClass = "file",
            expectedApp = "com.memfault.test.match",
            expectedComm = "m.webview_shell",
            expectedName = null,
            expectedMarMetadataTitle = "Denied open for org.chromium.webview_shell (m.webview_shell)",
            expectedPackageName = "com.memfault.test.match",
            expectedPackageVersionName = "version_name",
            expectedPackageVersionCode = 1,
        ),
        TestCase9(
            logcatLineAfterTag = """type=1400 audit(0.0:169): avc: denied { read } for name="init" """ +
                """dev="dm-0" ino=25 scontext=u:r:untrusted_app_25:s0:c512,c768 """ +
                """tcontext=u:object_r:init_exec:s0 tclass=lnk_file permissive=0 """ +
                """app=com.squareup.data.migration.demo.x2""",
            uid = 10083,
            packages = listOf(
                Package(
                    id = "com.memfault.test.match",
                    userId = 10083,
                    versionName = "version_name",
                    versionCode = 1,
                ),
            ),
            expectedAction = "read",
            expectedSourceContext = "untrusted_app_25",
            expectedTargetContext = "init_exec",
            expectedTargetClass = "lnk_file",
            expectedApp = "com.squareup.data.migration.demo.x2",
            expectedName = "init",
            expectedMarMetadataTitle = "Denied read for com.squareup.data.migration.demo.x2 (init)",
            expectedPackageName = "com.memfault.test.match",
            expectedPackageVersionName = "version_name",
            expectedPackageVersionCode = 1,
        ),
    }
}
