package app.what.navigation.core

import app.what.foundation.core.UIComponent

/**
 * Interface representing a screen UI component tied to a specific [NavProvider].
 */
interface NavComponent<P : NavProvider> : UIComponent {
    val data: P
}
