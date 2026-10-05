package app.what.foundation.healthcheck.registry

import app.what.foundation.healthcheck.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface HealthCheck {
    val id: String
    val title: String
    val category: HealthCategory
    suspend fun run(): HealthResult
}

class HealthRegistry {
    private val checks = mutableListOf<HealthCheck>()
    private val mutex = Mutex()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastReport = MutableStateFlow<HealthReport?>(null)
    val lastReport: StateFlow<HealthReport?> = _lastReport.asStateFlow()

    fun register(check: HealthCheck) {
        checks.removeAll { it.id == check.id }
        checks.add(check)
    }

    fun registerAll(vararg checkList: HealthCheck) {
        checkList.forEach { register(it) }
    }

    suspend fun runAll(): HealthReport = mutex.withLock {
        _isRunning.value = true
        try {
            val results = checks.map { check ->
                val res = try {
                    check.run()
                } catch (e: Exception) {
                    HealthResult.Failed(message = "Ошибка выполнения проверки: ${e.message}", error = e)
                }
                CheckItemResult(
                    checkId = check.id,
                    title = check.title,
                    category = check.category,
                    result = res
                )
            }
            val report = HealthReport(items = results)
            _lastReport.value = report
            report
        } finally {
            _isRunning.value = false
        }
    }
}
