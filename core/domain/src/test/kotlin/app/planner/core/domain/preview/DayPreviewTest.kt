package app.planner.core.domain.preview

import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import app.planner.core.domain.recurrence.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Что ждёт в день: дела со временем по порядку и «и ещё N» — для «Завтра» и вечерней сводки. */
class DayPreviewTest {

    private val tomorrow = LocalDate.of(2026, 11, 25)
    private val pool = RecurrenceRule(
        id = 1, title = "Бассейн", repeat = Repeat.Daily, startDate = tomorrow.minusDays(30), time = LocalTime.of(18, 0),
    )
    private val water = RecurrenceRule(
        id = 2, title = "Вода", repeat = Repeat.Daily, startDate = tomorrow.minusDays(30), isHabit = true,
    )

    @Test
    fun `timed tasks and repeat days go by time, the rest is counted`() {
        val tasks = listOf(
            Task(id = 1, title = "Врач", date = tomorrow, time = LocalTime.of(10, 30)),
            Task(id = 2, title = "Купить лампу", date = tomorrow),
            Task(id = 3, title = "Позвонить", date = tomorrow),
        )

        val preview = DayPreview.on(tomorrow, tasks, listOf(pool), instances = emptyList())

        assertEquals(listOf("Врач", "Бассейн"), preview.timed.map { it.title })
        assertEquals(4, preview.total)
        assertEquals(2, preview.untimedCount)
    }

    @Test
    fun `habits and things done in advance are not counted`() {
        val habitMark = Recurrence.toTask(Recurrence.occurrence(water, tomorrow)!!, TaskStatus.DONE)
        val doneEarly = Task(id = 5, title = "Врач", date = tomorrow, time = LocalTime.of(9, 0), status = TaskStatus.DONE)

        val preview = DayPreview.on(tomorrow, listOf(habitMark, doneEarly), listOf(water), instances = listOf(habitMark))

        assertEquals(0, preview.total)
        assertEquals(emptyList<Task>(), preview.timed)
    }

    @Test
    fun `repeat day moved to another day does not come back by the rule`() {
        val moved = Recurrence.toTask(Recurrence.occurrence(pool, tomorrow)!!, TaskStatus.OPEN)
            .copy(id = 7, date = tomorrow.plusDays(1))

        // Перенесённый день — задача на послезавтра: в задачах завтрашнего дня его нет, а в instances — есть
        val preview = DayPreview.on(tomorrow, emptyList(), listOf(pool), instances = listOf(moved))

        assertEquals(0, preview.total)
    }
}
