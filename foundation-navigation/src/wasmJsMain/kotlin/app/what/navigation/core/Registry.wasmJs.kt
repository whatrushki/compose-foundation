package app.what.navigation.core

import kotlin.reflect.KClass

actual val screenFactories: MutableMap<KClass<*>, (Any) -> Any> = mutableMapOf()

actual fun <P : NavProvider, S : NavComponent<P>> instantiateScreen(screen: KClass<S>, provider: P): S {
    val factory = screenFactories[screen]
        ?: error("Screen factory not registered for ${screen.qualifiedName ?: screen.simpleName}. On Web (WasmJs), reflection is unsupported. Register your screen with a factory: register(${screen.simpleName}::class) { ${screen.simpleName}(it) }")
    @Suppress("UNCHECKED_CAST")
    return factory(provider) as S
}
