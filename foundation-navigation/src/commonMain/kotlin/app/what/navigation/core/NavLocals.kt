package app.what.navigation.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

val LocalNavigator: ProvidableCompositionLocal<AppNavigator?> = staticCompositionLocalOf { null }
val LocalSheetNavigator: ProvidableCompositionLocal<AppNavigator?> = staticCompositionLocalOf { null }
val LocalDialogNavigator: ProvidableCompositionLocal<AppNavigator?> = staticCompositionLocalOf { null }
val LocalDrawerNavigator: ProvidableCompositionLocal<AppNavigator?> = staticCompositionLocalOf { null }

/**
 * Создает и сохраняет новый экземпляр [AppNavigator].
 * По умолчанию автоматически связывается с родительским [LocalNavigator.current].
 */
@Composable
fun rememberAppNavigator(
    initial: Any? = null,
    parent: AppNavigator? = LocalNavigator.current,
    onDismissRequest: (() -> Unit)? = null
): AppNavigator {
    return remember { AppNavigator(initial = initial, parent = parent, onDismissRequest = onDismissRequest) }
}

/**
 * Возвращает [AppNavigator] из текущего контекста Compose-дерева.
 * Если [level] > 0, поднимается на указанное количество уровней вверх по цепочке [AppNavigator.parent].
 */
@Composable
fun rememberAppNavigator(level: Int): AppNavigator {
    val current = LocalNavigator.current ?: rememberAppNavigator()
    if (level <= 0) return current

    var target: AppNavigator = current
    for (i in 0 until level) {
        val parent = target.parent ?: break
        target = parent
    }
    return target
}

/**
 * Возвращает самый верхний (корневой) [AppNavigator] приложения.
 * Безопасно поднимается по цепочке родителей, гарантируя доступ к корню независимо от глубины вложенности.
 */
@Composable
fun rememberRootAppNavigator(): AppNavigator {
    val current = LocalNavigator.current ?: rememberAppNavigator()
    var root = current
    while (true) {
        val p = root.parent ?: break
        root = p
    }
    return root
}

/**
 * Возвращает навигатор Bottom Sheet / Side Sheet из текущего окружения, либо создает локальный.
 */
@Composable
fun rememberSheetNavigator(): AppNavigator {
    val fromContext = LocalSheetNavigator.current
    if (fromContext != null) return fromContext
    return rememberAppNavigator()
}

/**
 * Возвращает навигатор диалоговых окон из текущего окружения, либо создает локальный.
 */
@Composable
fun rememberDialogNavigator(): AppNavigator {
    val fromContext = LocalDialogNavigator.current
    if (fromContext != null) return fromContext
    return rememberAppNavigator()
}

/**
 * Возвращает навигатор боковой панели (Drawer) из текущего окружения, либо создает локальный.
 */
@Composable
fun rememberDrawerNavigator(): AppNavigator {
    val fromContext = LocalDrawerNavigator.current
    if (fromContext != null) return fromContext
    return rememberAppNavigator()
}
