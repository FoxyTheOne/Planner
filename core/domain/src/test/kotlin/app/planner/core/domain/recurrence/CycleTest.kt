package app.planner.core.domain.recurrence

import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
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
        anchorWeekStart = oct5,
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

    // ---------- Неделя цикла с воскресенья ----------

    /** Два одинаковых цикла на 2 недели: один считает недели с понедельника, другой — с воскресенья. */
    private val twoWeeks = listOf(
        mapOf(SUNDAY to "неделя 1", WEDNESDAY to "неделя 1"),
        mapOf(SUNDAY to "неделя 2", WEDNESDAY to "неделя 2"),
    )
    private val fromMonday = Repeat.Cycle(twoWeeks, anchorWeekStart = oct5) // пн 5 октября
    private val fromSunday = Repeat.Cycle(twoWeeks, anchorWeekStart = oct5.minusDays(1), weekStart = SUNDAY) // вс 4

    private fun on(cycle: Repeat.Cycle, date: LocalDate, vararg pauses: Pause) = Recurrence.occurrence(
        RecurrenceRule(title = "Т", repeat = cycle, startDate = oct5.minusDays(1), pauses = pauses.toList()),
        date,
    )?.details

    @Test
    fun `sunday belongs to a different cycle week depending on week start`() {
        val sun11 = LocalDate.of(2026, 10, 11)
        // С понедельника: вс 11 — последний день недели пн 5…вс 11, то есть неделя 1
        assertEquals("неделя 1", on(fromMonday, sun11))
        // С воскресенья: вс 11 — первый день недели вс 11…сб 17, то есть уже неделя 2
        assertEquals("неделя 2", on(fromSunday, sun11))
    }

    @Test
    fun `weekdays other than sunday are the same for both week starts`() {
        val wed7 = LocalDate.of(2026, 10, 7)
        assertEquals(on(fromMonday, wed7), on(fromSunday, wed7)) // обе — неделя 1
        assertEquals(on(fromMonday, wed7.plusWeeks(1)), on(fromSunday, wed7.plusWeeks(1))) // обе — неделя 2
    }

    @Test
    fun `whole paused week is counted by the cycle own week`() {
        // Пауза вс 11 … сб 17 — это целая неделя для цикла с воскресенья, но не для цикла с понедельника
        val pause = Pause(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 17))
        val wed21 = LocalDate.of(2026, 10, 21)

        // С воскресенья неделя целиком на паузе и не считается: после неё снова неделя 2
        assertEquals("неделя 2", on(fromSunday, wed21, pause))
        // С понедельника целой недели на паузе нет — счёт идёт как обычно: 21-го неделя 1 (по кругу)
        assertEquals("неделя 1", on(fromMonday, wed21, pause))
    }

    @Test
    fun `set current week keeps the cycle week start`() {
        val sat17 = LocalDate.of(2026, 10, 17)

        val fixed = Recurrence.setCurrentWeek(fromSunday, today = sat17, weekNumber = 1)

        assertEquals(SUNDAY, fixed.weekStart)
        assertEquals(LocalDate.of(2026, 10, 11), fixed.anchorWeekStart) // воскресенье этой недели
    }

    @Test(expected = IllegalArgumentException::class)
    fun `anchor week must start on the cycle week start`() {
        // Цикл с понедельника, а опорная неделя — со вторника: так быть не может
        Repeat.Cycle(weeks = watering.weeks, anchorWeekStart = oct5.plusDays(1))
    }
}
