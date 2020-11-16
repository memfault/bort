package com.memfault.bort.dropbox

data class FakeLastProcessedEntryProvider(override var timeMillis: Long) : DropBoxLastProcessedEntryProvider
