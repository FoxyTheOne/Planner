package app.planner.core.domain.recurrence

import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

/**
 * Тесты цикла полива (паузы — в PauseTest).
 * Календарь октября 2026: пн 5, ср 7, сб 10 · пн 12, ср 14, сб 17 · пн 19 · пн 26 · пн 2 ноября.
 */
class CycleTest {

    private val oct5 = LocalDate.of(2026, 10, 5) // понедельник, начало «недели 1»

    /** Цикл полива на 4 недели: среда — каждую неделю, суббота — только во 2-й и 4-й. */
    private val watering = Repeat.Cycle(
        weeks = listOf(
            mapOf(WEDNESDAY to "фикус, монстера"),
            mapOf(WEDNESDAY to "фикус", SATURDAY to "подкормка"),
            mapOf(WEDNESDAY to "фикус, монстера"),
            mapOf(WEDNESDAY to "фикус", SATURDAY to "пересадка, если пора"),
        ),
        anchorMonday = oct5,
    )

    private fun rule() = RecurrenceRule(title = "Полив", repeat = watering, startDate = oct5)

    /** Что выпадает на день: текст дел или null. */
    private fun detailsOn(date: LocalDate, r: RecurrenceRule = rule()) =
        Recurrence.occurrence(r, date)?.details

    @Test
    fun `each week of the cycle has its own tasks`() {
        assertEquals("фикус, монстера", detailsOn(LocalDate.of(2026, 10, 7))) // ср, неделя 1
        assertNull(detailsOn(LocalDate.of(2026, 10, 10))) // сб, неделя 1 — по субботам в 1-й неделе ничего
        assertEquals("фикус", detailsOn(LocalDate.of(2026, 10, 14))) // ср, неделя 2
        assertEquals("подкормка", detailsOn(LocalDate.of(2026, 10, 17))) // сб, неделя 2
        assertEquals("пересадка, если пора", detailsOn(LocalDate.of(2026, 10, 31))) // сб, неделя 4
    }

    @Test
    fun `cycle starts over after the last week`() {
        // 2 ноября — через 4 недели после 5 октября, значит снова неделя 1
        val nov2 = LocalDate.of(2026, 11, 2)
        assertEquals(0, Recurrence.cycleWeekIndex(watering, nov2))
        assertEquals("фикус, монстера", detailsOn(nov2.plusDays(2))) // ср 4 ноября
        assertNull(detailsOn(nov2.plusDays(5))) // сб 7 ноября — как в неделе 1, ничего
    }

    @Test
    fun `week index is right for any day of the week, not only monday`() {
        // Воскресенье 18 октября — ещё неделя 2 (понедельник этой недели — 12 октября)
        assertEquals(1, Recurrence.cycleWeekIndex(watering, LocalDate.of(2026, 10, 18)))
    }

    @Test
    fun `dates before the first week go round the circle backwards`() {
        // Неделя перед «неделей 1» — это неделя 4 (floorMod, а не %)
        assertEquals(3, Recurrence.cycleWeekIndex(watering, LocalDate.of(2026, 9, 30)))
    }

    @Test
    fun `set current week remembers this week as week N`() {
        // В среду 14 октября по расчёту идёт неделя 2, а человек говорит: «на самом деле 3»
        val wed14 = LocalDate.of(2026, 10, 14)

        val fixed = Recurrence.setCurrentWeek(watering, today = wed14, weekNumber = 3)

        assertEquals(2, Recurrence.cycleWeekIndex(fixed, wed14)) // теперь неделя 3 (индекс 2)
        assertEquals(3, Recurrence.cycleWeekIndex(fixed, wed14.plusWeeks(1))) // дальше — неделя 4
        assertEquals(0, Recurrence.cycleWeekIndex(fixed, wed14.plusWeeks(2))) // и снова неделя 1
        assertEquals(watering.weeks, fixed.weeks) // сами дела не тронуты
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cycle must start on monday`() {
        Repeat.Cycle(weeks = watering.weeks, anchorMonday = oct5.plusDays(1))
    }
}
