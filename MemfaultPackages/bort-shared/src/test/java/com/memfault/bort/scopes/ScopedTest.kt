package com.memfault.bort.scopes

import com.memfault.bort.scopes.Scoped.Companion.registerAllSorted
import com.memfault.bort.scopes.Scoped.ScopedPriority
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.reflect.KClass

class ScopedTest {
    private val registered = mutableListOf<KClass<out TestBaseScoped>>()
    abstract inner class TestBaseScoped : Scoped {
        override fun onEnterScope(scope: Scope) {
            registered.add(this::class)
        }

        override fun onExitScope() = Unit
    }

    private inner class A : TestBaseScoped() {
        override fun priority(): ScopedPriority = ScopedPriority.STRICT_MODE
    }
    private inner class B : TestBaseScoped()
    private inner class C : TestBaseScoped()

    private val testDispatcher = TestCoroutineScheduler()

    @Test
    fun `registerAllSorted`() = runTest(testDispatcher) {
        val services = setOf(C(), A(), B())

        val testScope = Scope.buildTestScope(context = testDispatcher)
        testScope.registerAllSorted(services)

        assert(registered == listOf(A::class, B::class, C::class))
    }
}
