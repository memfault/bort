package com.memfault.bort.scopes

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

interface Scope {
    val name: String
    val parent: Scope?

    fun register(scoped: Scoped)
    fun destroy()

    fun <T : Any> getService(key: String): T?

    class Builder internal constructor(
        private val name: String,
        private val parent: Scope?,
    ) {
        private val services = mutableMapOf<String, Any>()

        fun addService(
            key: String,
            service: Any,
        ) {
            services[key] = service
        }

        internal fun build(): Scope = ScopeImpl(name, parent, services)
    }

    private class ScopeImpl(
        override val name: String,
        override val parent: Scope?,
        private val services: MutableMap<String, Any>,
    ) : Scope {
        private var destroyed = AtomicBoolean(false)
        private val scopedServices = mutableSetOf<Scoped>()

        override fun register(scoped: Scoped) {
            if (destroyed.get()) return

            if (scopedServices.add(scoped)) {
                scoped.onEnterScope(this)
            }
        }

        override fun destroy() {
            destroyed.compareAndSet(false, true)
            scopedServices.forEach(Scoped::onExitScope)
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> getService(key: String): T? = (services[key] as? T) ?: parent?.getService(key)
    }

    companion object {
        fun buildRootScope(
            name: String = "root",
            context: CoroutineContext,
            builder: (Builder.() -> Unit)? = null,
        ): Scope {
            val rootBuilder = Builder(name = name, parent = null)

            val coroutineScopeScoped = CoroutineScopeScoped(context)
            rootBuilder.addCoroutineScopeService(coroutineScopeScoped)

            builder?.invoke(rootBuilder)

            return rootBuilder.build()
                .apply { register(coroutineScopeScoped) }
        }
    }
}
