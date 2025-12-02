package com.memfault.bort.parsers

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class JavaExceptionParserTest {
    @Test
    fun exception() {
        assertThat(
            JavaExceptionParser(EXCEPTION_FIXTURE.lineSequence()).parse(),
        ).isEqualTo(
            JavaException(
                packageName = "com.memfault.bort_e2e_helper",
                signatureLines = listOf(
                    "java.lang.RuntimeException",
                    "android.app.ActivityThread.handleReceiver",
                    "com.android.internal.os.RuntimeInit.MethodAndArgsCaller.run",
                    "com.android.internal.os.ZygoteInit.main",
                    "java.lang.Exception",
                    "com.memfault.bort_e2e_helper.receivers.E2ETestReceiver.onJavaCrash",
                    "com.memfault.bort_e2e_helper.receivers.E2ETestReceiver.onReceive",
                    "android.app.ActivityThread.handleReceiver",
                ),
                exceptionClass = "java.lang.RuntimeException",
                exceptionMessage = "Unable to start receiver",
            ),
        )
    }

    @Test
    fun wtf() {
        assertThat(
            JavaExceptionParser(WTF_FIXTURE.lineSequence()).parse(),
        ).isEqualTo(
            JavaException(
                packageName = null,
                signatureLines = listOf(
                    "android.util.Log${'$'}TerribleFailure",
                    "android.util.Log.wtf",
                    "android.util.Slog.wtf",
                    "com.android.server.SystemServer.run",
                    "com.android.server.SystemServer.main",
                    "java.lang.reflect.Method.invoke",
                    "com.android.internal.os.RuntimeInit${'$'}MethodAndArgsCaller.run",
                    "com.android.internal.os.ZygoteInit.main",
                ),
                exceptionClass = "android.util.Log${'$'}TerribleFailure",
                exceptionMessage = "SystemServer init took too long. uptimeMillis=108077012",
            ),
        )
    }

    @Test
    fun truncatedWtf() {
        assertThat(
            JavaExceptionParser(
                """
                Package: com.memfault.usagereporter
                Foreground: Yes
                Build: generic/aosp_cf_x86_phone/vsoc_x86:9/PPRL.190801.002/root04302340:userdebug/test-keys
                android.util.Log${'$'}TerribleFailure: neither /proc/wakelocks nor /d/wakeup_sources exists
                    at android.util.Log.wtf(Log.java:309)
                """.trimIndent().lineSequence(),
            ).parse(),
        ).isEqualTo(
            JavaException(
                packageName = "com.memfault.usagereporter",
                signatureLines = listOf(
                    "android.util.Log${'$'}TerribleFailure",
                    "android.util.Log.wtf",
                ),
                exceptionClass = "android.util.Log${'$'}TerribleFailure",
                exceptionMessage = "neither /proc/wakelocks nor /d/wakeup_sources exists",
            ),
        )
    }
}

private val EXCEPTION_FIXTURE = """
Process: com.memfault.bort_e2e_helper
PID: 3220
Flags: 0x28e83e46
Package: com.memfault.bort_e2e_helper v2070100 (2.7.1+0-DEV-dc4a36119)
Foreground: No
Build: Android/aosp_arm64/generic_arm64:8.1.0/OC/root04302340:eng/test-keys

java.lang.RuntimeException: Unable to start receiver
	at android.app.ActivityThread.handleReceiver(ActivityThread.java:3194)
	at com.android.internal.os.RuntimeInit.MethodAndArgsCaller.run(RuntimeInit.java:438)
	at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:807)
Caused by: java.lang.Exception: 63bd07f1-1869-4a5c-953e-4a01e7318cf6
	at com.memfault.bort_e2e_helper.receivers.E2ETestReceiver.onJavaCrash(E2ETestReceiver.kt:35)
	at com.memfault.bort_e2e_helper.receivers.E2ETestReceiver.onReceive(E2ETestReceiver.kt:23)
	at android.app.ActivityThread.handleReceiver(ActivityThread.java:3187)
	... 2 more

""".trimIndent()

private val WTF_FIXTURE = """
Process: system_server
Subject: SystemServerTiming
Build: Android/aosp_arm64/generic_arm64:8.1.0/OC/root04302340:eng/test-keys

android.util.Log${'$'}TerribleFailure: SystemServer init took too long. uptimeMillis=108077012
    at android.util.Log.wtf(Log.java:309)
    at android.util.Slog.wtf(Slog.java:94)
    at com.android.server.SystemServer.run(SystemServer.java:537)
    at com.android.server.SystemServer.main(SystemServer.java:357)
    at java.lang.reflect.Method.invoke(Native Method)
    at com.android.internal.os.RuntimeInit${'$'}MethodAndArgsCaller.run(RuntimeInit.java:492)
    at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:913)
""".trimIndent()
