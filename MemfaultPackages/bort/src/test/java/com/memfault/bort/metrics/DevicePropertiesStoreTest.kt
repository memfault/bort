package com.memfault.bort.metrics

import androidx.test.core.app.ApplicationProvider
import com.memfault.bort.metrics.PropertyType.BOOL
import com.memfault.bort.metrics.PropertyType.DOUBLE
import com.memfault.bort.metrics.PropertyType.INT
import com.memfault.bort.metrics.PropertyType.STRING
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
internal class DevicePropertiesStoreTest {
    lateinit var store: DevicePropertiesStore

    @Before
    fun setup() {
        store = DevicePropertiesStore(DevicePropertiesDb.create(ApplicationProvider.getApplicationContext()))
    }

    @Test
    fun testTyping() = runBlocking {
        store.upsert(name = "a", value = "a1", type = STRING, internal = false)
        store.upsert(name = "1", value = "1", type = INT, internal = false)
        store.upsert(name = "1.3", value = "1.3", type = DOUBLE, internal = false)
        store.upsert(name = "true", value = "true", type = BOOL, internal = false)
        store.upsert(name = "bad1", value = "1.1", type = INT, internal = false)
        store.upsert(name = "bad1.3", value = "1.3xx", type = DOUBLE, internal = false)
        store.upsert(name = "badtrue", value = "badtrue", type = BOOL, internal = false)
        store.upsert(name = "false", value = "false", type = BOOL, internal = false)
        val props = store.collectDeviceProperties(internal = false)
        assertEquals(
            mapOf(
                "a" to JsonPrimitive("a1"),
                "1" to JsonPrimitive(1),
                "1.3" to JsonPrimitive(1.3),
                "true" to JsonPrimitive(true),
                "bad1" to JsonPrimitive("1.1"),
                "bad1.3" to JsonPrimitive("1.3xx"),
                "badtrue" to JsonPrimitive("badtrue"),
                "false" to JsonPrimitive(false),
            ),
            props
        )
    }

    @Test
    fun testInternal() = runBlocking {
        store.upsert(name = "a", value = "a1", type = STRING, internal = false)
        store.upsert(name = "1", value = "1", type = INT, internal = true)
        val props = store.collectDeviceProperties(internal = false)
        assertEquals(
            mapOf(
                "a" to JsonPrimitive("a1"),
            ),
            props
        )
        // Internals props
        val propsInternal = store.collectDeviceProperties(internal = true)
        assertEquals(
            mapOf(
                "1" to JsonPrimitive(1),
            ),
            propsInternal
        )
    }

    @Test
    fun testChanged() = runBlocking {
        store.upsert(name = "a", value = "a1", type = STRING, internal = false)
        assertEquals(
            mapOf(
                "a" to JsonPrimitive("a1"),
            ),
            store.collectDeviceProperties(internal = false)
        )

        // Collect again - no changes
        assertEquals(mapOf<String, JsonPrimitive>(), store.collectDeviceProperties(internal = false))

        // Record same value again
        // Collect again - no changes
        store.upsert(name = "a", value = "a1", type = STRING, internal = false)
        assertEquals(mapOf<String, JsonPrimitive>(), store.collectDeviceProperties(internal = false))

        // Record different value
        store.upsert(name = "a", value = "a2", type = STRING, internal = false)
        assertEquals(
            mapOf(
                "a" to JsonPrimitive("a2"),
            ),
            store.collectDeviceProperties(internal = false)
        )
    }
}
