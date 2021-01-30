package com.memfault.bort.tokenbucket

import com.memfault.bort.time.BoxedDuration
import io.mockk.spyk
import io.mockk.verify
import kotlin.time.milliseconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

data class MockTokenBucketStorage(
    var map: StoredTokenBucketMap,
) : TokenBucketStorage {
    override fun readMap(): StoredTokenBucketMap = map

    override fun writeMap(map: StoredTokenBucketMap) {
        this.map = map
    }
}

class TokenBucketStoreTest {
    val key = "x"
    val capacity = 4
    val period = 1.milliseconds

    lateinit var tokenBucketFactory: TokenBucketFactory
    lateinit var mockElapsedRealtime: MockElapsedRealtime

    @BeforeEach
    fun setUp() {
        mockElapsedRealtime = MockElapsedRealtime()
        tokenBucketFactory = MockTokenBucketFactory(
            defaultCapacity = capacity,
            defaultPeriod = period,
            mockElapsedRealtime = mockElapsedRealtime,
        )
    }

    @Test
    fun editNoChange() {
        val storageProvider = spyk(MockTokenBucketStorage(map = StoredTokenBucketMap()))
        val blockResult = 1234
        assertEquals(
            blockResult,
            TokenBucketStore(
                storage = storageProvider,
                maxBuckets = 1,
                tokenBucketFactory = tokenBucketFactory,
            ).edit {
                // not making any changes to the map here
                blockResult
            }
        )
        verify(exactly = 0) { storageProvider.writeMap(any()) }
    }

    @Test
    fun edit() {
        val storage = spyk(
            MockTokenBucketStorage(
                map = StoredTokenBucketMap(
                    mapOf(
                        key to StoredTokenBucket(
                            count = 3,
                            capacity = capacity,
                            period = BoxedDuration(period),
                            periodStartElapsedRealtime = BoxedDuration(mockElapsedRealtime.now)
                        )
                    )
                )
            )
        )

        val blockResult = 1234
        assertEquals(
            blockResult,
            TokenBucketStore(
                storage = storage,
                maxBuckets = 1,
                tokenBucketFactory = tokenBucketFactory,
            ).edit { map ->
                val bucket = map.upsertBucket(key = key, capacity = capacity, period = period)
                assertNotNull(bucket)
                bucket?.take() // decrements count
                blockResult
            }
        )

        verify(exactly = 1) {
            storage.writeMap(
                StoredTokenBucketMap(
                    mapOf(
                        key to StoredTokenBucket(
                            count = 2,
                            capacity = capacity,
                            period = BoxedDuration(period),
                            periodStartElapsedRealtime = BoxedDuration(mockElapsedRealtime.now)
                        )
                    )
                )
            )
        }
    }

    @Test
    fun handleBoot() {
        val initialBucket = StoredTokenBucket(
            count = 3,
            capacity = capacity,
            period = BoxedDuration(period),
            periodStartElapsedRealtime = BoxedDuration(12345.milliseconds)
        )
        val storage = spyk(
            MockTokenBucketStorage(
                map = StoredTokenBucketMap(
                    mapOf(
                        key to initialBucket
                    )
                )
            )
        )

        TokenBucketStore(
            storage = storage,
            maxBuckets = 1,
            tokenBucketFactory = tokenBucketFactory,
        ).handleBoot()

        verify(exactly = 1) {
            storage.writeMap(
                StoredTokenBucketMap(
                    mapOf(
                        key to initialBucket.copy(
                            periodStartElapsedRealtime = BoxedDuration(mockElapsedRealtime.now)
                        )
                    )
                )
            )
        }
    }

    @Test
    fun readCache() {
        val storageProvider = spyk(MockTokenBucketStorage(map = StoredTokenBucketMap()))
        val store = TokenBucketStore(
            storage = storageProvider,
            maxBuckets = 1,
            tokenBucketFactory = tokenBucketFactory,
        )
        store.edit { }
        store.edit { }
        // Only read once from store:
        verify(exactly = 1) { storageProvider.readMap() }
    }
}
