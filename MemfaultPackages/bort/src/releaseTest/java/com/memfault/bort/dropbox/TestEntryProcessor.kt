package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime

fun testDropBoxEntryProcessors(): Map<String, EntryProcessor> {
    val testEntryProcessor = TestEntryProcessor()
    return mapOf(
        *testEntryProcessor.tagPairs()
    )
}

class TestEntryProcessor : EntryProcessor() {
    override val tags = listOf("BORT_TEST")

    override suspend fun process(entry: DropBoxManager.Entry, fileTime: AbsoluteTime?) {
        Logger.test("Processing test entry with text: ${entry.getText(1024)}")
    }
}
