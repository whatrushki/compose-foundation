package app.what.navigation.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
     * Помещает Composable-контент напрямую в стек (для алертов, диалогов и временных экранов).
     */
    fun pushComposable(full: Boolean = false, content: @Composable () -> Unit): Long {
        val id = ++nextInlineId
        isForward = true
        backStack.add(InlineNavEntry(id = id, full = full, content = content))
        return id
    }

    /**
     * Извлекает верхний экран из стека.
     * Если оставался последний экран или стек пуст, вызывает [onDismissRequest].
     * @return true, если экран был извлечен или контейнер закрыт.
     */
    fun pop(): Boolean {
        isForward = false
        if (canGoBack) {
            backStack.removeAt(backStack.lastIndex)
            return true
        } else if (backStack.isNotEmpty()) {
            backStack.clear()
            onDismissRequest?.invoke()
            return true
        }
        return false
    }

    /**
     * Заменяет текущую вершину стека на [destination].
     */
    fun replace(destination: Any) {
        isForward = true
        if (backStack.isNotEmpty()) {
            backStack.removeAt(backStack.lastIndex)
        }
        backStack.add(destination)
    }

    /**
     * Очищает весь стек и закрывает контейнер при необходимости.
     */
    fun clear() {
        isForward = false
        backStack.clear()
        onDismissRequest?.invoke()
    }

    /**
     * Возвращается к первому экрану стека.
     */
    fun popToRoot() {
        isForward = false
        if (backStack.size > 1) {
            val first = backStack.first()
            backStack.clear()
            backStack.add(first)
        }
    }

    /**
     * Открывает Composable-контент.
     * Если [inStack] = false, предварительно очищает стек, начиная новый флоу.
     */
    fun open(full: Boolean = false, inStack: Boolean = false, content: @Composable () -> Unit): Long {
        if (!inStack) {
            backStack.clear()
        }
        return pushComposable(full = full, content = content)
    }

    /**
     * Помещает Composable-контент поверх текущего экрана в стеке.
     */
    fun push(full: Boolean = false, content: @Composable () -> Unit): Long =
        open(full = full, inStack = true, content = content)

    fun close() = clear()

    fun animateClose() = clear()

    fun back(): Boolean = pop()
}
