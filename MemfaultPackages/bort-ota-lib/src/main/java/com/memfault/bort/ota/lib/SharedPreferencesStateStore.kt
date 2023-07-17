package com.memfault.bort.ota.lib

import android.content.SharedPreferences
import com.memfault.bort.shared.BortSharedJson
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * An interface for persisting state. Android applications may be killed at any point by the system but ideally
 * we would want to keep the update state where applicable (i.e. if there's a download ready or waiting for user
 * input).
 */
interface StateStore {
    fun store(state: State)
    fun read(): State?
}

@ContributesBinding(SingletonComponent::class)
class SharedPreferencesStateStore @Inject constructor(
    private val sharedPreferences: SharedPreferences,
) : StateStore {
    override fun store(state: State) {
        val serialized = BortSharedJson.encodeToString(state)
        sharedPreferences.edit()
            .putString(STATE_KEY, serialized)
            .apply()
    }

    override fun read(): State? =
        sharedPreferences.getString(STATE_KEY, null)?.let { serializedState ->
            try {
                BortSharedJson.decodeFromString<State>(serializedState)
            } catch (ex: SerializationException) {
                Logger.e("Found an unknown state, ignoring", ex)
                null
            }
        }
}
