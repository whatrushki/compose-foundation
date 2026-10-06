package app.what.foundation.healthcheck.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

enum class HealthCategory(val title: String) {
    SYSTEM("Система и устройство"),
    NETWORK("Сеть и подключение"),
    STORAGE("Хранилище и база данных"),
    PROVIDER("Провайдеры расписания"),
    AUTH("Авторизация и аккаунты"),
    CUSTOM("Пользовательские проверки")
}

enum class HealthStatus {
    PENDING, RUNNING, PASSED, WARNING, FAILED, SKIPPED
}

sealed interface HealthResult {
    val status: HealthStatus
    val message: String?
    val details: Map<String, String>

    data object Pending : HealthResult {
        override val status: HealthStatus get() = HealthStatus.PENDING
        override val message: String? get() = "В очереди"
        override val details: Map<String, String> get() = emptyMap()
    }

    data object Running : HealthResult {
        override val status: HealthStatus get() = HealthStatus.RUNNING
        override val message: String? get() = "Выполняется проверка..."
        override val details: Map<String, String> get() = emptyMap()
    }

    data class Passed(
        override val message: String? = null,
        val durationMs: Long = 0,
        override val details: Map<String, String> = emptyMap()
    ) : HealthResult {
        override val status: HealthStatus get() = HealthStatus.PASSED
    }

    data class Warning(
        override val message: String,
        val canProceed: Boolean = true,
        val fixActionTitle: String? = null,
        val fixAction: (suspend () -> Unit)? = null,
        override val details: Map<String, String> = emptyMap()
    ) : HealthResult {
        override val status: HealthStatus get() = HealthStatus.WARNING
    }

    data class Failed(
        override val message: String,
        val error: Throwable? = null,
        val fixActionTitle: String? = null,
        val fixAction: (suspend () -> Unit)? = null,
        override val details: Map<String, String> = emptyMap()
    ) : HealthResult {
        override val status: HealthStatus get() = HealthStatus.FAILED
    }

    data class Skipped(
        val reason: String
    ) : HealthResult {
        override val status: HealthStatus get() = HealthStatus.SKIPPED
        override val message: String get() = reason
        override val details: Map<String, String> get() = emptyMap()
    }
}

data class CheckItemResult(
    val checkId: String,
    val title: String,
    val category: HealthCategory,
    val result: HealthResult
)

data class HealthReport(
    val timestamp: Instant = Clock.System.now(),
    val items: List<CheckItemResult>
) {
    val overallStatus: HealthStatus
        get() = when {
            items.any { it.result.status == HealthStatus.FAILED } -> HealthStatus.FAILED
            items.any { it.result.status == HealthStatus.WARNING } -> HealthStatus.WARNING
            else -> HealthStatus.PASSED
        }

    val passedCount: Int get() = items.count { it.result.status == HealthStatus.PASSED }
    val warningCount: Int get() = items.count { it.result.status == HealthStatus.WARNING }
    val failedCount: Int get() = items.count { it.result.status == HealthStatus.FAILED }

    fun toJson(): String {
        val lines = items.map { item ->
            """  "${item.checkId}": { "title": "${item.title}", "status": "${item.result.status}", "message": "${item.result.message.orEmpty()}" }"""
        }
        return "{\n  \"timestamp\": \"$timestamp\",\n  \"status\": \"$overallStatus\",\n  \"checks\": {\n${lines.joinToString(",\n")}\n  }\n}"
    }
}
