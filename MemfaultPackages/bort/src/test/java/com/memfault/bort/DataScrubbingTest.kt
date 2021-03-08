package com.memfault.bort

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DataScrubbingTest {
    @Test
    fun emailMatcher() {
        assertEquals(
            listOf(
                "mail@example.com",
                "test@gmail.com",
                "something@gmail.com",
                "Should mail something to something@gmail.com tomorrow",
                "not_a_mail",
                "two@@example.com",
                "nobody@notld",
            ).map { EmailScrubbingRule.clean(it) },
            listOf(
                "{{EMAIL}}",
                "{{EMAIL}}",
                "{{EMAIL}}",
                "Should mail something to {{EMAIL}} tomorrow",
                "not_a_mail",
                "two@@example.com",
                "nobody@notld"
            ),
        )
    }

    @Test
    fun credentialMatcher() {
        assertEquals(
            listOf(
                "username: root password: root",
                "short u: root p: root",
            ).map { CredentialScrubbingRule.clean(it) },
            listOf(
                "username: {{USERNAME}} password: {{PASSWORD}}",
                "short u: {{USERNAME}} p: {{PASSWORD}}",
            )
        )
    }

    @Test
    fun testScrubbing() {
        val scrubber = DataScrubber(listOf(EmailScrubbingRule, CredentialScrubbingRule))
        assertEquals(
            listOf(
                "regular line",
                "something something {{EMAIL}}",
                "u: {{USERNAME}} password: {{PASSWORD}}",
            ),
            sequenceOf(
                "regular line",
                "something something secret@mflt.com",
                "u: root password: hunter2",
            ).scrubbedWith(scrubber).toList()
        )
    }

    @Test
    fun testBlake2b() {
        // Not meant to fully test blake2b, just to check that it returns the same as the backend
        // λ python3 -sBc 'import hashlib;print(hashlib.blake2b("MEMFAULT".encode(), digest_size=4).hexdigest())'
        //   b047528a
        assertEquals("***SCRUBBED-b047528a***", DataScrubber().scrubEntirely("MEMFAULT"))
    }
}
