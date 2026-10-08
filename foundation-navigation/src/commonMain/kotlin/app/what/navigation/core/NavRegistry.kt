package app.what.navigation.core

import androidx.compose.runtime.Composable
import kotlin.reflect.KClass

typealias NavContent<T> = @Composable (T) -> Unit

/**
 * Реестр сопоставления типов маршрутов с Composable-контентом.
 */
class NavRegistry internal constructor(
    private val routes: Map<KClass<*>, NavContent<*>>
) {
    @Suppress("UNCHECKED_CAST")
    @Composable
    fun Render(destination: Any): Boolean {
        val content = routes[destination::class] as? NavContent<Any> ?: return false
        content(destination)
        return true
    }

    operator fun plus(other: NavRegistry): NavRegistry {
        return NavRegistry(this.routes + other.routes)
    }
}

class NavRegistryBuilder {
    @PublishedApi
    internal val routes = mutableMapOf<KClass<*>, NavContent<*>>()

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> entry(noinline content: @Composable (T) -> Unit) {
        routes[T::class] = content as NavContent<*>
    }

    fun build(): NavRegistry = NavRegistry(routes)
}

fun navRegistry(builder: NavRegistryBuilder.() -> Unit): NavRegistry {
    val b = NavRegistryBuilder()
    b.builder()
    return b.build()
}
