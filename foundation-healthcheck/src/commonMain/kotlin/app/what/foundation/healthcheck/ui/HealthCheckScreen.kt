package app.what.foundation.healthcheck.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.foundation.healthcheck.model.*
import app.what.foundation.healthcheck.registry.HealthRegistry
import app.what.foundation.ui.Gap
import app.what.foundation.ui.bclick
import app.what.foundation.ui.icons.WHATIcons
import app.what.foundation.ui.icons.filled.Export
import app.what.foundation.ui.icons.filled.Run
import kotlinx.coroutines.launch

@Composable
fun HealthCheckScreen(
    registry: HealthRegistry,
    onExportJson: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val isRunning by registry.isRunning.collectAsState()
    val report by registry.lastReport.collectAsState()
    val itemsState by registry.itemsState.collectAsState()

    val displayItems = remember(itemsState, registry.registeredChecks) {
        if (itemsState.isNotEmpty()) itemsState
        else registry.registeredChecks.map {
            CheckItemResult(
                checkId = it.id,
                title = it.title,
                category = it.category,
                result = HealthResult.Pending
            )
        }
    }

    val passedCount = displayItems.count { it.result.status == HealthStatus.PASSED }
    val failedCount = displayItems.count { it.result.status == HealthStatus.FAILED }
    val warningCount = displayItems.count { it.result.status == HealthStatus.WARNING }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Шапка в общем стиле dev-панели
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Проверка компонентов",
                style = typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$passedCount/${displayItems.size} пройдено",
                    style = typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )

                Gap(4)

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            when {
                                isRunning -> colorScheme.primary
                                failedCount > 0 -> colorScheme.error
                                warningCount > 0 -> Color(0xFFF57F17)
                                else -> Color(0xFF2E7D32)
                            },
                            CircleShape
                        )
                )

                Text(
                    text = if (isRunning) "Тестирование..." else "Готово",
                    style = typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        // Панель действий
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Кнопка запуска тестов
            Button(
                onClick = { coroutineScope.launch { registry.runAll() } },
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.onPrimary
                    )
                    Gap(8)
                    Text("Проверка...", style = typography.labelMedium)
                } else {
                    Icon(
                        imageVector = WHATIcons.Run,
                        contentDescription = "Запуск",
                        modifier = Modifier.size(16.dp)
                    )
                    Gap(8)
                    Text("Прогнать тесты", style = typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            val currentReport = report
            if (currentReport != null) {
                IconButton(
                    onClick = { onExportJson(currentReport.toJson()) },
                    modifier = Modifier
                        .size(44.dp)
                        .background(colorScheme.surfaceContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = WHATIcons.Export,
                        contentDescription = "Экспорт отчёта",
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Gap(8)

        if (isRunning) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = colorScheme.primary,
                trackColor = colorScheme.primaryContainer.copy(alpha = 0.4f)
            )
        }

        Gap(4)

        // Список всех проверок (всегда виден в реальном времени)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(displayItems, key = { it.checkId }) { item ->
                CheckItemCard(item = item)
            }
        }
    }
}

@Composable
fun CheckItemCard(item: CheckItemResult) {
    var expanded by remember { mutableStateOf(false) }

    val status = item.result.status
    val isRunning = status == HealthStatus.RUNNING
    val isFailed = status == HealthStatus.FAILED
    val isWarning = status == HealthStatus.WARNING

    val statusColor = when (status) {
        HealthStatus.PASSED -> Color(0xFF2E7D32)
        HealthStatus.WARNING -> Color(0xFFF57F17)
        HealthStatus.FAILED -> colorScheme.error
        HealthStatus.RUNNING -> colorScheme.primary
        HealthStatus.PENDING -> colorScheme.outline
        HealthStatus.SKIPPED -> colorScheme.outline
    }

    val statusSymbol = when (status) {
        HealthStatus.PASSED -> "✓"
        HealthStatus.WARNING -> "⚠"
        HealthStatus.FAILED -> "✗"
        HealthStatus.RUNNING -> "●"
        HealthStatus.PENDING -> "○"
        HealthStatus.SKIPPED -> "—"
    }

    Column(
        modifier = Modifier
            .animateContentSize()
            .fillMaxWidth()
            .clip(shapes.small)
            .bclick { expanded = !expanded }
            .background(
                when {
                    isFailed -> colorScheme.errorContainer.copy(alpha = 0.22f)
                    isWarning -> Color(0xFFFFF3E0).copy(alpha = 0.22f)
                    isRunning -> colorScheme.primaryContainer.copy(alpha = 0.2f)
                    else -> colorScheme.surfaceContainerLow
                },
                shapes.small
            )
            .border(
                1.dp,
                when {
                    isFailed -> colorScheme.error.copy(alpha = 0.35f)
                    isWarning -> Color(0xFFF57F17).copy(alpha = 0.35f)
                    isRunning -> colorScheme.primary.copy(alpha = 0.5f)
                    else -> colorScheme.outlineVariant.copy(alpha = 0.35f)
                },
                shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = statusColor
                    )
                } else {
                    Text(
                        text = statusSymbol,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Gap(12)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                Gap(2)
                item.result.message?.let { msg ->
                    Text(
                        text = msg,
                        style = typography.bodySmall,
                        color = if (isFailed) colorScheme.error else colorScheme.onSurfaceVariant
                    )
                }
            }

            // Категория Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(colorScheme.surfaceContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.category.name,
                    style = typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
            }
        }

        AnimatedVisibility(visible = expanded && (item.result.details.isNotEmpty() || (item.result as? HealthResult.Failed)?.error != null)) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f))
                Gap(6)

                if (item.result.details.isNotEmpty()) {
                    item.result.details.forEach { (k, v) ->
                        Text(
                            text = "$k: $v",
                            style = typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                (item.result as? HealthResult.Failed)?.error?.let { err ->
                    Gap(4)
                    Text(
                        text = err.stackTraceToString().take(300),
                        style = typography.bodySmall,
                        color = colorScheme.error,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
