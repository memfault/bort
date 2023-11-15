package com.memfault.bort.dropbox

data class FakeDropBoxPendingTimeChangeProvider(
    override var pendingBackwardsTimeChange: Boolean,
) : DropBoxPendingTimeChangeProvider
