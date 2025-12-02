package com.memfault.bort.scopes

import com.memfault.bort.scopes.Scope.Builder
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

fun Scope.Companion.buildTestScope(
    name: String = "test",
    context: CoroutineContext?,
    builder: (Builder.() -> Unit)? = null,
): Scope {
    var coroutineContext = SupervisorJob() + CoroutineName(name)
    if (context != null) {
        coroutineContext += context
    }

    return buildRootScope(name = name, context = coroutineContext, builder = builder)
}
