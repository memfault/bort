package com.memfault.bort.tokenbucket

import android.content.Context
import android.content.SharedPreferences
import com.memfault.bort.BortJson
import com.memfault.bort.TOKEN_STORE_PREFERENCE_FILE_NAME_TEMPLATE
import com.memfault.bort.shared.PreferenceKeyProvider

interface TokenBucketStorage {
    fun readMap(): StoredTokenBucketMap
    fun writeMap(map: StoredTokenBucketMap)
}

class RealTokenBucketStorage(
    sharedPreferences: SharedPreferences,
    preferenceKey: String,
) : TokenBucketStorage, PreferenceKeyProvider<String>(
    sharedPreferences = sharedPreferences,
    defaultValue = BortJson.encodeToString(StoredTokenBucketMap.serializer(), StoredTokenBucketMap()),
    preferenceKey = preferenceKey,
) {
    override fun readMap(): StoredTokenBucketMap =
        try {
            BortJson.decodeFromString(StoredTokenBucketMap.serializer(), super.getValue())
        } catch (e: Exception) {
            StoredTokenBucketMap()
        }

    override fun writeMap(map: StoredTokenBucketMap) =
        super.setValue(
            BortJson.encodeToString(StoredTokenBucketMap.serializer(), map)
        )

    companion object {
        fun createFor(
            context: Context,
            key: String,
        ) = RealTokenBucketStorage(
            sharedPreferences = context.getSharedPreferences(
                TOKEN_STORE_PREFERENCE_FILE_NAME_TEMPLATE.format(key),
                Context.MODE_PRIVATE
            ),
            preferenceKey = "tokens",
        )
    }
}
