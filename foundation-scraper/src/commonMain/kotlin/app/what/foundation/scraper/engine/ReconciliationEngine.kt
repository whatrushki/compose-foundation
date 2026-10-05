package app.what.foundation.scraper.engine

import app.what.foundation.scraper.cleaner.TeacherNameCleaner
import app.what.foundation.scraper.model.LessonTimeSlot
import app.what.foundation.scraper.model.ScrapedLesson
import app.what.foundation.scraper.model.ScrapedLessonState
import app.what.foundation.scraper.model.ScrapedLessonType
import kotlinx.datetime.LocalTime

object ReconciliationEngine {

    fun applyReplacements(
        baseLessons: List<ScrapedLesson>,
        replacements: List<ScrapedLesson>,
        timeSlots: List<LessonTimeSlot>,
        subjectResolver: ((teacher: String, group: String) -> String?)? = null
    ): List<ScrapedLesson> {
        val cleanBaseLessons = baseLessons.groupBy { Triple(it.date, it.startTime, it.subject) }
            .map { (_, groupLessons) ->
                groupLessons.first().copy(
                    units = groupLessons.flatMap { it.units }.distinct()
                )
            }

        if (replacements.isEmpty()) {
            return cleanBaseLessons.sortedWith(compareBy({ it.startTime }, { it.number }))
        }

        val cleanReplacements = replacements.groupBy { it.date to it.number }
            .map { (_, groupLessons) ->
                groupLessons.first().copy(
                    units = groupLessons.flatMap { it.units }.distinct()
                )
            }

        val minTime = LocalTime(0, 0)
        val unionSchedule = mutableMapOf<Int, Pair<ScrapedLesson?, ScrapedLesson?>>()
        cleanReplacements.forEach { unionSchedule[it.number] = it to null }
        cleanBaseLessons.forEach { unionSchedule[it.number] = unionSchedule[it.number]?.first to it }

        return unionSchedule.mapNotNull { (_, pair) ->
            val replacement = pair.first
            val lesson = pair.second

            if (replacement == null && lesson != null) {
                // Пары нет в планшетке замен, хотя для группы были замены в этот день -> пара отменена
                lesson.copy(state = ScrapedLessonState.REMOVED)
            } else if (replacement != null && lesson == null) {
                // Добавленная пара
                val lessonTime = timeSlots.firstOrNull { it.number == replacement.number }
                val repTeacher = replacement.units.firstOrNull()?.teacher.orEmpty().trim()
                val repGroup = replacement.units.firstOrNull()?.group.orEmpty().trim()
                val resolvedSubject = if (repTeacher.isNotEmpty()) subjectResolver?.invoke(repTeacher, repGroup) else null

                val isClassHour = replacement.number == 0 ||
                    replacement.subject.contains("Классный", ignoreCase = true) ||
                    replacement.type == ScrapedLessonType.CLASS_HOUR

                val subject = when {
                    isClassHour -> "Классный час"
                    replacement.subject.isNotBlank() -> replacement.subject
                    !resolvedSubject.isNullOrBlank() -> resolvedSubject
                    else -> "Предмет не указан"
                }

                val start = lessonTime?.start ?: replacement.startTime.takeIf { it != minTime } ?: minTime
                val end = lessonTime?.end ?: replacement.endTime.takeIf { it != minTime } ?: minTime

                replacement.copy(
                    number = if (isClassHour) 0 else replacement.number,
                    state = ScrapedLessonState.ADDED,
                    startTime = start,
                    endTime = end,
                    subject = subject,
                    type = if (isClassHour) ScrapedLessonType.CLASS_HOUR else replacement.type
                )
            } else if (replacement != null && lesson != null) {
                val isClassHour = replacement.number == 0 ||
                    replacement.subject.contains("Классный", ignoreCase = true) ||
                    replacement.type == ScrapedLessonType.CLASS_HOUR ||
                    lesson.number == 0 ||
                    lesson.type == ScrapedLessonType.CLASS_HOUR

                if (lesson.matchesIdentity(replacement)) {
                    // Преподаватель и аудитория совпадают -> пара НЕ изменена
                    lesson.copy(state = ScrapedLessonState.COMMON)
                } else {
                    // Преподаватель или аудитория изменились -> пара изменена
                    val lessonTime = timeSlots.firstOrNull { it.number == replacement.number }
                    val repTeacher = replacement.units.firstOrNull()?.teacher.orEmpty().trim()
                    val repGroup = replacement.units.firstOrNull()?.group.orEmpty().trim()
                    val sameTeacher = lesson.units.any { TeacherNameCleaner.isSameTeacher(it.teacher, repTeacher) }

                    val subject = when {
                        isClassHour -> "Классный час"
                        replacement.subject.isNotBlank() -> replacement.subject
                        sameTeacher -> lesson.subject.ifEmpty { "Предмет не указан" }
                        else -> {
                            val resolved = if (repTeacher.isNotEmpty()) subjectResolver?.invoke(repTeacher, repGroup) else null
                            if (!resolved.isNullOrBlank()) resolved else "Предмет не указан"
                        }
                    }

                    val targetType = when {
                        isClassHour -> ScrapedLessonType.CLASS_HOUR
                        sameTeacher -> lesson.type
                        else -> ScrapedLessonType.COMMON
                    }

                    val start = lesson.startTime.takeIf { it != minTime }
                        ?: (lessonTime?.start ?: replacement.startTime.takeIf { it != minTime } ?: minTime)
                    val end = lesson.endTime.takeIf { it != minTime }
                        ?: (lessonTime?.end ?: replacement.endTime.takeIf { it != minTime } ?: minTime)

                    replacement.copy(
                        number = if (isClassHour) 0 else replacement.number,
                        state = ScrapedLessonState.CHANGED,
                        startTime = start,
                        endTime = end,
                        subject = subject,
                        type = targetType
                    )
                }
            } else {
                null
            }
        }.sortedWith(compareBy({ it.startTime }, { it.number }))
    }
}
