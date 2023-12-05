package com.memfault.bort.dropbox

import android.os.DropBoxManager
import io.mockk.every
import io.mockk.mockk

const val TEST_TAG = "TEST"
const val TEST_TAG_TO_IGNORE = "TEST_TAG_TO_IGNORE"

fun mockEntry(timeMillis_: Long = 0, tag_: String = TEST_TAG, text: String = "") = mockk<DropBoxManager.Entry> {
    every { tag } returns tag_
    every { timeMillis } returns timeMillis_
    every { close() } returns Unit
    every { getInputStream() } answers {
        text.byteInputStream()
    }
}
