package com.memfault.bort.scopes

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private const val COROUTINE_SCOPE_SCOPED_KEY = "CoroutineScopeScoped"

class CoroutineScopeScoped(
    private val mainCoroutineContext: CoroutineContext,
) : Scoped {

    val job = Job()

    override fun onEnterScope(scope: Scope) = Unit

    override fun onExitScope() = job.cancel()

    fun createChild(coroutineContext: CoroutineContext? = null): CoroutineScope =
        CoroutineScope(job + mainCoroutineContext + (coroutineContext ?: EmptyCoroutineContext))
}

fun Scope.coroutineScope(
    coroutineContext: CoroutineContext? = null,
): CoroutineScope {
    val result = checkNotNull(getService<CoroutineScopeScoped>(COROUTINE_SCOPE_SCOPED_KEY)) {
        "Could not find service for $COROUTINE_SCOPE_SCOPED_KEY in $name."
    }

    check(result.job.isActive) {
        "Expected ${result.job[CoroutineName]?.name} to be active."
    }

    return result.createChild(coroutineContext)
}

fun Scope.Builder.addCoroutineScopeService(coroutineScopeScoped: CoroutineScopeScoped) {
    addService(COROUTINE_SCOPE_SCOPED_KEY, coroutineScopeScoped)
}
