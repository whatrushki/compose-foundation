package app.what.foundation.scraper.dsl

import app.what.foundation.scraper.model.*
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class ItemSchedulePipelineBuilder {
    var daySelector: String = ".schedule_item"
    var titleSelector: String = ".schedule_title"
    var lessonSelector: String = "p"
    var targetSelector: String = "h3"

    var ignoreLessonIf: ((Element) -> Boolean)? = { it.html().contains("href") }

    var targetNameExtractor: (Element) -> String = { doc ->
        doc.selectFirst(targetSelector)?.text()?.trim().orEmpty()
    }

    var dateParser: (String, LocalDate) -> LocalDate = { rawTitle, today ->
        val dateParts = rawTitle.split(",").firstOrNull()?.trim()?.split(Regex("\\s+")) ?: emptyList()
        if (dateParts.size >= 2) {
            val dayNum = dateParts[0].toIntOrNull() ?: today.dayOfMonth
            val monthNum = parseRussianMonth(dateParts[1]).takeIf { it in 1..12 } ?: today.monthNumber
            try {
                LocalDate(today.year, monthNum, dayNum)
            } catch (_: Exception) {
                today
            }
        } else {
            today
        }
    }

    var timeParser: (String) -> Pair<LocalTime, LocalTime>? = { rawTime ->
        val parts = rawTime.split(Regex("[-—–]")).map { it.trim() }
        if (parts.size >= 2) {
            val start = parseLocalTime(parts.first())
            val end = parseLocalTime(parts.last())
            if (start != null && end != null) start to end else null
        } else null
    }

    var commonBells: List<LessonTimeSlot> = emptyList()
    var shortenedBells: List<LessonTimeSlot> = emptyList()
    var withClassHourBells: List<LessonTimeSlot> = emptyList()

    var scheduleTypeDetector: (List<ScrapedLesson>) -> ScrapedScheduleType = { lessons ->
        when {
            lessons.any { it.type == ScrapedLessonType.CLASS_HOUR || it.subject.contains("Классный", ignoreCase = true) } ->
                ScrapedScheduleType.WITH_CLASS_HOUR
            lessons.any { it.startTime == LocalTime(8, 0) && it.endTime == LocalTime(8, 50) } ->
                ScrapedScheduleType.SHORTENED
            else ->
                ScrapedScheduleType.COMMON
        }
    }

    fun bells(
        common: List<LessonTimeSlot>,
        shortened: List<LessonTimeSlot> = emptyList(),
        withClassHour: List<LessonTimeSlot> = emptyList()
    ) {
        this.commonBells = common
        this.shortenedBells = shortened
        this.withClassHourBells = withClassHour
    }

    fun build(): ItemSchedulePipeline = ItemSchedulePipeline(this)
}

class ItemSchedulePipeline(val config: ItemSchedulePipelineBuilder) {

    private val brRegex = Regex("<br\\s*/?>")
    private val htmlTagRegex = Regex("<.*?>")

    fun extractTargetName(document: Element, fallback: String = ""): String {
        val extracted = config.targetNameExtractor(document).trim()
        return extracted.ifEmpty { fallback }
    }

