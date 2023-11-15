package com.memfault.bort.ota.lib

import com.memfault.bort.shared.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow.SUSPEND
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * The updater keeps the update state and events, delegating all action handling to the passed handler.
 */
@Singleton
class Updater @Inject constructor(
    // Provider to break dependency cycle (which the compiler didn't catch = stackoverflow at runtime).
    private val actionHandler: Provider<UpdateActionHandler>,
    private val stateStore: StateStore,
) {
    private val _updateState = MutableSharedFlow<State>(
        replay = 1,
        extraBufferCapacity = 0,
        onBufferOverflow = SUSPEND,
    ).also {
        val initialState = stateStore.read()
        Logger.d("initialState from store: $initialState")
        it.tryEmit(initialState ?: State.Idle)
    }
    val updateState: Flow<State> = _updateState.distinctUntilChanged()

    fun badCurrentUpdateState(): State = _updateState.replayCache.last()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events

    // Do all state machine operations on main thread, avoiding race conditions between threads causing bugs.
    suspend fun perform(action: Action) = withContext(Dispatchers.Main) {
        val currentState = badCurrentUpdateState()
        Logger.d("perform: $action (in state: $currentState)")
        actionHandler.get().handle(
            state = currentState,
            action = action,
        )
    }

    // Do all state machine operations on main thread, avoiding race conditions between threads causing bugs.
    suspend fun setState(state: State) = withContext(Dispatchers.Main) {
        stateStore.store(state)
        _updateState.emit(state)
    }

    suspend fun triggerEvent(event: Event) {
        _events.emit(event)
    }
}
