package app.what.foundation.scraper.dsl

import app.what.foundation.scraper.cleaner.LessonTypeClassifier
import app.what.foundation.scraper.cleaner.TeacherNameCleaner
import app.what.foundation.scraper.model.*
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import kotlinx.datetime.*

data class ScrapedFaculty(val id: String, val name: String)
data class ScrapedGroup(val id: String, val name: String, val courseId: Int, val facId: String, val eduType: String = "")

class HtmlSchedulePipelineBuilder {
    var facultySelector: String = "a[data-fac-id]"
    var facultyIdAttr: String = "data-fac-id"
    var facultyNameExtractor: (Element) -> String = { it.text().trim() }

    var courseSelector: String = "a[data-course-id]"
    var courseIdAttr: String = "data-course-id"

    var groupSelector: String = "a[data-group-id]"
    var groupIdAttr: String = "data-group-id"
    var groupNameExtractor: (Element) -> String = { it.text().trim() }

    var tableSelector: String = "table"
    var dayHeaderSelector: String = "th, td.day, .day-header"
    var timeSlotSelector: String = "tr"
}

class HtmlSchedulePipeline(private val config: HtmlSchedulePipelineBuilder) {

    fun parseFaculties(html: String): List<ScrapedFaculty> {
        val doc = Ksoup.parse(html)
        return doc.select(config.facultySelector).mapNotNull { el ->
            val id = el.attr(config.facultyIdAttr).trim()
            val name = config.facultyNameExtractor(el)
            if (id.isNotEmpty() && name.isNotEmpty()) ScrapedFaculty(id, name) else null
        }.distinctBy { it.id }
    }

    fun parseCourses(html: String): List<Int> {
        val doc = Ksoup.parse(html)
        return doc.select(config.courseSelector).mapNotNull { el ->
            el.attr(config.courseIdAttr).trim().toIntOrNull()
        }.distinct().sorted()
    }

    fun parseGroups(html: String, facId: String, courseId: Int, eduType: String = ""): List<ScrapedGroup> {
        val doc = Ksoup.parse(html)
        return doc.select(config.groupSelector).mapNotNull { el ->
            val id = el.attr(config.groupIdAttr).trim()
            val name = config.groupNameExtractor(el)
            if (id.isNotEmpty() && name.isNotEmpty()) {
                ScrapedGroup(id = id, name = name, courseId = courseId, facId = facId, eduType = eduType)
            } else null
        }.distinctBy { it.id }
    }

    private data class ParsedSlotRow(
        val isDisabled: Boolean,
        val weekLabel: String,
        val subject: String,
        val teacher: String,
        val room: String
    )

    private data class ParsedSlot(
        val parnum: Int,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val rows: MutableList<ParsedSlotRow> = mutableListOf()
    )

