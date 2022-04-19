package com.memfault.bort.clientserver

import com.memfault.bort.CachedAsyncProperty
import com.memfault.bort.DumpsterClient
import com.memfault.bort.shared.ClientServerMode
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

fun interface CachedClientServerMode {
    suspend fun get(): ClientServerMode
}

@Singleton
@ContributesBinding(scope = SingletonComponent::class)
class RealCachedClientServerMode @Inject constructor(
    private val dumpsterClient: DumpsterClient,
) : CachedClientServerMode {
    private val clientServerMode = CachedAsyncProperty {
        ClientServerMode.decode(dumpsterClient.getprop()?.get(ClientServerMode.SYSTEM_PROP))
    }

    override suspend fun get() = clientServerMode.get()
}
