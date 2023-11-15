package com.memfault.bort.tokenbucket

import com.memfault.bort.parsers.JavaException
import okio.ByteString.Companion.toByteString
import java.security.MessageDigest

fun JavaException.tokenBucketKey() =
    MessageDigest.getInstance("MD5").let { digest ->
        unparsedStackFrames.forEach {
            digest.update(it.encodeToByteArray())
        }
        digest.digest().toByteString().hex()
    }
