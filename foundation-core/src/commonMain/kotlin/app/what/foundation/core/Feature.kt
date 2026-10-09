package app.what.foundation.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

typealias Listener<T> = (T) -> Unit

abstract class Feature<Ctrl : UIController<*, *, Event>, Event : Any> : UIComponent {
    protected abstract val controller: Ctrl
    protected val listener: Listener<Event> by lazy { controller::obtainEvent }

    @Composable
    override fun content(modifier: Modifier) {}
}