    fun parseSchedule(
        html: String,
        isGroup: Boolean,
        fallbackTarget: String = "",
        today: LocalDate
    ): List<ScrapedDay> {
        val document = Ksoup.parse(html)
        val targetName = extractTargetName(document, fallbackTarget)
        val dayElements = document.select(config.daySelector)

        return dayElements.map { dayElement ->
            val titleText = dayElement.selectFirst(config.titleSelector)?.text()?.trim().orEmpty()
            val date = config.dateParser(titleText, today)

            val rawLessons = dayElement.select(config.lessonSelector)
            var lessons = rawLessons.mapNotNull { lessonRaw ->
                if (config.ignoreLessonIf?.invoke(lessonRaw) == true) return@mapNotNull null

                val content = lessonRaw.html().split(brRegex)
                if (content.size < 2) return@mapNotNull null

                val timePair = config.timeParser(content[0].replace(htmlTagRegex, "").trim())
                    ?: return@mapNotNull null

                val subject = content[1].replace(htmlTagRegex, "").trim()
                if (subject.isBlank()) return@mapNotNull null

                val thirdLine = content.getOrNull(2)?.split(", ")
                val teacherOrGroup = thirdLine?.firstOrNull()?.replace(htmlTagRegex, "")?.trim().orEmpty()
                val audBuilding = thirdLine?.lastOrNull()?.split(Regex("\\s+"))?.lastOrNull()?.split("/") ?: emptyList()

                val aud = if (audBuilding.size > 1) {
                    audBuilding.dropLast(1).joinToString("/")
                } else {
                    audBuilding.firstOrNull() ?: ""
                }
                val bld = audBuilding.lastOrNull() ?: "1"

                val isClassHour = subject.contains("Классный", ignoreCase = true)
                val isAdditional = "Доп." in subject

                val unit = ScrapedUnit(
                    teacher = if (isGroup) teacherOrGroup else targetName,
                    group = if (isGroup) targetName else teacherOrGroup,
                    room = aud,
                    building = bld
                )

                ScrapedLesson(
                    date = date,
                    number = 0,
                    startTime = timePair.first,
                    endTime = timePair.second,
                    subject = subject,
                    units = if (unit.teacher.isNotBlank() || unit.room.isNotBlank()) listOf(unit) else emptyList(),
                    type = when {
                        isClassHour -> ScrapedLessonType.CLASS_HOUR
                        isAdditional -> ScrapedLessonType.PRACTICE
                        else -> ScrapedLessonType.COMMON
                    },
                    state = ScrapedLessonState.COMMON
                )
            }

            val scheduleType = config.scheduleTypeDetector(lessons)
            val timeSchedule = when (scheduleType) {
                ScrapedScheduleType.SHORTENED -> config.shortenedBells
                ScrapedScheduleType.WITH_CLASS_HOUR -> config.withClassHourBells
                else -> config.commonBells
            }

            lessons = lessons.mapIndexed { index, lesson ->
                val isClassHour = lesson.subject.contains("Классный", ignoreCase = true) ||
                        lesson.type == ScrapedLessonType.CLASS_HOUR
                if (isClassHour) {
                    lesson.copy(number = 0, type = ScrapedLessonType.CLASS_HOUR)
                } else {
                    val match = timeSchedule.firstOrNull { it.number != 0 && (it.start == lesson.startTime || it.end == lesson.endTime) }
                        ?: config.commonBells.firstOrNull { it.number != 0 && (it.start == lesson.startTime || it.end == lesson.endTime) }
                        ?: config.withClassHourBells.firstOrNull { it.number != 0 && (it.start == lesson.startTime || it.end == lesson.endTime) }
                        ?: config.shortenedBells.firstOrNull { it.number != 0 && (it.start == lesson.startTime || it.end == lesson.endTime) }

                    val number = match?.number ?: (index + 1)
                    lesson.copy(number = number)
                }
            }

            ScrapedDay(
                date = date,
                scheduleType = scheduleType,
                lessons = lessons
            )
        }
    }
}

fun itemSchedulePipeline(block: ItemSchedulePipelineBuilder.() -> Unit = {}): ItemSchedulePipeline =
    ItemSchedulePipelineBuilder().apply(block).build()

private fun parseRussianMonth(month: String): Int {
    val m = month.lowercase().trim()
    return when {
        m.startsWith("янв") -> 1
        m.startsWith("фев") -> 2
        m.startsWith("мар") -> 3
        m.startsWith("апр") -> 4
        m.startsWith("май") || m.startsWith("мая") -> 5
        m.startsWith("июн") -> 6
        m.startsWith("июл") -> 7
        m.startsWith("авг") -> 8
        m.startsWith("сен") -> 9
        m.startsWith("окт") -> 10
        m.startsWith("ноя") -> 11
        m.startsWith("дек") -> 12
        else -> -1
    }
}

private fun parseLocalTime(str: String): LocalTime? {
    return try {
        val parts = str.split(":", ".").map { it.trim().toInt() }
        if (parts.size >= 2) LocalTime(parts[0], parts[1]) else null
    } catch (_: Exception) {
        null
    }
}
