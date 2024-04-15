package com.memfault.bort.scopes

import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * The [Scoped] interface allows for an Actor-like pattern to be built into an application, by allowing isolated
 * services to be dynamically spun up to execute code asynchronously, often by listening to an async channel or Flow.
 *
 * It can also be used to dynamically run code at startup that can be performed asynchronously, without dependencies
 * on each other, and not necessarily in order. For example, logging some debug information doesn't absolutely need
 * to happen before or after anything else, so [Scoped] can be used in that situation, but setting the project
 * sysprop key probably should happen synchronously, so [Scoped] shouldn't be used in that situation.
 *
 * !!! IMPORTANT !!!
 * It is important to note that by default, Android can tear down the application at any point in time, unless the
 * Application is foregrounded (not applicable to Bort), or the Application is handling an Intent (5s max), or
 * the Application is executing a WorkManager task (10m max). If an action **must** be executed, then you **must**
 * ensure separately that the Application process can't die.
 *
 * For example, if the user sends an Intent to disable Bort, then Bort also must disable the structuredlogd service,
 * and it'd be bad if Bort was disabled, but the process died before it could send the signal to disable the
 * structuredlogd service. So the disabling should happen synchronously while handling the Intent, within 5s.
 * !!! IMPORTANT !!!
 *
 * Usage:
 *
 * @ContributesMultibinding(SingletonComponent::class)
 * class Service
 * @Inject constructor() : Scoped {
 *     override fun onEnterScope(scope: Scope) {
 *         scope.coroutineScope().launch { ... }
 *     }
 *
 *     override fun onExitScope() = Unit
 * }
 */
interface Scoped {

    fun priority(): ScopedPriority = ScopedPriority.NONE

    fun onEnterScope(scope: Scope)

    fun onExitScope()

    /**
     * By default, [Scoped] implementations have no priority (NONE), so they will be ordered randomly
     * (alphabetically for consistency). To add a priority, add a new [ScopedPriority] enum for the type and
     * update the tests to make sure the new priority executes in the correct order. Enums higher in the list
     * will be executed first.
     *
     * Note that coroutines are executed asynchronously depending on the dispatcher used, so unless the work is
     * all done on the same single threaded dispatcher, it might not actually be performed in order.
     */
    enum class ScopedPriority {
        STRICT_MODE,
        NONE,
    }

    companion object {
        fun Scope.registerAllSorted(services: Set<Scoped>) {
            val orderedServices = services.sortedWith(
                compareBy({ it.priority().ordinal }, { it.javaClass.name }),
            )
            orderedServices.forEach {
                Logger.test("$name register: ${it.javaClass}")
                register(it)
            }
        }
    }
}

@ContributesTo(SingletonComponent::class)
@Module
abstract class Module {
    @Multibinds
    @ForScope(SingletonComponent::class)
    abstract fun singletonScoped(): Set<Scoped>
}
