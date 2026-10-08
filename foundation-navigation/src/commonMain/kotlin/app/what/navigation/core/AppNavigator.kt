package app.what.navigation.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Обертка для прямого инлайн-рендеринга Composable без предварительной регистрации роута.
 */
data class InlineNavEntry(
    val id: Long,
    val full: Boolean = false,
    val content: @Composable () -> Unit
)

/**
 * Единый контроллер навигации в парадигме Navigation 3.
 * Управляет реактивным стеком экранов [backStack] и иерархией [parent].
 */
class AppNavigator(
    initial: Any? = null,
    val parent: AppNavigator? = null,
    private val onDismissRequest: (() -> Unit)? = null
) {
    private var nextInlineId = 0L

    val backStack: SnapshotStateList<Any> = mutableStateListOf<Any>().apply {
        if (initial != null) add(initial)
    }

    /**
     * Флаг направления перехода (push вперед или pop назад) для анимаций.
     */
    var isForward: Boolean by mutableStateOf(true)
        internal set

    val current: Any?
        get() = backStack.lastOrNull()

    val canGoBack: Boolean
        get() = backStack.size > 1

    val isEmpty: Boolean
        get() = backStack.isEmpty()

    val isNotEmpty: Boolean
        get() = backStack.isNotEmpty()

    val size: Int
        get() = backStack.size

    /**
     * Помещает маршрут [destination] на вершину стека.
     */
    fun push(destination: Any) {
        isForward = true
        backStack.add(destination)
    }

    /**
     * Открывает Composable напрямую без регистрации роута.
     * Удобно для диалогов, шторок и временных экранов.
     */
    fun open(full: Boolean = false, content: @Composable () -> Unit): AppNavigator {
        val entry = InlineNavEntry(
            id = ++nextInlineId,
            full = full,
            content = content
        )
        push(entry)
        return this
    }

    /**
     * Извлекает верхний экран из стека.
     * Если стек пуст и задан [onDismissRequest], вызывает его.
     */
    fun pop(): Boolean {
        if (backStack.isNotEmpty()) {
            isForward = false
            backStack.removeAt(backStack.lastIndex)
            if (backStack.isEmpty()) {
                onDismissRequest?.invoke()
            }
            return true
        }
        return false
    }

    /**
     * Заменяет текущий верхний экран на новый [destination].
     */
    fun replace(destination: Any) {
        isForward = true
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = destination
        } else {
            backStack.add(destination)
        }
    }

    /**
     * Очищает весь стек до первого экрана или до пула.
     */
    fun popToRoot() {
        if (backStack.size > 1) {
            isForward = false
            val first = backStack.first()
            backStack.clear()
            backStack.add(first)
        }
    }

    /**
     * Полностью очищает стек и закрывает хост.
     */
    fun clear() {
        isForward = false
        backStack.clear()
        onDismissRequest?.invoke()
    }
}
