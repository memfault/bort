package com.memfault.bort.ota.lib

import com.memfault.bort.shared.LogLevel
import com.memfault.bort.shared.LogLevel.TEST
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@ContributesBinding(SingletonComponent::class)
class DebugOtaLoggerSettings
@Inject constructor() : OtaLoggerSettings {
    override val minLogcatLevel: LogLevel = TEST
}
