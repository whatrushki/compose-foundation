package app.what.foundation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

@Composable
fun <T : Any?> useState(initialValue: T): MutableState<T> {
    val state = remember { mutableStateOf(initialValue) }
    return state
}

@Composable
fun <T> useState(initialValue: T, key: Any? = Unit): MutableState<T> {
    return remember(key) { mutableStateOf(initialValue) }
}

@Composable
fun <T> useState(initialValue: T, vararg keys: Any?): MutableState<T> {
    return remember(*keys) { mutableStateOf(initialValue) }
}

@Composable
fun <T : Any?> useStateList(): SnapshotStateList<T> {
    val state = remember { mutableStateListOf<T>() }
    return state
}

@Composable
fun <T : Any?> useStateList(vararg initialValue: T): SnapshotStateList<T> {
    val state = remember { mutableStateListOf(*initialValue) }
    return state
}

@Composable
fun <T> useSave(initialValue: T, vararg inputs: Any?): MutableState<T> {
    return rememberSaveable(inputs = inputs) {
        mutableStateOf(initialValue)
    }
}

/**
 * Replaces the old polling implementation with genuine reaction to [value] changes.
 * @param value The value to observe for changes.
 * @param skipInitial If true, the [block] will not run during initial composition.
 * @param block Suspend lambda executed whenever [value] changes.
 */
@Composable
fun <T> useChange(
    value: T,
    skipInitial: Boolean = false,
    block: suspend (T) -> Unit
) {
    val isInitial = remember { mutableStateOf(true) }
    val currentBlock by rememberUpdatedState(block)

    LaunchedEffect(value) {
        if (skipInitial && isInitial.value) {
            isInitial.value = false
            return@LaunchedEffect
        }
        isInitial.value = false
        currentBlock(value)
    }
}

/**
 * Executes [block] when any of [keys] change.
 */
@Composable
fun useChange(
    vararg keys: Any?,
    skipInitial: Boolean = false,
    block: suspend () -> Unit
) {
    val isInitial = remember { mutableStateOf(true) }
    val currentBlock by rememberUpdatedState(block)

    LaunchedEffect(*keys) {
        if (skipInitial && isInitial.value) {
            isInitial.value = false
            return@LaunchedEffect
        }
        isInitial.value = false
        currentBlock()
    }
}

/**
 * Periodically executes [block] while application is in foreground.
 */
@Composable
fun <T> useInterval(
    initialValue: T,
    delaySeconds: Long = 10L,
    block: (T) -> T
): State<T> {
    val state = remember { mutableStateOf(initialValue) }
    val isAppInForeground by rememberIsAppInForeground()

    val currentBlock by rememberUpdatedState(block)

    LaunchedEffect(isAppInForeground, delaySeconds) {
        while (isAppInForeground) {
            delay(delaySeconds * 1000L)
            state.value = currentBlock(state.value)
        }
    }

    return state
}

@Composable
fun rememberIsAppInForeground(): State<Boolean> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.value = true
            else if (event == Lifecycle.Event.ON_PAUSE) state.value = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}