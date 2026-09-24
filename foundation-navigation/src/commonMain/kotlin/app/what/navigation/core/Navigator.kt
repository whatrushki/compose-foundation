package app.what.navigation.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController

val LocalNavController = compositionLocalOf<Navigator?> { null }

data class Navigator(
    val parent: Navigator?,
    val c: NavHostController
) {
    /**
     * Readable accessor for [c].
     */
    val controller: NavHostController get() = c
}

@Composable
fun rememberHostNavigator(
    parent: Navigator? = LocalNavController.current
): Navigator {
    val navController = rememberNavController()
    return remember(parent, navController) { Navigator(parent, navController) }
}

@Composable
fun rememberNavigator(): Navigator =
    LocalNavController.current ?: error("No Navigator found in CompositionLocal. Ensure your UI hierarchy is hosted within NavigationHost(...)")

@Composable
fun rememberNavigator(level: Int): Navigator {
    require(level >= 1) { "Navigation hierarchy level must be >= 1, but was $level" }
    var current = LocalNavController.current ?: error("No root Navigator found in CompositionLocal")

    for (i in 1 until level) {
        current = current.parent ?: error("Requested navigator level $level exceeds navigation hierarchy depth ($i level(s) available)")
    }

    return current
}
