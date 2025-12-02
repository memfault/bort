package com.memfault.bort.dropbox

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.parsers.JavaException
import com.memfault.bort.tokenbucket.tokenBucketKey
import org.junit.Test

class TokenBucketKeysTest {
    @Test
    fun javaException() {
        assertThat(
            JavaException(
                packageName = null,
                signatureLines = listOf(
                    "android.app.ActivityThread.handleReceiver(ActivityThread.java:3194)",
                    "com.android.internal.os.RuntimeInit.MethodAndArgsCaller.run(RuntimeInit.java:438)",
                ),
                exceptionClass = null,
                exceptionMessage = null,
            ).tokenBucketKey(),
        ).isEqualTo(
            "7e881ac7cda0b9a1182d3448cdbaa67d",
        )
    }
}
