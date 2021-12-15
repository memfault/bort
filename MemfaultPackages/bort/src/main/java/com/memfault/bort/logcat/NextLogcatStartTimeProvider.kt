package com.memfault.bort.logcat

import android.content.SharedPreferences
import com.memfault.bort.BortJson
import com.memfault.bort.PREFERENCE_NEXT_LOGCAT_START_TIME_JSON
import com.memfault.bort.shared.Logger
import com.memfault.bort.shared.PreferenceKeyProvider
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.BaseAbsoluteTime
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.SerializationException

/**
 * Provides the start time of the block of logcat logs to be collected next.
 */
interface NextLogcatStartTimeProvider {
    var nextStart: BaseAbsoluteTime
}

@ContributesBinding(SingletonComponent::class, boundType = NextLogcatStartTimeProvider::class)
class RealNextLogcatStartTimeProvider @Inject constructor(
    sharedPreferences: SharedPreferences
) : NextLogcatStartTimeProvider, PreferenceKeyProvider<String>(
    sharedPreferences = sharedPreferences,
    defaultValue = BortJson.encodeToString(AbsoluteTime.serializer(), DEFAULT_LAST_END),
    preferenceKey = PREFERENCE_NEXT_LOGCAT_START_TIME_JSON,
) {
    override var nextStart
        get() = try {
            BortJson.decodeFromString(
                AbsoluteTime.serializer(), super.getValue()
            ) as BaseAbsoluteTime
        } catch (e: SerializationException) {
            AbsoluteTime.now().also {
                Logger.w("Logcat start time failed to deserialize, falling back to now")
            }
        }
        set(value) = super.setValue(
            BortJson.encodeToString(AbsoluteTime.serializer(), AbsoluteTime(value))
        )
}

fun NextLogcatStartTimeProvider.handleTimeChanged(getNow: () -> BaseAbsoluteTime = AbsoluteTime.Companion::now) {
    val now = getNow()
    if (nextStart > now) {
        Logger.w("Detected backwards time change! $nextStart < $now")
        // Not much more we can do to prevent missing logs, other than adjusting the next starting point:
        nextStart = now
    }
}

private val DEFAULT_LAST_END = AbsoluteTime(Instant.EPOCH)
