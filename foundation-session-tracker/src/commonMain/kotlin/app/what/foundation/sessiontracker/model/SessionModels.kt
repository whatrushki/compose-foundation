package app.what.foundation.sessiontracker.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class SystemSnapshot(
    val isOnline: Boolean = true,
    val currentScreen: String = "",
    val memoryEstimateMb: Long = 0
)

@Serializable
data class SessionAction(
    val id: String,
    val name: String,
    val category: String,
    val timestamp: Instant = Clock.System.now(),
    val durationMs: Long = 0,
    val metadata: Map<String, String> = emptyMap(),
    val systemState: SystemSnapshot = SystemSnapshot(),
    val networkRequestIds: List<String> = emptyList(),
    val logs: List<String> = emptyList(),
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

@Serializable
data class SessionReport(
    val sessionId: String,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val totalActions: Int,
    val actions: List<SessionAction>,
    val resolvedNetworkPayloads: Map<String, String> = emptyMap()
)
