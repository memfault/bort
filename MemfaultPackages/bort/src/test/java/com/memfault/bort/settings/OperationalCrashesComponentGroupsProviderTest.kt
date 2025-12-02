package com.memfault.bort.settings

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import assertk.assertions.single
import com.memfault.bort.scopes.Scope
import com.memfault.bort.scopes.buildTestScope
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Before
import org.junit.Test

class OperationalCrashesComponentGroupsProviderTest {

    private val testDispatcher = TestCoroutineScheduler()
    private var componentGroupsJson = JsonObject(emptyMap())
    private val metricsSettings = mockk<MetricsSettings> {
        every { operationalCrashesComponentGroups } answers { componentGroupsJson }
    }
    private val settingsProvider = mockk<SettingsProvider> {
        every { metricsSettings } answers { this@OperationalCrashesComponentGroupsProviderTest.metricsSettings }
    }
    private val settingsFlow = object : SettingsFlow {
        override val settings: Flow<SettingsProvider> = MutableStateFlow(settingsProvider)
    }

    private val provider = RealOperationalCrashesComponentGroupsProvider(
        defaultCoroutineContext = testDispatcher,
        settingsFlow = settingsFlow,
    )

    @Before
    fun setup() {
        val testScope = Scope.buildTestScope(context = testDispatcher)
        testScope.register(provider)
    }

    @Test
    fun deserialize_error_empty() = runTest(testDispatcher) {
        assertFailure {
            componentGroupsJson =
                Json.Default.parseToJsonElement("""""").jsonObject
        }.isInstanceOf<SerializationException>()

        val componentGroups = provider.componentGroups(settingsProvider)

        assertThat(componentGroups.groups).isEmpty()
    }

    @Test
    fun deserialize_error_nothing() = runTest(testDispatcher) {
        componentGroupsJson =
            Json.Default.parseToJsonElement("""{}""").jsonObject

        val componentGroups = provider.componentGroups(settingsProvider)

        assertThat(componentGroups.groups).isEmpty()
    }

    @Test
    fun deserialize_empty() = runTest(testDispatcher) {
        componentGroupsJson =
            Json.Default.parseToJsonElement(
                """{"groups": [] }""".trimIndent(),
            ).jsonObject

        val componentGroups = provider.componentGroups(settingsProvider)

        assertThat(componentGroups.groups).isEmpty()
    }

    @Test
    fun deserialize_wrong() = runTest(testDispatcher) {
        componentGroupsJson =
            Json.Default.parseToJsonElement(
                """{"groups": ["name"] }""".trimIndent(),
            ).jsonObject

        val componentGroups = provider.componentGroups(settingsProvider)

        assertThat(componentGroups.groups).isEmpty()
    }

    @Test
    fun deserialize_missingPattern() = runTest(testDispatcher) {
        componentGroupsJson =
            Json.Default.parseToJsonElement(
                """{"groups": [{"name": "name" }] }""".trimIndent(),
            ).jsonObject

        val componentGroups = provider.componentGroups(settingsProvider)

        assertThat(componentGroups.groups).isEmpty()
    }

    @Test
    fun deserialize_wrongFormat() = runTest(testDispatcher) {
        componentGroupsJson =
            Json.Default.parseToJsonElement(
                """{"groups": [{"name": 0, "patterns": 0 }] }""".trimIndent(),
            ).jsonObject

        val componentGroups = provider.componentGroups(settingsProvider)

        assertThat(componentGroups.groups).isEmpty()
    }

    @Test
    fun deserialize_success() = runTest(testDispatcher) {
        componentGroupsJson =
            Json.Default.parseToJsonElement(
                """{"groups": [{"name": "name", "patterns": []}]}""".trimIndent(),
            ).jsonObject

        val componentGroups = provider.componentGroups(settingsProvider)

        assertThat(componentGroups.groups).single().all {
            prop(OperationalCrashesComponentGroup::name).isEqualTo("name")
            prop(OperationalCrashesComponentGroup::patterns).isEmpty()
        }
    }

    @Test
    fun deserialize_success_multiple() = runTest(testDispatcher) {
        componentGroupsJson =
            Json.Default.parseToJsonElement(
                """{"groups": [{"name": "name", "patterns": ["/proc/memfaultd", "com.memfault"]}]}""".trimIndent(),
            ).jsonObject

        val componentGroups = provider.componentGroups(settingsProvider)

        assertThat(componentGroups.groups).single().all {
            prop(OperationalCrashesComponentGroup::name).isEqualTo("name")
            prop(OperationalCrashesComponentGroup::patterns).containsExactly("/proc/memfaultd", "com.memfault")
        }
    }
}
