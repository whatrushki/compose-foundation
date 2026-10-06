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
    val registeredChecks: List<HealthCheck> get() = checks.toList()

    private val _itemsState = MutableStateFlow<List<CheckItemResult>>(emptyList())
    val itemsState: StateFlow<List<CheckItemResult>> = _itemsState.asStateFlow()

    private fun refreshInitialItems() {
        if (_lastReport.value == null) {
            _itemsState.value = checks.map {
                CheckItemResult(
                    checkId = it.id,
                    title = it.title,
                    category = it.category,
                    result = HealthResult.Pending
                )
            }
        }
    }

    fun register(check: HealthCheck) {
        checks.removeAll { it.id == check.id }
        checks.add(check)
        refreshInitialItems()
    }

    fun registerAll(vararg checkList: HealthCheck) {
        checkList.forEach { check ->
            checks.removeAll { it.id == check.id }
            checks.add(check)
        }
        refreshInitialItems()
    }

    suspend fun runAll(): HealthReport = mutex.withLock {
        _isRunning.value = true
        
        // Пометить все тесты как Pending
        val currentResults = checks.map { check ->
            CheckItemResult(
                checkId = check.id,
                title = check.title,
                category = check.category,
                result = HealthResult.Pending
            )
        }.toMutableList()
        _itemsState.value = currentResults.toList()

        try {
            for (i in checks.indices) {
                val check = checks[i]
                
                // Ставим текущему статус RUNNING
                currentResults[i] = currentResults[i].copy(result = HealthResult.Running)
                _itemsState.value = currentResults.toList()

                val res = try {
                    check.run()
                } catch (e: Exception) {
                    HealthResult.Failed(message = "Ошибка выполнения проверки: ${e.message}", error = e)
                }

                currentResults[i] = currentResults[i].copy(result = res)
                _itemsState.value = currentResults.toList()
            }
            
            val report = HealthReport(items = currentResults.toList())
            _lastReport.value = report
            report
        } finally {
            _isRunning.value = false
        }
    }
}
