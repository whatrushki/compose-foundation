package app.what.foundation.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

/**
 * Ergonomic click modifier without legacy composed {} overhead.
 */
fun Modifier.bclick(enabled: Boolean = true, block: (() -> Unit)?): Modifier {
    return if (block != null && enabled) {
        this.clickable(
            enabled = true,
            onClick = block
        )
    } else {
        this
    }
}