    fun parseTimetable(
        html: String,
        currentLocalDate: LocalDate,
        groupName: String = ""
    ): List<ScrapedDay> {
        val doc = Ksoup.parse(html)
        val table = doc.selectFirst(config.tableSelector) ?: return emptyList()
        val rows = table.select("tr")

        val currentDayOfWeek = currentLocalDate.dayOfWeek.isoDayNumber // 1..7
        val monday = currentLocalDate.minus(DatePeriod(days = currentDayOfWeek - 1))

        val daysMap = mutableMapOf<LocalDate, MutableList<ParsedSlot>>()
        var currentDayDate: LocalDate? = null
        var currentSlot: ParsedSlot? = null

        for (r in rows) {
            val th = r.selectFirst(config.dayHeaderSelector)
            if (th != null && th.tagName().equals("th", ignoreCase = true)) {
                val headerText = th.text().trim()
                val dayOfWeekNum = parseDayOfWeek(headerText)
                currentDayDate = if (dayOfWeekNum in 1..7) {
                    monday.plus(DatePeriod(days = dayOfWeekNum - 1))
                } else null
                currentSlot = null
                continue
            }

            val targetDate = currentDayDate ?: continue
            val tds = r.select("td")
            if (tds.isEmpty()) continue
            if (tds.size == 1 && tds[0].text().contains("Нет пар", ignoreCase = true)) {
                continue
            }

            val isDisabled = tds.any { it.hasClass("disable") }
            val firstText = tds[0].text().trim()

            if (tds.size >= 6 && firstText.toIntOrNull() != null) {
                val parnum = firstText.toInt()
                val (st, et) = parseTimeRange(tds[1].text().trim())
                val weekLabel = tds[2].text().trim()
                val subject = tds[3].text().trim()
                val teacher = cleanTeacher(tds[4].text())
                val room = cleanField(tds[5].text())

                val slot = ParsedSlot(
                    parnum = parnum,
                    startTime = st,
                    endTime = et,
                    rows = mutableListOf(ParsedSlotRow(isDisabled, weekLabel, subject, teacher, room))
                )
                currentSlot = slot
                daysMap.getOrPut(targetDate) { mutableListOf() }.add(slot)
            } else if (tds.size >= 4) {
                val weekLabel = tds[0].text().trim()
                val subject = tds[1].text().trim()
                val teacher = cleanTeacher(tds[2].text())
                val room = cleanField(tds[3].text())
                currentSlot?.rows?.add(ParsedSlotRow(isDisabled, weekLabel, subject, teacher, room))
            } else if (tds.size >= 3) {
                val subject = tds[0].text().trim()
                val teacher = cleanTeacher(tds[1].text())
                val room = cleanField(tds[2].text())
                val weekLabel = currentSlot?.rows?.lastOrNull()?.weekLabel.orEmpty()
                currentSlot?.rows?.add(ParsedSlotRow(isDisabled, weekLabel, subject, teacher, room))
            }
        }

        val safeGroup = cleanField(groupName)

        return daysMap.map { (date, slots) ->
            val dayLessons = mutableListOf<ScrapedLesson>()

            for (slot in slots) {
                val slotRows = slot.rows
                if (slotRows.isEmpty()) continue

                // Check "обе недели"
                if (slotRows.size == 1 && slotRows[0].weekLabel.contains("обе недели", ignoreCase = true)) {
                    val r = slotRows[0]
                    if (r.subject.isNotBlank() && r.subject != "—") {
                        val (subj, type) = parseSubjectAndType(r.subject)
                        dayLessons.add(
                            ScrapedLesson(
                                date = date,
                                number = slot.parnum,
                                startTime = slot.startTime,
                                endTime = slot.endTime,
                                subject = subj,
                                units = listOf(ScrapedUnit(teacher = cleanTeacher(r.teacher), group = safeGroup, room = cleanField(r.room))),
                                type = type,
                                state = ScrapedLessonState.COMMON
                            )
                        )
                    }
                    continue
                }

                // Split week (над чертой / под чертой)
                val activeRows = slotRows.filter { !it.isDisabled }
                val disabledRows = slotRows.filter { it.isDisabled }

                val validActive = activeRows.filter { it.subject.isNotBlank() && it.subject != "—" }

                if (validActive.isNotEmpty()) {
                    val bySubj = validActive.groupBy { it.subject }
                    for ((subjRaw, subjRows) in bySubj) {
                        val (subj, type) = parseSubjectAndType(subjRaw)
                        val units = subjRows.map { r ->
                            ScrapedUnit(teacher = cleanTeacher(r.teacher), group = safeGroup, room = cleanField(r.room))
                        }.distinct()
                        dayLessons.add(
                            ScrapedLesson(
                                date = date,
                                number = slot.parnum,
                                startTime = slot.startTime,
                                endTime = slot.endTime,
                                subject = subj,
                                units = units,
                                type = type,
                                state = ScrapedLessonState.CHANGED
                            )
                        )
                    }
                } else {
                    val validDisabled = disabledRows.filter { it.subject.isNotBlank() && it.subject != "—" }
                    if (validDisabled.isNotEmpty()) {
                        val bySubj = validDisabled.groupBy { it.subject }
                        for ((subjRaw, subjRows) in bySubj) {
                            val (subj, type) = parseSubjectAndType(subjRaw)
                            val units = subjRows.map { r ->
                                ScrapedUnit(teacher = cleanTeacher(r.teacher), group = safeGroup, room = cleanField(r.room))
                            }.distinct()
                            dayLessons.add(
                                ScrapedLesson(
                                    date = date,
                                    number = slot.parnum,
                                    startTime = slot.startTime,
                                    endTime = slot.endTime,
                                    subject = subj,
                                    units = units,
                                    type = type,
                                    state = ScrapedLessonState.REMOVED
                                )
                            )
                        }
                    }
                }
            }

            ScrapedDay(
                date = date,
                scheduleType = ScrapedScheduleType.COMMON,
                lessons = dayLessons.sortedWith(compareBy({ it.startTime }, { it.number }))
            )
        }.sortedBy { it.date }
    }

