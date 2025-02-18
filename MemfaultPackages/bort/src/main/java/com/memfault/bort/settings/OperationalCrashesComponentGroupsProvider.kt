package com.memfault.bort.settings

import com.memfault.bort.Default
import com.memfault.bort.scopes.Scope
import com.memfault.bort.scopes.Scoped
import com.memfault.bort.scopes.coroutineScope
import com.memfault.bort.shared.BortSharedJson
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Serializable
data class OperationalCrashesComponentGroup(
    val name: String,
    val patterns: List<String>,
)

@Serializable
data class OperationalCrashesComponentGroups(
    val groups: List<OperationalCrashesComponentGroup> = emptyList(),
)

fun interface OperationalCrashesComponentGroupsProvider : () -> OperationalCrashesComponentGroups

@Singleton
@ContributesBinding(SingletonComponent::class, boundType = OperationalCrashesComponentGroupsProvider::class)
@ContributesMultibinding(SingletonComponent::class, boundType = Scoped::class)
class RealOperationalCrashesComponentGroupsProvider
@Inject constructor(
    @Default private val defaultCoroutineContext: CoroutineContext,
    private val settingsFlow: SettingsFlow,
) : OperationalCrashesComponentGroupsProvider, Scoped {

    private val componentGroups = MutableStateFlow(OperationalCrashesComponentGroups())

    override fun onEnterScope(scope: Scope) {
        scope.coroutineScope(defaultCoroutineContext)
            .launch {
                settingsFlow.settings
                    .collectLatest { settingsProvider ->
                        componentGroups.value = componentGroups(settingsProvider)
                    }
            }
    }

    override fun onExitScope() = Unit

    override fun invoke(): OperationalCrashesComponentGroups = componentGroups.value

    internal fun componentGroups(provider: SettingsProvider): OperationalCrashesComponentGroups = try {
        BortSharedJson.decodeFromJsonElement(
            deserializer = OperationalCrashesComponentGroups.serializer(),
            element = provider.metricsSettings.operationalCrashesComponentGroups,
        )
    } catch (e: SerializationException) {
        Logger.w("Error deserializing operational crash groups", e)
        OperationalCrashesComponentGroups()
    }
}
