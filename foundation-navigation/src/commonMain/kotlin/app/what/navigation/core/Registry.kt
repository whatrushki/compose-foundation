package app.what.navigation.core

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlin.reflect.KClass

expect val screenFactories: MutableMap<KClass<*>, (Any) -> Any>

fun <P : NavProvider, S : NavComponent<P>> registerScreenFactory(screen: KClass<S>, factory: (P) -> S) {
    @Suppress("UNCHECKED_CAST")
    screenFactories[screen] = { provider -> factory(provider as P) }
}

inline fun <reified P : NavProvider, S : NavComponent<P>> registerScreen(
    screen: KClass<S>,
    noinline factory: (P) -> S
) {
    registerScreenFactory(screen, factory)
}

expect fun <P : NavProvider, S : NavComponent<P>> instantiateScreen(screen: KClass<S>, provider: P): S

inline fun <reified P : NavProvider, S : NavComponent<P>> NavGraphBuilder.register(screen: KClass<S>) {
    composable<P> {
        val provider = it.toRoute<P>()
        val s = androidx.compose.runtime.remember(provider) { instantiateScreen(screen, provider) }
        s.content(androidx.compose.ui.Modifier)
    }
}

inline fun <reified P : NavProvider, S : NavComponent<P>> NavGraphBuilder.register(
    screen: KClass<S>,
    noinline factory: (P) -> S
) {
    registerScreenFactory(screen, factory)
    composable<P> {
        val provider = it.toRoute<P>()
        val s = androidx.compose.runtime.remember(provider) { factory(provider) }
        s.content(androidx.compose.ui.Modifier)
    }
}

typealias Registry = NavGraphBuilder.() -> Unit
