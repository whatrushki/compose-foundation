package app.what.foundation.delivery.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DeliveryType {
    @SerialName("banner") BANNER,
    @SerialName("dialog") DIALOG,
    @SerialName("push") PUSH,
    @SerialName("survey") SURVEY
}

@Serializable
data class DeliveryTargeting(
    @SerialName("app_ids") val appIds: List<String> = emptyList(),
    @SerialName("min_version_code") val minVersionCode: Int? = null,
    @SerialName("max_version_code") val maxVersionCode: Int? = null,
    val institutions: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val languages: List<String> = emptyList()
)

@Serializable
enum class SurveyQuestionType {
    @SerialName("single_choice") SINGLE_CHOICE,
    @SerialName("multi_choice") MULTI_CHOICE,
    @SerialName("text") TEXT,
    @SerialName("rating") RATING
}

@Serializable
data class SurveyQuestion(
    val id: String,
    val title: String,
    val type: SurveyQuestionType,
    val options: List<String> = emptyList(),
    val required: Boolean = true
)

@Serializable
data class DeliverySurvey(
    @SerialName("survey_id") val surveyId: String,
    @SerialName("submit_url") val submitUrl: String? = null,
    val questions: List<SurveyQuestion> = emptyList()
)

@Serializable
data class DeliveryNotification(
    val id: String,
    val type: DeliveryType,
    val priority: String = "normal",
    val title: String,
    val message: String,
    val icon: String? = null,
    @SerialName("action_title") val actionTitle: String? = null,
    @SerialName("action_url") val actionUrl: String? = null,
    @SerialName("starts_at") val startsAt: Instant? = null,
    @SerialName("expires_at") val expiresAt: Instant,
    val dismissible: Boolean = true,
    val targeting: DeliveryTargeting? = null,
    val survey: DeliverySurvey? = null
)

@Serializable
data class DeliveryNotificationResponse(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("updated_at") val updatedAt: String? = null,
    val items: List<DeliveryNotification> = emptyList()
)

@Serializable
data class ChangelogRelease(
    @SerialName("version_name") val versionName: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("release_date") val releaseDate: String,
    val title: String,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("is_critical") val isCritical: Boolean = false,
    val categories: Map<String, List<String>> = emptyMap()
)

@Serializable
data class ChangelogResponse(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("app_id") val appId: String,
    val releases: List<ChangelogRelease> = emptyList()
)
