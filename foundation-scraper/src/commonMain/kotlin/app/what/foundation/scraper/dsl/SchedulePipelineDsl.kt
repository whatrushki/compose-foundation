package app.what.foundation.scraper.dsl

import app.what.foundation.scraper.cleaner.HomoglyphCleaner
import app.what.foundation.scraper.cleaner.ParsedRoomInfo
import app.what.foundation.scraper.cleaner.RoomCleaner
import app.what.foundation.scraper.cleaner.TeacherNameCleaner
import app.what.foundation.scraper.engine.CalendarProjector
import app.what.foundation.scraper.engine.ProjectedDay
import app.what.foundation.scraper.engine.ReconciliationEngine
import app.what.foundation.scraper.model.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class BellsConfigBuilder {
    private val timeSlots = mutableListOf<LessonTimeSlot>()
    private var typeDetector: ((List<ScrapedLesson>) -> ScrapedScheduleType)? = null

    fun slot(number: Int, start: LocalTime, end: LocalTime) {
        timeSlots.add(LessonTimeSlot(number, start, end))
    }

    fun slots(list: List<LessonTimeSlot>) {
        timeSlots.addAll(list)
    }

    fun detectScheduleType(detector: (List<ScrapedLesson>) -> ScrapedScheduleType) {
        typeDetector = detector
    }

    fun buildSlots(): List<LessonTimeSlot> = timeSlots.toList()
    fun buildDetector(): (List<ScrapedLesson>) -> ScrapedScheduleType =
        typeDetector ?: { ScrapedScheduleType.COMMON }
}

class TextSanitizerBuilder {
    var unifyHomoglyphs: Boolean = true
    var stripIdSuffix: Boolean = true

    fun cleanTeacher(name: String?): String {
        if (name.isNullOrBlank()) return "-"
        var res = name.replace('\u00A0', ' ').trim()
        if (stripIdSuffix) {
            res = TeacherNameCleaner.stripIdSuffix(res)
        }
        if (unifyHomoglyphs) {
            res = HomoglyphCleaner.unifyToCyrillic(res)
        }
        return res.ifEmpty { "-" }
    }
}

class RoomParserConfigBuilder {
    var defaultBuilding: String = "1"
    private var customParser: ((String) -> ParsedRoomInfo)? = null

    fun parseUsing(parser: (String) -> ParsedRoomInfo) {
        customParser = parser
    }

    fun parseSlashBuilding() {
        customParser = { raw -> RoomCleaner.parseRoomSlashBuilding(raw, defaultBuilding) }
    }

    fun parseDashBuilding(defaultBuildingName: String = "Главный") {
        customParser = { raw -> RoomCleaner.parseBuildingDashRoom(raw, defaultBuildingName) }
    }

    fun parsePrefixBuilding(prefixMap: Map<Char, String>) {
        customParser = { raw -> RoomCleaner.parsePrefixBuilding(raw, prefixMap, defaultBuilding) }
    }

    fun parse(raw: String): ParsedRoomInfo =
        customParser?.invoke(raw) ?: RoomCleaner.parseRoomSlashBuilding(raw, defaultBuilding)
}

class ReconcileConfigBuilder {
    private var subjectResolver: ((teacher: String, group: String) -> String?)? = null

    fun resolveMissingSubject(resolver: (teacher: String, group: String) -> String?) {
        subjectResolver = resolver
    }

    fun reconcile(
        baseLessons: List<ScrapedLesson>,
        replacements: List<ScrapedLesson>,
        timeSlots: List<LessonTimeSlot>
    ): List<ScrapedLesson> =
        ReconciliationEngine.applyReplacements(baseLessons, replacements, timeSlots, subjectResolver)
}

class CalendarProjectionBuilder {
    var baselineDate: LocalDate? = null
    var projectWeeks: Int = 4
    var skipSunday: Boolean = true

    fun project(): List<ProjectedDay> =
        CalendarProjector.projectDates(baselineDate, projectWeeks, skipSunday)
}

class SchedulePipeline(
    val timeSlots: List<LessonTimeSlot>,
    val scheduleTypeDetector: (List<ScrapedLesson>) -> ScrapedScheduleType,
    val sanitizer: TextSanitizerBuilder,
    val roomParser: RoomParserConfigBuilder,
    val reconciler: ReconcileConfigBuilder,
    val calendarProjection: CalendarProjectionBuilder
) {
    fun reconcile(base: List<ScrapedLesson>, replacements: List<ScrapedLesson>): List<ScrapedLesson> =
        reconciler.reconcile(base, replacements, timeSlots)
}

class SchedulePipelineBuilder {
    private val bellsBuilder = BellsConfigBuilder()
    private val sanitizerBuilder = TextSanitizerBuilder()
    private val roomParserBuilder = RoomParserConfigBuilder()
    private val reconcileBuilder = ReconcileConfigBuilder()
    private val calendarBuilder = CalendarProjectionBuilder()

    fun bells(block: BellsConfigBuilder.() -> Unit) {
        bellsBuilder.apply(block)
    }

    fun sanitizer(block: TextSanitizerBuilder.() -> Unit) {
        sanitizerBuilder.apply(block)
    }

    fun roomParser(block: RoomParserConfigBuilder.() -> Unit) {
        roomParserBuilder.apply(block)
    }

    fun reconcile(block: ReconcileConfigBuilder.() -> Unit) {
        reconcileBuilder.apply(block)
    }

    fun calendarProjection(block: CalendarProjectionBuilder.() -> Unit) {
        calendarBuilder.apply(block)
    }

    fun build(): SchedulePipeline = SchedulePipeline(
        timeSlots = bellsBuilder.buildSlots(),
        scheduleTypeDetector = bellsBuilder.buildDetector(),
        sanitizer = sanitizerBuilder,
        roomParser = roomParserBuilder,
        reconciler = reconcileBuilder,
        calendarProjection = calendarBuilder
    )
}

fun schedulePipeline(block: SchedulePipelineBuilder.() -> Unit): SchedulePipeline =
    SchedulePipelineBuilder().apply(block).build()
