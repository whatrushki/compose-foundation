package app.what.navigation.core

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import app.what.foundation.ui.PlatformBackHandler

/**
 * Базовый рендерер стека экранов.
 * Отвечает за:
 * 1. Сохранение состояния каждого экрана через [rememberSaveableStateHolder].
 * 2. Анимацию перехода между экранами при push/pop.
 * 3. Перехват системного жеста/кнопки «Назад» через [PlatformBackHandler].
 * 4. Предоставление текущего [navigator] в [LocalNavigator] для дочерних компонентов.
 */
@Composable
fun NavStackHost(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    registry: NavRegistry? = null,
    enableBackHandler: Boolean = true,
    content: (@Composable (Any) -> Unit)? = null
) {
    if (enableBackHandler) {
        PlatformBackHandler(enabled = !navigator.isEmpty) {
            navigator.pop()
        }
    }

    val saveableStateHolder = rememberSaveableStateHolder()
    val current = navigator.current

    CompositionLocalProvider(
        LocalNavigator provides navigator
    ) {
        Box(modifier = modifier) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    if (navigator.isForward) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width / 3 } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width / 3 } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut()
                        )
                    }
                },
                contentKey = { target ->
                    when (target) {
                        is InlineNavEntry -> target.id
                        null -> "empty"
                        else -> target
                    }
                },
                label = "NavStackTransition",
                modifier = Modifier.fillMaxSize()
            ) { targetDestination ->
                if (targetDestination != null) {
                    val key = if (targetDestination is InlineNavEntry) targetDestination.id else targetDestination
                    saveableStateHolder.SaveableStateProvider(key) {
                        when (targetDestination) {
                            is InlineNavEntry -> {
                                targetDestination.content()
                            }
                            else -> {
                                val renderedByRegistry = registry?.Render(targetDestination) ?: false
                                if (!renderedByRegistry && content != null) {
                                    content(targetDestination)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
