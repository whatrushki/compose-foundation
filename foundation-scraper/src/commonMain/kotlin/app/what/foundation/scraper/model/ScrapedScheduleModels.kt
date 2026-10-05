package app.what.foundation.scraper.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@Serializable
enum class ScrapedLessonType {
    COMMON,
    PRACTICE,
    LECTURE,
    LABORATORY,
    EXAM,
    CREDIT,
    CONSULTATION,
    CLASS_HOUR,
    OTHER,
    UNKNOWN;

    companion object {
        fun fromString(value: String): ScrapedLessonType = when {
            value.contains("класс", ignoreCase = true) -> CLASS_HOUR
            value.contains("пр", ignoreCase = true) -> PRACTICE
            value.contains("лек", ignoreCase = true) -> LECTURE
            value.contains("лаб", ignoreCase = true) -> LABORATORY
            value.contains("экз", ignoreCase = true) -> EXAM
            value.contains("зач", ignoreCase = true) -> CREDIT
            value.contains("конс", ignoreCase = true) -> CONSULTATION
            else -> OTHER
        }
    }
}

@Serializable
enum class ScrapedLessonState {
    COMMON, ADDED, REMOVED, CHANGED
}

@Serializable
enum class ScrapedScheduleType {
    COMMON,
    SHORTENED,
    WITH_CLASS_HOUR
}

@Serializable
data class LessonTimeSlot(
    val number: Int,
    val start: LocalTime,
    val end: LocalTime
)

@Serializable
data class ScrapedUnit(
    val teacher: String,
    val group: String,
    val room: String,
    val building: String = "",
    val onlineUrl: String? = null,
    val subject: String? = null
)

@Serializable
data class ScrapedLesson(
    val date: LocalDate,
    val number: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val subject: String,
    val units: List<ScrapedUnit>,
    val type: ScrapedLessonType = ScrapedLessonType.COMMON,
    val state: ScrapedLessonState = ScrapedLessonState.COMMON
) {
    infix operator fun plus(other: ScrapedLesson): ScrapedLesson =
        copy(units = (units + other.units).distinct())

    fun matchesIdentity(other: ScrapedLesson): Boolean {
        if (units.isEmpty() && other.units.isEmpty()) return true
        if (units.isEmpty() || other.units.isEmpty()) return false

        fun clean(s: String) = s.replace(" ", "")
            .replace(".", "")
            .replace("—", "-")
            .replace("–", "-")
            .replace("с", "c", ignoreCase = true)
            .replace("а", "a", ignoreCase = true)
            .replace("о", "o", ignoreCase = true)
            .replace("р", "p", ignoreCase = true)
            .replace("х", "x", ignoreCase = true)
            .replace("е", "e", ignoreCase = true)
            .trim()
            .lowercase()

        fun cleanRoom(r: String): String {
            val trimmed = r.trim().removeSuffix(".0")
            return clean(trimmed)
        }

        return units.all { u ->
            other.units.any { ou ->
                clean(u.teacher) == clean(ou.teacher) &&
                cleanRoom(u.room) == cleanRoom(ou.room)
            }
        }
    }
}

@Serializable
data class ScrapedDay(
    val date: LocalDate,
    val scheduleType: ScrapedScheduleType = ScrapedScheduleType.COMMON,
    val lessons: List<ScrapedLesson>
)
