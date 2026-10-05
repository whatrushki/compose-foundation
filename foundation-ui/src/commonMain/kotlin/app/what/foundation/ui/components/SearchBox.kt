package app.what.foundation.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

@Composable
fun SearchBox(
    query: String,
    setQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
    debounce: Long = 400L
) {
    var text by remember { mutableStateOf(query) }

    LaunchedEffect(query) {
        if (text != query) {
            text = query
        }
    }

    LaunchedEffect(text) {
        if (text != query) {
            if (debounce > 0) delay(debounce)
            setQuery(text)
        }
    }

    TextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier
            .focusable(true)
            .fillMaxWidth()
            .then(modifier),
        singleLine = true,
        placeholder = { Text("Поиск...") },
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            errorIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                contentDescription = "search"
            )
        }
    )
}