package app.what.foundation.healthcheck.registry

import app.what.foundation.healthcheck.model.HealthCategory
import app.what.foundation.healthcheck.model.HealthResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

class NetworkConnectivityCheck(
    private val httpClient: HttpClient,
    private val pingUrl: String = "https://1.1.1.1"
) : HealthCheck {
    override val id: String = "system_network_connectivity"
    override val title: String = "Подключение к сети Интернет"
    override val category: HealthCategory = HealthCategory.NETWORK

    override suspend fun run(): HealthResult {
        return try {
            val response: HttpResponse = httpClient.get(pingUrl)
            if (response.status.isSuccess()) {
                HealthResult.Passed(message = "Интернет-соединение активно")
            } else {
                HealthResult.Warning(message = "Подключение есть, ответ сервера HTTP ${response.status.value}")
            }
        } catch (e: Exception) {
            HealthResult.Failed(message = "Отсутствует подключение к сети: ${e.message}", error = e)
        }
    }
}

class EndpointAvailabilityCheck(
    override val id: String,
    override val title: String,
    override val category: HealthCategory = HealthCategory.PROVIDER,
    private val endpointUrl: String,
    private val httpClient: HttpClient
) : HealthCheck {
    override suspend fun run(): HealthResult {
        return try {
            val start = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            val response: HttpResponse = httpClient.get(endpointUrl)
            val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - start

            if (response.status.isSuccess()) {
                HealthResult.Passed(
                    message = "Сервер доступен (HTTP ${response.status.value}, ${duration}ms)",
                    durationMs = duration
                )
            } else {
                HealthResult.Warning(
                    message = "Сервер вернул статус HTTP ${response.status.value}",
                    details = mapOf("url" to endpointUrl, "status" to response.status.value.toString())
                )
            }
        } catch (e: Exception) {
            HealthResult.Failed(
                message = "Сервер недоступен: ${e.message}",
                error = e,
                details = mapOf("url" to endpointUrl)
            )
        }
    }
}
