package app.what.foundation.sessiontracker.tracker

import app.what.foundation.sessiontracker.model.SessionAction
import app.what.foundation.sessiontracker.model.SessionReport
import app.what.foundation.sessiontracker.model.SystemSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ActionScope(
    val actionId: String
) {
    val linkedRequestIds = mutableListOf<String>()
    val logs = mutableListOf<String>()
    var errorMessage: String? = null
    var isSuccess: Boolean = true

    fun linkRequest(requestId: String) {
        linkedRequestIds.add(requestId)
    }

    fun log(message: String) {
        logs.add(message)
    }

    fun failure(message: String) {
        isSuccess = false
        errorMessage = message
    }
}

object SessionTracker {
    private var currentSessionId: String = "session_${Clock.System.now().toEpochMilliseconds()}"
    private var sessionStartTime: Instant = Clock.System.now()
    private val currentActions = mutableListOf<SessionAction>()

    private var previousSessionReport: SessionReport? = null

    private val _actionsFlow = MutableStateFlow<List<SessionAction>>(emptyList())
    val actionsFlow: StateFlow<List<SessionAction>> = _actionsFlow.asStateFlow()

    var isEnabled: Boolean = true
    var currentScreenName: String = ""

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun startNewSession() {
        if (!isEnabled) return
        if (currentActions.isNotEmpty()) {
            previousSessionReport = buildReport()
        }
        currentSessionId = "session_${Clock.System.now().toEpochMilliseconds()}"
        sessionStartTime = Clock.System.now()
        currentActions.clear()
        _actionsFlow.value = emptyList()
    }

    fun <T> trackAction(
        name: String,
        category: String = "USER_ACTION",
        metadata: Map<String, String> = emptyMap(),
        isOnline: Boolean = true,
        block: (ActionScope) -> T
    ): T {
        if (!isEnabled) {
            val scope = ActionScope("disabled")
            return block(scope)
        }

        val actionId = "act_${Clock.System.now().toEpochMilliseconds()}_${currentActions.size}"
        val scope = ActionScope(actionId)
        val start = Clock.System.now().toEpochMilliseconds()

        return try {
            val result = block(scope)
            val duration = Clock.System.now().toEpochMilliseconds() - start
            recordActionInternal(
                SessionAction(
                    id = actionId,
                    name = name,
                    category = category,
                    durationMs = duration,
                    metadata = metadata,
                    systemState = SystemSnapshot(isOnline = isOnline, currentScreen = currentScreenName),
                    networkRequestIds = scope.linkedRequestIds.toList(),
                    logs = scope.logs.toList(),
                    isSuccess = scope.isSuccess,
                    errorMessage = scope.errorMessage
                )
            )
            result
        } catch (e: Throwable) {
            val duration = Clock.System.now().toEpochMilliseconds() - start
            scope.failure(e.message ?: e.toString())
            recordActionInternal(
                SessionAction(
                    id = actionId,
                    name = name,
                    category = category,
                    durationMs = duration,
                    metadata = metadata,
                    systemState = SystemSnapshot(isOnline = isOnline, currentScreen = currentScreenName),
                    networkRequestIds = scope.linkedRequestIds.toList(),
                    logs = scope.logs.toList(),
                    isSuccess = false,
                    errorMessage = e.message ?: e.toString()
                )
            )
            throw e
        }
    }

    @PublishedApi
    internal fun recordActionInternal(action: SessionAction) {
        currentActions.add(action)
        _actionsFlow.value = currentActions.toList()
    }

    fun buildReport(resolvedNetworkPayloads: Map<String, String> = emptyMap()): SessionReport =
        SessionReport(
            sessionId = currentSessionId,
            startedAt = sessionStartTime,
            endedAt = Clock.System.now(),
            totalActions = currentActions.size,
            actions = currentActions.toList(),
            resolvedNetworkPayloads = resolvedNetworkPayloads
        )

    fun exportJson(resolvedNetworkPayloads: Map<String, String> = emptyMap()): String =
        json.encodeToString(buildReport(resolvedNetworkPayloads))

    fun getPreviousSessionReport(): SessionReport? = previousSessionReport
}
