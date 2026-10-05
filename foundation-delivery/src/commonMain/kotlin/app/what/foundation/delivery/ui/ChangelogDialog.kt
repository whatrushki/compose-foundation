package app.what.foundation.delivery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.what.foundation.delivery.model.ChangelogRelease

@Composable
fun ChangelogDialog(
    releases: List<ChangelogRelease>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Что нового") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(releases) { release ->
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Версия ${release.versionName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = release.releaseDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = release.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val newItems = release.categories["new"].orEmpty()
                        if (newItems.isNotEmpty()) {
                            Text(text = "Новое:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            newItems.forEach { item ->
                                Text(text = "• $item", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        val improvedItems = release.categories["improved"].orEmpty()
                        if (improvedItems.isNotEmpty()) {
                            Text(text = "Улучшения:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            improvedItems.forEach { item ->
                                Text(text = "• $item", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        val fixedItems = release.categories["fixed"].orEmpty()
                        if (fixedItems.isNotEmpty()) {
                            Text(text = "Исправления:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            fixedItems.forEach { item ->
                                Text(text = "• $item", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Понятно")
            }
        },
        modifier = modifier
    )
}
