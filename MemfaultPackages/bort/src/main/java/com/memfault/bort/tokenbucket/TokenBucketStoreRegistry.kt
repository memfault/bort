package com.memfault.bort.tokenbucket

import android.content.Context
import com.memfault.bort.TOKEN_STORE_PREFERENCE_FILE_NAME_TEMPLATE

class TokenBucketStoreRegistry {
    private val registry: MutableMap<String, TokenBucketStore> = mutableMapOf()

    operator fun set(key: String, store: TokenBucketStore) {
        check(!registry.containsKey(key)) { "TokenBucketStore already exists for key $key" }
        registry[key] = store
    }

    fun handleLinuxReboot() {
        registry.forEach { _, store ->
            store.handleLinuxReboot()
        }
    }

    /**
     * For testing purposes: resets all token bucket stores.
     */
    fun reset() {
        registry.forEach { _, store ->
            store.reset()
        }
    }
}

fun TokenBucketStoreRegistry.createAndRegisterStore(
    context: Context,
    key: String,
    block: (storage: TokenBucketStorage) -> TokenBucketStore,
): TokenBucketStore =
    block(
        RealTokenBucketStorage(
            sharedPreferences = context.getSharedPreferences(
                TOKEN_STORE_PREFERENCE_FILE_NAME_TEMPLATE.format(key),
                Context.MODE_PRIVATE
            ),
            preferenceKey = "tokens",
        )
    ).also {
        set(key, it)
    }
