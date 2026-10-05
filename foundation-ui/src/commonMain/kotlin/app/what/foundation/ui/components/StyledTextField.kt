package app.what.foundation.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.delay

@Composable
fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    debounce: Long = 0,
    placeholder: String? = null,
    shape: Shape = CircleShape,
    options: KeyboardOptions = KeyboardOptions.Default,
    disabled: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    var text by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        if (text != value) {
            text = value
        }
    }

    LaunchedEffect(text) {
        if (text != value) {
            if (debounce > 0) delay(debounce)
            onValueChange(text)
        }
    }

    OutlinedTextField(
        enabled = !disabled,
        modifier = modifier,
        value = text,
        onValueChange = { text = it },
        placeholder = placeholder?.let {
            {
                Text(
                    it,
                    style = typography.bodySmall,
                    color = colorScheme.secondary
                )
            }
        },
        keyboardOptions = options,
        leadingIcon = leading,
        trailingIcon = trailing,
        singleLine = true,
        shape = shape,
        textStyle = typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colorScheme.surfaceContainer,
            unfocusedContainerColor = colorScheme.surfaceContainer,
            disabledContainerColor = colorScheme.surfaceContainer.copy(alpha = 0.5f),
            focusedBorderColor = colorScheme.primary,
            unfocusedBorderColor = colorScheme.outlineVariant
        ),
    )
}