    private fun parseDayOfWeek(header: String): Int {
        val clean = header.lowercase()
        return when {
            clean.contains("понедельник") -> 1
            clean.contains("вторник") -> 2
            clean.contains("среда") -> 3
            clean.contains("четверг") -> 4
            clean.contains("пятница") -> 5
            clean.contains("суббота") -> 6
            clean.contains("воскресенье") -> 7
            else -> 0
        }
    }

    private fun cleanField(value: String?): String {
        val clean = value?.replace("\u00A0", " ")?.trim().orEmpty()
        return if (clean.isEmpty() || clean == "_" || clean == "—" || clean == "–") "-" else clean
    }

    private fun cleanTeacher(teacher: String?): String {
        val stripped = teacher?.replace(Regex("""\s*\[\d+\]"""), "")?.replace("\u00A0", " ")?.trim().orEmpty()
        return if (stripped.isEmpty() || stripped == "_" || stripped == "—" || stripped == "–") "-" else stripped
    }

    private fun parseSubjectAndType(raw: String): Pair<String, ScrapedLessonType> {
        val regex = Regex("^(.*?)(?:\\s*\\((.*?)\\))?$")
        val match = regex.find(raw.trim())
        val baseSubject = match?.groups?.get(1)?.value?.trim().orEmpty()
        val typeStr = match?.groups?.get(2)?.value?.trim().orEmpty()
        val (type, typeLabel) = when {
            typeStr.contains("ЛЕК", ignoreCase = true) -> ScrapedLessonType.LECTURE to " (лекция)"
            typeStr.contains("ПРАК", ignoreCase = true) || typeStr.contains("ПР", ignoreCase = true) -> ScrapedLessonType.PRACTICE to " (практика)"
            typeStr.contains("ЛАБ", ignoreCase = true) -> ScrapedLessonType.LABORATORY to " (лабораторная)"
            typeStr.contains("КОНС", ignoreCase = true) -> ScrapedLessonType.CONSULTATION to " (консультация)"
            typeStr.contains("ЭКЗ", ignoreCase = true) -> ScrapedLessonType.EXAM to " (экзамен)"
            typeStr.contains("ЗАЧ", ignoreCase = true) -> ScrapedLessonType.CREDIT to " (зачёт)"
            typeStr.isNotBlank() -> ScrapedLessonType.COMMON to " ($typeStr)"
            else -> ScrapedLessonType.COMMON to ""
        }
        val subject = if (typeLabel.isNotEmpty()) "$baseSubject$typeLabel" else baseSubject
        return subject to type
    }

    private fun parseTimeRange(range: String): Pair<LocalTime, LocalTime> {
        val parts = range.split("-", "—", "–").map { it.trim() }
        val start = parts.getOrNull(0)?.let { parseTime(it) } ?: LocalTime(8, 15)
        val end = parts.getOrNull(1)?.let { parseTime(it) } ?: LocalTime(9, 45)
        return start to end
    }

    private fun parseTime(str: String): LocalTime {
        return try {
            val parts = str.split(":", ".").map { it.trim().toInt() }
            LocalTime(parts[0], parts[1])
        } catch (_: Exception) {
            LocalTime(8, 15)
        }
    }
}

fun htmlSchedulePipeline(block: HtmlSchedulePipelineBuilder.() -> Unit = {}): HtmlSchedulePipeline {
    val builder = HtmlSchedulePipelineBuilder().apply(block)
    return HtmlSchedulePipeline(builder)
}
