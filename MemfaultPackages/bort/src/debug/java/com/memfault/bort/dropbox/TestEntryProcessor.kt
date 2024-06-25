package com.memfault.bort.dropbox

import android.os.DropBoxManager
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@ContributesMultibinding(SingletonComponent::class)
class TestEntryProcessor @Inject constructor() : EntryProcessor() {
    override val tags = listOf("BORT_TEST")

    override suspend fun process(entry: DropBoxManager.Entry) {
        Logger.test("Processing test entry with text: ${entry.getText(1024)}")
    }
}
