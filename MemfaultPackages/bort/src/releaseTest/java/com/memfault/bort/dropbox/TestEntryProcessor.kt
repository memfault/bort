package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.shared.Logger

fun testDropBoxEntryProcessors(): Map<String, EntryProcessor> {
    val testEntryProcessor = TestEntryProcessor()
    return mapOf(
        *testEntryProcessor.tagPairs()
    )
}

class TestEntryProcessor : EntryProcessor() {
    override val tags = listOf("BORT_TEST")

    override fun process(entry: DropBoxManager.Entry) {
        Logger.test("Processing test entry with text: ${entry.getText(1024)}")
    }
}
