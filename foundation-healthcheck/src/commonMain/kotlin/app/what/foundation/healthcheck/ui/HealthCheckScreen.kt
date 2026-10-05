package app.what.foundation.healthcheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.what.foundation.healthcheck.model.*
import app.what.foundation.healthcheck.registry.HealthRegistry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthCheckScreen(
    registry: HealthRegistry,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val isRunning by registry.isRunning.collectAsState()
    val report by registry.lastReport.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Диагностика системы") },
                actions = {
                    Button(
                        onClick = { coroutineScope.launch { registry.runAll() } },
                        enabled = !isRunning,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(if (isRunning) "Проверка..." else "Запустить тест")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (isRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }

            val currentReport = report
            if (currentReport == null && !isRunning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Нажмите «Запустить тест» для проверки компонентов приложения",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (currentReport != null) {
                // Summary card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (currentReport.overallStatus) {
                            HealthStatus.PASSED -> MaterialTheme.colorScheme.primaryContainer
                            HealthStatus.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
                            HealthStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                            HealthStatus.SKIPPED -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = when (currentReport.overallStatus) {
                                HealthStatus.PASSED -> "Все системы работают стабильно"
                                HealthStatus.WARNING -> "Обнаружены предупреждения"
                                HealthStatus.FAILED -> "Обнаружены критические сбои"
                                HealthStatus.SKIPPED -> "Проверка пропущена"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Успешно: ${currentReport.passedCount} | Предупреждений: ${currentReport.warningCount} | Ошибок: ${currentReport.failedCount}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentReport.items) { item ->
                        CheckItemCard(item = item)
                    }
                }
            }
        }
    }
}

@Composable
fun CheckItemCard(item: CheckItemResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusColor = when (item.result.status) {
                HealthStatus.PASSED -> Color(0xFF2E7D32)
                HealthStatus.WARNING -> Color(0xFFF57F17)
                HealthStatus.FAILED -> MaterialTheme.colorScheme.error
                HealthStatus.SKIPPED -> Color.Gray
            }
            val statusSymbol = when (item.result.status) {
                HealthStatus.PASSED -> "✓"
                HealthStatus.WARNING -> "⚠"
                HealthStatus.FAILED -> "✗"
                HealthStatus.SKIPPED -> "—"
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = statusSymbol, color = statusColor, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                item.result.message?.let { msg ->
                    Text(text = msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
