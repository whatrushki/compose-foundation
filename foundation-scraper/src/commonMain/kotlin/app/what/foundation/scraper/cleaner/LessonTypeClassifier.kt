package app.what.foundation.scraper.cleaner

import app.what.foundation.scraper.model.ScrapedLessonType

object LessonTypeClassifier {
    private val TYPE_IN_PARENS_REGEX = Regex("^(.*?)(?:\\s*\\((.*?)\\))?$")

    fun extractSubjectAndType(raw: String): Pair<String, ScrapedLessonType> {
        val trimmed = raw.trim()
        val match = TYPE_IN_PARENS_REGEX.find(trimmed)
        val baseSubject = match?.groups?.get(1)?.value?.trim().orEmpty()
        val typeStr = match?.groups?.get(2)?.value?.trim().orEmpty().lowercase()

        val type = when {
            typeStr.contains("лек") -> ScrapedLessonType.LECTURE
            typeStr.contains("прак") || typeStr.contains("пр") -> ScrapedLessonType.PRACTICE
            typeStr.contains("лаб") -> ScrapedLessonType.LABORATORY
            typeStr.contains("конс") -> ScrapedLessonType.CONSULTATION
            typeStr.contains("экз") -> ScrapedLessonType.EXAM
            typeStr.contains("зач") -> ScrapedLessonType.CREDIT
            baseSubject.contains("классный час", ignoreCase = true) -> ScrapedLessonType.CLASS_HOUR
            else -> ScrapedLessonType.COMMON
        }
        return (if (baseSubject.isNotEmpty()) baseSubject else trimmed) to type
    }
}
