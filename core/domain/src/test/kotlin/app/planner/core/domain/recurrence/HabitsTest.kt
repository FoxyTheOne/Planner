package app.planner.core.domain.recurrence

import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.LocalDate

/** Привычки: какие «таблетки» сегодня и мягкая статистика — что считается «сделано» и за какой период. */
class HabitsTest {

    private val today = LocalDate.of(2026, 10, 15)

    private fun water(start: LocalDate = today.minusMonths(3)) =
        RecurrenceRule(id = 1, title = "Вода", repeat = Repeat.Daily, startDate = start, isHabit = true)

    /** Отметка привычки на день [date] — как её сохраняет completeOccurrence. */
    private fun doneOn(rule: RecurrenceRule, date: LocalDate, status: TaskStatus = TaskStatus.DONE) =
        Recurrence.toTask(Recurrence.occurrence(rule, date)!!, status)

    @Test
    fun `habits of the day include done ones and skip plain rules`() {
        val water = water()
        val reading = water.copy(id = 2, title = "Читать")
        val gym = water.copy(id = 3, repeat = Repeat.OnWeekdays(setOf(MONDAY))) // сегодня четверг — не её день
        val pool = water.copy(id = 4, title = "Бассейн", isHabit = false) // дело, не привычка

        val habits = HabitDay.on(listOf(water, reading, gym, pool), today, listOf(doneOn(water, today)))

        assertEquals(listOf("Вода", "Читать"), habits.map { it.rule.title })
        assertTrue(habits[0].isDone) // отмеченная привычка не пропала, а показана отмеченной
        assertFalse(habits[1].isDone)
    }

    @Test
    fun `counts done days of the last 30 days only`() {
        val rule = water()
        val instances = listOf(
            doneOn(rule, today),
            doneOn(rule, today.minusDays(29)), // первый день периода — считается
            doneOn(rule, today.minusDays(30)), // уже за пределами
            doneOn(rule, today.minusDays(3), TaskStatus.SKIPPED), // пропуск — не «сделано»
            doneOn(rule.copy(id = 2), today.minusDays(1)), // другая привычка
        )

        val history = HabitHistory.of(rule, instances, today)

        assertEquals(2, history.doneCount)
        assertEquals(30, history.days.size)
        assertEquals(30, history.planned.size) // каждый день
        assertFalse(history.startedRecently)
    }

    @Test
    fun `new habit counts from its start`() {
        val history = HabitHistory.of(water(start = today.minusDays(2)), emptyList(), today)

        assertEquals(today.minusDays(2), history.from)
        assertEquals(3, history.days.size)
        assertTrue(history.startedRecently)
    }

    @Test
    fun `paused and not scheduled days are not planned`() {
        val mondays = water().copy(
            repeat = Repeat.OnWeekdays(setOf(MONDAY)),
            pauses = listOf(Pause(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11))),
        )

        val history = HabitHistory.of(mondays, emptyList(), today)

        // Понедельники 16.09–15.10: 21, 28 сентября, 5 и 12 октября; 5-е — на паузе
        assertEquals(
            setOf(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 12)),
            history.planned,
        )
    }
}
