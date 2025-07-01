package com.memfault.bort.settings

import kotlin.time.Duration

open class FakeHttpApiSettings : HttpApiSettings {
    override val projectKey: String
        get() = TODO("Not yet implemented")
    override val filesBaseUrl: String
        get() = TODO("Not yet implemented")
    override val deviceBaseUrl: String
        get() = TODO("Not yet implemented")
    override val uploadNetworkConstraint: NetworkConstraint
        get() = TODO("Not yet implemented")
    override val uploadRequiresBatteryNotLow: Boolean
        get() = TODO("Not yet implemented")
    override val uploadRequiresCharging: Boolean
        get() = TODO("Not yet implemented")
    override val uploadCompressionEnabled: Boolean
        get() = TODO("Not yet implemented")
    override val connectTimeout: Duration
        get() = TODO("Not yet implemented")
    override val writeTimeout: Duration
        get() = TODO("Not yet implemented")
    override val readTimeout: Duration
        get() = TODO("Not yet implemented")
    override val callTimeout: Duration
        get() = TODO("Not yet implemented")
    override val zipCompressionLevel: Int
        get() = TODO("Not yet implemented")
    override val batchMarUploads: Boolean
        get() = TODO("Not yet implemented")
    override val batchedMarUploadPeriod: Duration
        get() = TODO("Not yet implemented")
    override val deviceConfigInterval: Duration
        get() = TODO("Not yet implemented")
    override val maxMarFileSizeBytes: Int
        get() = TODO("Not yet implemented")
    override val maxMarStorageBytes: Long
        get() = TODO("Not yet implemented")
    override val maxMarSampledStoredAge: Duration
        get() = TODO("Not yet implemented")
    override val maxMarUnsampledStoredAge: Duration
        get() = TODO("Not yet implemented")
    override val maxMarUnsampledStoredBytes: Long
        get() = TODO("Not yet implemented")
}
