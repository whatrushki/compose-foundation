package app.what.navigation.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 1. Экранный контейнер (Screen): обычный экран или панель.
 */
@Composable
fun NavScreenHost(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    registry: NavRegistry? = null,
    enableBackHandler: Boolean = true,
    content: (@Composable (Any) -> Unit)? = null
) {
    NavStackHost(
        navigator = navigator,
        modifier = modifier,
        registry = registry,
        enableBackHandler = enableBackHandler,
        content = content
    )
}

/**
 * 2. Контейнер Bottom Sheet: модальная шторка с поддержкой стека переходов внутри неё.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavSheetHost(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    full: Boolean = false,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = full),
    registry: NavRegistry? = null,
    onDismissRequest: (() -> Unit)? = null,
    content: (@Composable (Any) -> Unit)? = null
) {
    CompositionLocalProvider(
        LocalSheetNavigator provides navigator
    ) {
        if (!navigator.isEmpty) {
            val isFull = full || (navigator.current as? InlineNavEntry)?.full == true

            ModalBottomSheet(
                onDismissRequest = {
                    navigator.clear()
                    onDismissRequest?.invoke()
                },
                sheetState = sheetState,
                modifier = modifier
            ) {
                Box(
                    modifier = if (isFull) Modifier.fillMaxWidth().fillMaxHeight(0.92f)
                    else Modifier.fillMaxWidth()
                ) {
                    NavStackHost(
                        navigator = navigator,
                        modifier = Modifier.fillMaxSize(),
                        registry = registry,
                        enableBackHandler = true,
                        content = content
                    )
                }
            }
        }
    }
}

/**
 * 3. Контейнер Dialog: модальное окно (алерт или fullscreen dialog) с поддержкой стека переходов.
 */
@Composable
fun NavDialogHost(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    full: Boolean = false,
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = !full,
        dismissOnBackPress = true,
        dismissOnClickOutside = true
    ),
    registry: NavRegistry? = null,
    onDismissRequest: (() -> Unit)? = null,
    content: (@Composable (Any) -> Unit)? = null
) {
    CompositionLocalProvider(
        LocalDialogNavigator provides navigator
    ) {
        if (!navigator.isEmpty) {
            val isFull = full || (navigator.current as? InlineNavEntry)?.full == true

            Dialog(
                onDismissRequest = {
                    navigator.clear()
                    onDismissRequest?.invoke()
                },
                properties = if (isFull) DialogProperties(usePlatformDefaultWidth = false) else properties
            ) {
                Surface(
                    shape = if (isFull) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = if (isFull) modifier.fillMaxSize().padding(16.dp)
                    else modifier.fillMaxWidth()
                ) {
                    NavStackHost(
                        navigator = navigator,
                        modifier = Modifier.fillMaxSize(),
                        registry = registry,
                        enableBackHandler = true,
                        content = content
                    )
                }
            }
        }
    }
}

/**
 * 4. Контейнер Drawer: боковая панель с поддержкой навигационного стека.
 */
@Composable
fun NavDrawerHost(
    navigator: AppNavigator,
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
    modifier: Modifier = Modifier,
    registry: NavRegistry? = null,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalDrawerNavigator provides navigator
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(modifier = modifier) {
                    NavStackHost(
                        navigator = navigator,
                        modifier = Modifier.fillMaxSize(),
                        registry = registry,
                        enableBackHandler = true
                    )
                }
            },
            content = content
        )
    }
}
