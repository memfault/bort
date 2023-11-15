package com.memfault.bort.logcat

import android.content.SharedPreferences
import com.memfault.bort.LogcatCollectionId
import com.memfault.bort.PREFERENCE_NEXT_LOGCAT_ID
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject

/**
 * Provides the ID of the block of logcat logs to be collected next.
 */
interface NextLogcatCidProvider {
    var cid: LogcatCollectionId

    /**
     * Generate next ID and return Pair(old, new)
     */
    fun rotate(): Pair<LogcatCollectionId, LogcatCollectionId>
}

@ContributesBinding(SingletonComponent::class, boundType = NextLogcatCidProvider::class)
class RealNextLogcatCidProvider @Inject constructor(
    sharedPreferences: SharedPreferences,
) : NextLogcatCidProvider, PreferenceKeyProvider<String>(
    sharedPreferences = sharedPreferences,
    defaultValue = "",
    preferenceKey = PREFERENCE_NEXT_LOGCAT_ID,
) {
    override var cid
        get() = LogcatCollectionId(
            try {
                UUID.fromString(super.getValue())
            } catch (e: IllegalArgumentException) {
                UUID.randomUUID().also {
                    Logger.w("Illegal logcat collection ID: ${super.getValue()}, generating new one: $it")
                    super.setValue(it.toString())
                }
            },
        )
        set(value) = super.setValue(value.uuid.toString())

    override fun rotate(): Pair<LogcatCollectionId, LogcatCollectionId> =
        LogcatCollectionId(UUID.randomUUID()).let { new ->
            val old = cid
            cid = new
            Pair(old, new)
        }
}
