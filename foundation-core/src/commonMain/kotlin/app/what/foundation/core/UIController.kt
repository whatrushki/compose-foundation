package app.what.foundation.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class UIController<S : Any, A, E>(initialState: S) : ViewModel() {
    private val _viewStates = MutableStateFlow(initialState)
    val states: StateFlow<S> = _viewStates.asStateFlow()

    private val _viewActions = Channel<A>(Channel.BUFFERED)
    val actions: Flow<A> = _viewActions.receiveAsFlow()

    protected val viewState: S
        get() = _viewStates.value

    fun getState(): S = viewState

    @Composable
    fun collectStates(): State<S> = _viewStates.collectAsStateWithLifecycle()

    /**
     * Ergonomic way to consume single-time Actions safely according to the Lifecycle.
     */
    @Composable
    fun CollectAction(block: suspend (A) -> Unit) {
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                actions.collect { block(it) }
            }
        }
    }

    @Deprecated(
        message = "Use CollectAction { } for channel-based actions",
        replaceWith = ReplaceWith("CollectAction(block)")
    )
    @Composable
    fun collectActions(): State<A?> = actions.collectAsStateWithLifecycle(null)

    abstract fun obtainEvent(viewEvent: E)

    protected fun updateState(state: S) {
        _viewStates.value = state
    }

    protected fun updateState(reducer: S.() -> S) {
        _viewStates.update { it.reducer() }
    }

    /**
     * Emits a one-time Action into the buffered channel without race conditions.
     */
    protected fun emitAction(action: A) {
        viewModelScope.launch {
            _viewActions.send(action)
        }
    }

    protected fun setAction(action: A) {
        emitAction(action)
    }

    @Deprecated(
        message = "Manual action clearing is unnecessary when using Channels",
        replaceWith = ReplaceWith("")
    )
    fun clearAction() {
        // No-op for channel-based actions
    }
}