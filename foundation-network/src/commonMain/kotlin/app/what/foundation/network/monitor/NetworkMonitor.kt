package app.what.foundation.network.monitor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.what.foundation.utils.currentTimeMillis
import io.ktor.client.call.save
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpSendPipeline
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.util.AttributeKey
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private var nextRequestId = 0L
private fun generateNetworkRequestId(): String =
    "${currentTimeMillis()}_${++nextRequestId}_${kotlin.random.Random.nextInt(1000, 9999)}"

private val SENSITIVE_HEADERS = setOf("authorization", "cookie", "set-cookie", "x-auth-token")
private fun sanitizeHeaders(headers: Map<String, String>): Map<String, String> {
    return headers.mapValues { (key, value) ->
        if (key.lowercase() in SENSITIVE_HEADERS) "[REDACTED]" else value
    }
}

private const val MAX_REQUESTS = 150
private const val MAX_BODY_PREVIEW_SIZE = 32 * 1024 // 32 KB

data class NetworkRequest(
    val id: String = generateNetworkRequestId(),
    val url: String,
    val method: String,
    val statusCode: Int? = null,
    val requestTime: Long,
    var responseTime: Long? = null,
    var endTime: Long? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    var responseHeaders: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    var responseBody: String? = null,
    var error: String? = null,
    val requestSize: Long = 0,
    var responseSize: Long = 0
) {
    val isSuccessful: Boolean get() = statusCode in 200..299
    val isWebSocket: Boolean get() = requestHeaders["Upgrade"]?.equals("websocket", true) == true
    val isPending: Boolean get() = statusCode == null && error == null

    val host: String get() = runCatching { Url(url).host }.getOrDefault(url)
    val path: String get() = runCatching { Url(url).encodedPath }.getOrDefault("/")

    val queryParams: List<Pair<String, String>>
        get() = runCatching {
            Url(url).parameters.entries().flatMap { (key, values) -> values.map { key to it } }
        }.getOrDefault(emptyList())

    val requestCookies: Map<String, String> get() = parseCookies(requestHeaders["Cookie"])
    val responseCookies: Map<String, String> get() = parseSetCookies(responseHeaders["Set-Cookie"])

    val duration: Long
        get() {
            val end = endTime ?: responseTime
            return if (end != null && end >= requestTime) end - requestTime else 0
        }
    val latency: Long get() = if (responseTime != null && responseTime!! >= requestTime) responseTime!! - requestTime else 0

    val contentType: String?
        get() = responseHeaders[HttpHeaders.ContentType] ?: responseHeaders["content-type"]

    val statusCategory: StatusCategory
        get() = when (statusCode) {
            in 200..299 -> StatusCategory.Success
            in 300..399 -> StatusCategory.Redirect
            in 400..599 -> StatusCategory.Error
            null -> if (error != null) StatusCategory.Error else StatusCategory.Pending
            else -> StatusCategory.Unknown
        }

    private fun parseCookies(header: String?): Map<String, String> {
        if (header.isNullOrEmpty()) return emptyMap()
        return header.split(";").associate {
            val parts = it.split("=", limit = 2)
            (parts.getOrNull(0)?.trim() ?: "") to (parts.getOrNull(1)?.trim() ?: "")
        }
    }

    private fun parseSetCookies(header: String?): Map<String, String> = parseCookies(header)
}

enum class StatusCategory {
    Success, Redirect, Error, Pending, Unknown
}

object NetworkMonitor {
    private val _requests = kotlinx.coroutines.flow.MutableStateFlow<List<NetworkRequest>>(emptyList())
    val requestsFlow: kotlinx.coroutines.flow.StateFlow<List<NetworkRequest>> = _requests.asStateFlow()
    val requests: List<NetworkRequest> get() = _requests.value

    var isMonitoringPaused by mutableStateOf(false)
        private set

    fun setMonitoringPause(value: Boolean) {
        isMonitoringPaused = value
    }

    fun trackRequest(request: NetworkRequest) {
        if (isMonitoringPaused) return
        val sanitized = request.copy(
            requestHeaders = sanitizeHeaders(request.requestHeaders),
            requestBody = request.requestBody?.take(MAX_BODY_PREVIEW_SIZE)
        )
        _requests.update { current ->
            (current + sanitized).takeLast(MAX_REQUESTS)
        }
    }

    fun updateRequest(id: String, transform: (NetworkRequest) -> NetworkRequest) {
        _requests.update { current ->
            current.map { if (it.id == id) transform(it) else it }
        }
    }

    fun toggleMonitoring(paused: Boolean) {
        isMonitoringPaused = paused
    }

    fun clearRequests() {
        _requests.value = emptyList()
    }

