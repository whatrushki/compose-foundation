package app.what.navigation.core

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

actual val screenFactories: MutableMap<KClass<*>, (Any) -> Any> = ConcurrentHashMap()

actual fun <P : NavProvider, S : NavComponent<P>> instantiateScreen(screen: KClass<S>, provider: P): S {
    val factory = screenFactories[screen]
    if (factory != null) {
        @Suppress("UNCHECKED_CAST")
        return factory(provider) as S
    }
    val singleParamCtor = screen.constructors.firstOrNull { it.parameters.size == 1 }
    if (singleParamCtor != null) {
        @Suppress("UNCHECKED_CAST")
        return singleParamCtor.call(provider)
    }
    val noArgCtor = screen.constructors.firstOrNull { it.parameters.isEmpty() }
    if (noArgCtor != null) {
        @Suppress("UNCHECKED_CAST")
        return noArgCtor.call()
    }
    return screen.constructors.firstOrNull()?.call(provider)
        ?: error("Screen factory not registered for ${screen.qualifiedName ?: screen.simpleName}, and reflection-based instantiation failed. Provide a factory in register(Screen::class) { Screen(it) }")
}
