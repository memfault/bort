package com.memfault.bort.scopes

import com.memfault.bort.Main
import com.memfault.bort.dagger.InjectSet
import com.memfault.bort.scopes.Scoped.Companion.registerAllSorted
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Singleton
class RootScopeBuilder
@Inject constructor(
    @Main private val mainCoroutineContext: CoroutineContext,
    @ForScope(SingletonComponent::class) val singletonScopedServices: InjectSet<Scoped>,
) {
    private lateinit var rootScope: Scope
    fun onCreate(name: String) {
        rootScope = Scope.buildRootScope(name = name, context = mainCoroutineContext)
        rootScope.registerAllSorted(singletonScopedServices)
    }

    fun onTerminate() {
        rootScope.destroy()
    }
}
