package app.what.foundation.ui.controllers

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun rememberSheetController(): SheetController = LocalSheetController.current

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSheetHostController(
    start: @Composable () -> Unit = {},
    scope: CoroutineScope = rememberCoroutineScope()
): SheetController {
    var sheetState by remember { mutableStateOf<SheetState?>(null) }

    return remember {
        object : SheetController {
            override var full by mutableStateOf(true)
            override var content by mutableStateOf(start)
            override var cancellable by mutableStateOf(true)
            override var opened by mutableStateOf(false)

            override fun setSheetState(state: SheetState) {
                sheetState = state
            }

            override fun open(
                full: Boolean,
                cancellable: Boolean,
                content: @Composable () -> Unit
            ) {
                this.content = content
                this.cancellable = cancellable
                this.full = full
                open(full)
            }

            override fun open(full: Boolean) {
                this.full = full
                opened = true
                scope.launch {
                    try {
                        if (sheetState?.isVisible == true) {
                            if (full) sheetState?.expand() else sheetState?.show()
                        }
                    } catch (_: Exception) {}
                }
            }

            override fun close() {
                opened = false
            }

            override fun animateClose() {
                scope.launch { sheetState?.hide() }
                    .invokeOnCompletion { close() }
            }
        }
    }
}

val LocalSheetController = compositionLocalOf<SheetController> { error("SheetController не предоставлен в дереве компонентов") }

interface SheetController {
    var full: Boolean
    val opened: Boolean
    var cancellable: Boolean
    var content: @Composable () -> Unit

    fun open(full: Boolean = true)
    fun open(
        full: Boolean = true,
        cancellable: Boolean = true,
        content: @Composable () -> Unit
    )

    fun close()
    fun animateClose()

    @OptIn(ExperimentalMaterial3Api::class)
    fun setSheetState(state: SheetState)
}