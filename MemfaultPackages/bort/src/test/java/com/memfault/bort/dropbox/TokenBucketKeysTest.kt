package com.memfault.bort.dropbox

import com.memfault.bort.parsers.JavaException
import com.memfault.bort.tokenbucket.tokenBucketKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TokenBucketKeysTest {
    @Test
    fun javaException() {
        assertEquals(
            "7e881ac7cda0b9a1182d3448cdbaa67d",
            JavaException(
                packageName = null,
                unparsedStackFrames = listOf(
                    "android.app.ActivityThread.handleReceiver(ActivityThread.java:3194)",
                    "com.android.internal.os.RuntimeInit.MethodAndArgsCaller.run(RuntimeInit.java:438)",
                ),
            ).tokenBucketKey(),
        )
    }
}
