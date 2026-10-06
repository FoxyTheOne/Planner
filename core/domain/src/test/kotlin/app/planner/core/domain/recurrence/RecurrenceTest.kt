package app.planner.core.domain.recurrence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

/**
 * Тесты движка повторений. Даты настоящие: 5 октября 2026 — понедельник.
 * Удобно сверять с календарём.
 */
class RecurrenceTest {

    private val monday = LocalDate.of(2026, 10, 5)

    private fun rule(repeat: Repeat, start: LocalDate = monday, end: LocalDate? = null) =
        RecurrenceRule(title = "Тест", repeat = repeat, startDate = start, endDate = end)

    @Test
    fun `daily occurs every day of the week`() {
        val days = Recurrence.occurrences(rule(Repeat.Daily), monday, monday.plusDays(6))
        assertEquals(7, days.size)
    }

    @Test
    fun `weekdays rule picks only chosen days`() {
        val r = rule(Repeat.OnWeekdays(setOf(MONDAY, WEDNESDAY, FRIDAY)))

        val days = Recurrence.occurrences(r, monday, monday.plusDays(6))

        // пн 5, ср 7, пт 9 октября
        assertEquals(listOf(monday, monday.plusDays(2), monday.plusDays(4)), days)
    }

    @Test
    fun `every n days counts from the start date`() {
        // «Через день» с понедельника: пн, ср, пт, вс, вт…
        val r = rule(Repeat.EveryNDays(2))

        val days = Recurrence.occurrences(r, monday, monday.plusDays(8))

        assertEquals(listOf(0L, 2, 4, 6, 8).map { monday.plusDays(it) }, days)
    }

    @Test
    fun `every n days works when the asked range starts later`() {
        // Правило началось давно — считать всё равно надо от его начала, а не от начала недели
        val r = rule(Repeat.EveryNDays(3), start = monday.minusDays(10))

        // От minusDays(10) каждые 3 дня: …, minusDays(1), plusDays(2), plusDays(5)
        val days = Recurrence.occurrences(r, monday, monday.plusDays(6))

        assertEquals(listOf(monday.plusDays(2), monday.plusDays(5)), days)
    }

    @Test
    fun `nothing before start and after end`() {
        val r = rule(Repeat.Daily, start = monday.plusDays(2), end = monday.plusDays(4))

        assertFalse(Recurrence.occursOn(r, monday.plusDays(1))) // до начала
        assertTrue(Recurrence.occursOn(r, monday.plusDays(2))) // первый день — включительно
        assertTrue(Recurrence.occursOn(r, monday.plusDays(4))) // последний день — включительно
        assertFalse(Recurrence.occursOn(r, monday.plusDays(5))) // после конца
    }

    @Test(expected = IllegalArgumentException::class)
    fun `weekdays rule without days is not allowed`() {
        // require в init не даст создать «по дням недели» без единого дня
        Repeat.OnWeekdays(emptySet())
    }
}
