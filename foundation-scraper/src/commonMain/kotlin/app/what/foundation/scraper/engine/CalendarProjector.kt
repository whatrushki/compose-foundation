package app.what.foundation.scraper.engine

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class ProjectedDay(
    val date: LocalDate,
    val isoDayOfWeek: Int,
    val weekNumber: Int,
    val isEvenWeek: Boolean,
    val weekParityMark: String // e.g. "plus", "minus", "upper", "lower"
)

object CalendarProjector {

    /**
     * Генерирует список дат на [projectWeeks] вперед, начиная с понедельника текущей недели,
     * сопоставляя каждую дату с четностью относительно [baselineDate].
     */
    fun projectDates(
        baselineDate: LocalDate? = null,
        projectWeeks: Int = 4,
        skipSunday: Boolean = true
    ): List<ProjectedDay> {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val base = baselineDate ?: run {
            if (now.monthNumber >= 9) LocalDate(now.year, 9, 1)
            else LocalDate(now.year, 2, 1)
        }

        val baselineMonday = base.minus(DatePeriod(days = base.dayOfWeek.isoDayNumber - 1))
        val currentMonday = now.minus(DatePeriod(days = now.dayOfWeek.isoDayNumber - 1))

        val daysCount = projectWeeks * 7
        val result = mutableListOf<ProjectedDay>()

        for (offset in 0 until daysCount) {
            val targetDate = currentMonday.plus(DatePeriod(days = offset))
            val isoDay = targetDate.dayOfWeek.isoDayNumber
            if (skipSunday && isoDay > 6) continue

            val targetMonday = targetDate.minus(DatePeriod(days = isoDay - 1))
            val weeksDiff = (targetMonday.toEpochDays() - baselineMonday.toEpochDays()) / 7
            val isEven = (weeksDiff % 2L == 0L || weeksDiff % 2L == -0L)
            val mark = if (isEven) "minus" else "plus"

            result.add(
                ProjectedDay(
                    date = targetDate,
                    isoDayOfWeek = isoDay,
                    weekNumber = weeksDiff.toInt(),
                    isEvenWeek = isEven,
                    weekParityMark = mark
                )
            )
        }

        return result
    }
}
