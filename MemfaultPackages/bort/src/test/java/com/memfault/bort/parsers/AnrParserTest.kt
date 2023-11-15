package com.memfault.bort.parsers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AnrParserTest {
    @Test
    fun packageNameOK() {
        assertEquals(
            "com.memfault.bort_e2e_helper",
            AnrParser(FIXTURE.byteInputStream()).parse().packageName,
        )
    }

    @Test
    fun noPackage() {
        assertNull(
            AnrParser(INVALID_FIXTURE.byteInputStream()).parse().packageName,
        )
    }
}

private val INVALID_FIXTURE = "nothing"

// note: this fixture is redacted because we only care about the header
private val FIXTURE = """
Process: com.memfault.bort_e2e_helper
PID: 1456
Flags: 0x38c83e0d
Package: com.memfault.bort_e2e_helper v27 (8.1.0)
Foreground: No
Subject: executing service com.memfault.bort_e2e_helper/.SystemUIService
Build: Android/aosp_arm64/generic_arm64:8.1.0/OC/root04302340:eng/test-keys

CPU usage from 3290ms to -31207ms ago (1970-01-01 00:02:49.554 to 2020-11-06 10:52:56.705):
  22% 1300/system_server: 14% user + 8% kernel / faults: 3273 minor 13 major
  14% 1594/com.android.phone: 11% user + 2.5% kernel / faults: 7476 minor 54 major
  14% 1456/com.memfault.bort_e2e_helper: 11% user + 2.3% kernel / faults: 7333 minor 34 major
  8.5% 1533/webview_zygote32: 7% user + 1.5% kernel / faults: 4741 minor
  8.3% 1084/surfaceflinger: 4.4% user + 3.9% kernel / faults: 197 minor 1 major
  3% 1076/android.hardware.graphics.composer@2.1-service: 1.1% user + 1.9% kernel / faults: 2 minor
  1.3% 1094/installd: 0.6% user + 0.7% kernel / faults: 30 minor
  2.1% 1090/adbd: 0.4% user + 1.7% kernel / faults: 805 minor
  1.9% 1439/com.android.inputmethod.latin: 1.4% user + 0.5% kernel / faults: 427 minor 12 major
  1.6% 1078/android.hardware.sensors@1.0-service: 0.7% user + 0.8% kernel
""".trimIndent()