    fun exportRequests(): String {
        return Json.encodeToString(requests.map {
            mapOf(
                "url" to it.url,
                "method" to it.method,
                "status" to it.statusCode.toString(),
                "duration" to "${it.duration}ms",
                "requestTime" to it.requestTime.toString()
            )
        })
    }
}

private val json = Json { prettyPrint = true }

val NetworkMonitorPlugin = createClientPlugin("NetworkMonitor") {
    val callIdKey = AttributeKey<String>("NetworkMonitorCallId")

    on(SendingRequest) { request, content ->
        val callId = generateNetworkRequestId()
        request.attributes.put(callIdKey, callId)

        val requestBodyString = content.decodeContent()

        val netRequest = NetworkRequest(
            id = callId,
            url = request.url.toString(),
            method = request.method.value,
            requestTime = currentTimeMillis(),
            requestHeaders = sanitizeHeaders(
                request.headers.entries().associate { it.key to it.value.joinToString(", ") }
            ),
            requestBody = requestBodyString.take(MAX_BODY_PREVIEW_SIZE),
            requestSize = requestBodyString.length.toLong()
        )
        NetworkMonitor.trackRequest(netRequest)
    }

    client.sendPipeline.intercept(HttpSendPipeline.Before) {
        try {
            proceed()
        } catch (e: Throwable) {
            val callId = context.attributes.getOrNull(callIdKey)
            if (callId != null) {
                val errorMsg = when {
                    e is kotlinx.coroutines.CancellationException -> "Cancelled"
                    e.message.isNullOrBlank() -> e::class.simpleName ?: "Request failed"
                    else -> "${e::class.simpleName}: ${e.message}"
                }
                NetworkMonitor.updateRequest(callId) {
                    it.copy(
                        error = errorMsg,
                        endTime = currentTimeMillis()
                    )
                }
            }
            throw e
        }
    }

    onResponse { response ->
        val callId = response.call.attributes.getOrNull(callIdKey) ?: return@onResponse
        val responseTime = currentTimeMillis()

        NetworkMonitor.updateRequest(callId) {
            it.copy(
                statusCode = response.status.value,
                responseTime = responseTime,
                responseHeaders = sanitizeHeaders(
                    response.headers.entries().associate { entry -> entry.key to entry.value.joinToString(", ") }
                )
            )
        }

        val contentType = response.headers[HttpHeaders.ContentType] ?: response.headers["content-type"] ?: ""
        val isImage = contentType.contains("image", ignoreCase = true)
        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L

        if (isImage) {
            NetworkMonitor.updateRequest(callId) {
                it.copy(
                    endTime = currentTimeMillis(),
                    responseBody = "[Binary Image]",
                    responseSize = contentLength
                )
            }
            return@onResponse
        }

        val isBinary = contentType.contains("octet-stream", ignoreCase = true) ||
                contentType.contains("audio", ignoreCase = true) ||
                contentType.contains("video", ignoreCase = true) ||
                contentType.contains("zip", ignoreCase = true) ||
                contentType.contains("pdf", ignoreCase = true)

        if (isBinary || contentLength > 1_000_000L) {
            NetworkMonitor.updateRequest(callId) {
                it.copy(
                    endTime = currentTimeMillis(),
                    responseBody = if (isBinary) "[Binary Data (${formatBytesFallback(contentLength)})]"
                    else "[Large Response (${formatBytesFallback(contentLength)})]",
                    responseSize = contentLength
                )
            }
            return@onResponse
        }

        try {
            val savedResponse = runCatching { response.call.save().response }.getOrDefault(response)
            val body = runCatching { savedResponse.bodyAsText() }.getOrDefault("")
            val formatted = try {
                json.encodeToString(json.decodeFromString<JsonElement>(body))
            } catch (_: Exception) {
                body
            }
            val preview = formatted.take(MAX_BODY_PREVIEW_SIZE)

            NetworkMonitor.updateRequest(callId) {
                it.copy(
                    endTime = currentTimeMillis(),
                    responseBody = preview,
                    responseSize = if (contentLength > 0) contentLength else body.length.toLong()
                )
            }
        } catch (e: Throwable) {
            NetworkMonitor.updateRequest(callId) {
                it.copy(
                    error = "Read Error: ${e.message ?: e::class.simpleName}",
                    endTime = currentTimeMillis()
                )
            }
        }
    }
}

private fun formatBytesFallback(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val rounded = (value * 10).toInt() / 10.0
    return "$rounded ${units[unitIndex]}"
}

fun OutgoingContent.decodeContent(): String {
    return runCatching {
        when (this) {
            is OutgoingContent.ByteArrayContent -> bytes().decodeToString()
            is OutgoingContent.NoContent -> ""
            is OutgoingContent.ReadChannelContent -> "[Stream Content]"
            is OutgoingContent.WriteChannelContent -> "[Stream Content]"
            else -> ""
        }
    }.getOrDefault("")
}
