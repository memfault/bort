package com.memfault.bort.tokenbucket

import com.memfault.bort.parsers.JavaException
import java.security.MessageDigest
import okio.ByteString.Companion.toByteString

fun JavaException.tokenBucketKey() =
    MessageDigest.getInstance("MD5").let { digest ->
        unparsedStackFrames.forEach {
            digest.update(it.encodeToByteArray())
        }
        digest.digest().toByteString().hex()
    }
