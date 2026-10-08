package app.what.navigation.core

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

val LocalIsWideScreen = compositionLocalOf { false }

/**
 * Адаптивный контейнер Sheet-навигации:
 * - На узких экранах (смартфон): отображает NavSheetHost (ModalBottomSheet).
 * - На широких экранах (планшет, десктоп): отображает NavSideSheetHost (модальная боковая панель справа).
 */
@Composable
fun ProvideAdaptiveSheet(
    isWideScreen: Boolean,
    navigator: AppNavigator = rememberAppNavigator(),
    content: @Composable () -> Unit
) {
    ProvideGlobalNavigation(
        isWideScreen = isWideScreen,
        sheetNavigator = navigator,
        content = content
    )
}

/**
 * Контейнер Side Sheet: модальная боковая шторка справа с затемнением фона и поддержкой навигационного стека.
 */
@Composable
fun NavSideSheetHost(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    registry: NavRegistry? = null,
    onDismissRequest: (() -> Unit)? = null,
    content: (@Composable (Any) -> Unit)? = null
) {
    val isVisible = !navigator.isEmpty

    Box(modifier = Modifier.fillMaxSize().zIndex(100f)) {
        // Затемняющий оверлей (Scrim)
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        navigator.clear()
                        onDismissRequest?.invoke()
                    }
            )
        }

        // Выдвигающаяся справа панель
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = modifier
                    .fillMaxHeight()
                    .widthIn(min = 360.dp, max = 440.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
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
