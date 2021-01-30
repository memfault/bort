package com.memfault.bort.parsers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JavaExceptionParserTest {
    @Test
    fun ok() {
        assertEquals(
            JavaException(
                unparsedStackFrames = listOf(
                    "android.app.ActivityThread.handleReceiver(ActivityThread.java:3194)",
                    "com.android.internal.os.RuntimeInit.MethodAndArgsCaller.run(RuntimeInit.java:438)",
                    "com.android.internal.os.ZygoteInit.main(ZygoteInit.java:807)",
                    "com.memfault.bort_e2e_helper.receivers.E2ETestReceiver.onJavaCrash(E2ETestReceiver.kt:35)",
                    "com.memfault.bort_e2e_helper.receivers.E2ETestReceiver.onReceive(E2ETestReceiver.kt:23)",
                    "android.app.ActivityThread.handleReceiver(ActivityThread.java:3187)",
                ),
            ),
            JavaExceptionParser(FIXTURE.byteInputStream()).parse()
        )
    }
}

private val FIXTURE = """
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
