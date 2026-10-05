package app.what.foundation.delivery.client

import app.what.foundation.data.settings.KeyValueStorage
import app.what.foundation.delivery.model.*
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

data class DeliveryAppContext(
    val appId: String,
    val versionCode: Int,
    val institutionId: String = "",
    val platform: String = "android",
    val language: String = "ru"
)

class DeliveryClient(
    private val httpClient: HttpClient,
    private val storage: KeyValueStorage,
    private val rawBaseUrl: String = "https://raw.githubusercontent.com/whatrushki/delivery/main"
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetchActiveNotifications(appContext: DeliveryAppContext): List<DeliveryNotification> {
        val url = "$rawBaseUrl/notifications/active.json"
        val cachedEtag = storage.getString("delivery_notifications_etag") ?: ""
        val cachedPayload = storage.getString("delivery_notifications_payload") ?: ""

        val responseText: String = try {
            val response: HttpResponse = httpClient.get(url) {
                if (cachedEtag.isNotBlank()) {
                    header(HttpHeaders.IfNoneMatch, cachedEtag)
                }
            }

            if (response.status == HttpStatusCode.NotModified && cachedPayload.isNotBlank()) {
                cachedPayload
            } else if (response.status.value in 200..299) {
                val text = response.bodyAsText()
                val etag = response.headers[HttpHeaders.ETag].orEmpty()
                if (etag.isNotBlank()) {
                    storage.putString("delivery_notifications_etag", etag)
                }
                storage.putString("delivery_notifications_payload", text)
                text
            } else {
                cachedPayload
            }
        } catch (_: Exception) {
            cachedPayload
        }

        if (responseText.isBlank()) return emptyList()

        val parsed = try {
            json.decodeFromString<DeliveryNotificationResponse>(responseText)
        } catch (_: Exception) {
            return emptyList()
        }

        val now = Clock.System.now()
        val dismissedIds = getDismissedIds()

        return parsed.items.filter { item ->
            if (dismissedIds.contains(item.id)) return@filter false
            if (item.expiresAt < now) return@filter false
            item.startsAt?.let { if (it > now) return@filter false }

            // Target matching
            val targeting = item.targeting ?: return@filter true
            if (targeting.appIds.isNotEmpty() && !targeting.appIds.contains(appContext.appId)) return@filter false
            targeting.minVersionCode?.let { if (appContext.versionCode < it) return@filter false }
            targeting.maxVersionCode?.let { if (appContext.versionCode > it) return@filter false }
            if (targeting.institutions.isNotEmpty() && appContext.institutionId.isNotBlank() && !targeting.institutions.contains(appContext.institutionId)) return@filter false
            if (targeting.platforms.isNotEmpty() && !targeting.platforms.contains(appContext.platform)) return@filter false
            if (targeting.languages.isNotEmpty() && !targeting.languages.contains(appContext.language)) return@filter false

            true
        }
    }

    private fun getDismissedIds(): Set<String> {
        val raw = storage.getString("delivery_dismissed_ids") ?: ""
        return if (raw.isBlank()) emptySet() else raw.split(",").toSet()
    }

    fun dismissNotification(id: String) {
        val updated = getDismissedIds() + id
        storage.putString("delivery_dismissed_ids", updated.joinToString(","))
    }

    suspend fun fetchChangelog(appId: String = "schedule"): List<ChangelogRelease> {
        val url = "$rawBaseUrl/changelogs/$appId/releases.json"
        return try {
            val response: HttpResponse = httpClient.get(url)
            val text = response.bodyAsText()
            val parsed = json.decodeFromString<ChangelogResponse>(text)
            parsed.releases.sortedByDescending { it.versionCode }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
