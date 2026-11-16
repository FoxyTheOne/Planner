package app.planner.core.domain.recurrence

import app.planner.core.domain.model.IntervalUnit
import app.planner.core.domain.model.Medication
import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Повторение «после выполнения»: УЗИ раз в год — от того дня, когда его сделали на самом деле. */
class AfterDoneTest {

    private val utc = ZoneOffset.UTC
    private val start = LocalDate.of(2026, 3, 10)

    private fun ultrasound(repeat: Repeat = Repeat.AfterDone(1, IntervalUnit.YEARS)) =
        RecurrenceRule(id = 7, title = "УЗИ", repeat = repeat, startDate = start, listId = 2)

    /** Задача «следующий раз», отмеченная сделанной в день [doneOn]. */
    private fun done(planned: LocalDate, doneOn: LocalDate) = Task(
        id = 1, title = "УЗИ", date = planned, afterRuleId = 7,
        status = TaskStatus.DONE, doneAt = doneOn.atTime(12, 0).toInstant(utc),
    )

    @Test
    fun `first time is on the start day`() {
        val task = Recurrence.afterDoneTaskDue(ultrasound(), emptyList(), utc)!!

        assertEquals(start, task.date)
        assertEquals(7L, task.afterRuleId)
        assertEquals(2L, task.listId) // список — как у правила
        assertNull(task.ruleId) // это обычная задача, а не день повторения: она копится в «Незавершённом»
    }

    @Test
    fun `nothing new while the next time is still open`() {
        val open = Task(id = 1, title = "УЗИ", date = start, afterRuleId = 7)

        assertNull(Recurrence.afterDoneTaskDue(ultrasound(), listOf(open), utc))
    }

    @Test
    fun `next time counts from the day it was actually done`() {
        // Собиралась 10 марта, сделала 12 июня — следующее через год от 12 июня
        val history = listOf(done(planned = start, doneOn = LocalDate.of(2026, 6, 12)))

        val next = Recurrence.afterDoneTaskDue(ultrasound(), history, utc)!!

        assertEquals(LocalDate.of(2027, 6, 12), next.date)
    }

    @Test
    fun `the latest time wins`() {
        val history = listOf(
            done(planned = start, doneOn = LocalDate.of(2026, 3, 10)),
            done(planned = LocalDate.of(2027, 3, 10), doneOn = LocalDate.of(2027, 5, 1)).copy(id = 2),
        )

        assertEquals(LocalDate.of(2028, 5, 1), Recurrence.afterDoneTaskDue(ultrasound(), history, utc)!!.date)
    }

    @Test
    fun `skipped time counts from its own day`() {
        val skipped = Task(id = 1, title = "УЗИ", date = start, afterRuleId = 7, status = TaskStatus.SKIPPED)

        assertEquals(start.plusYears(1), Recurrence.afterDoneTaskDue(ultrasound(), listOf(skipped), utc)!!.date)
    }

    @Test
    fun `days, weeks and months are counted too`() {
        val doneOn = LocalDate.of(2026, 1, 31)
        val history = listOf(done(planned = doneOn, doneOn = doneOn))

        fun next(repeat: Repeat) = Recurrence.afterDoneTaskDue(ultrasound(repeat), history, utc)!!.date

        assertEquals(LocalDate.of(2026, 2, 10), next(Repeat.AfterDone(10, IntervalUnit.DAYS)))
        assertEquals(LocalDate.of(2026, 3, 14), next(Repeat.AfterDone(6, IntervalUnit.WEEKS)))
        // В феврале нет 31-го — получается последний день февраля
        assertEquals(LocalDate.of(2026, 2, 28), next(Repeat.AfterDone(1, IntervalUnit.MONTHS)))
    }

    @Test
    fun `next time that falls on a pause moves to the day after it`() {
        val rule = ultrasound().copy(pauses = listOf(Pause(start.minusDays(5), start.plusDays(3))))

        assertEquals(start.plusDays(4), Recurrence.afterDoneTaskDue(rule, emptyList(), utc)!!.date)
    }

    @Test
    fun `open ended pause and finished rule create nothing`() {
        assertNull(Recurrence.afterDoneTaskDue(ultrasound().copy(pauses = listOf(Pause(start))), emptyList(), utc))
        assertNull(Recurrence.afterDoneTaskDue(ultrasound().copy(endDate = start.minusDays(1)), emptyList(), utc))
    }

    @Test
    fun `task keeps the time and reminder fits it`() {
        val rule = ultrasound().copy(time = LocalTime.of(10, 30))

        assertEquals(LocalTime.of(10, 30), Recurrence.afterDoneTaskDue(rule, emptyList(), utc)!!.time)
    }

    @Test
    fun `after done has no calendar days`() {
        assertFalse(Recurrence.occursOn(ultrasound(), start))
    }

    @Test
    fun `after done is never a habit`() {
        val draft = Task(title = "Стрижка", date = start)

        val rule = Recurrence.ruleFromDraft(draft, Repeat.AfterDone(6, IntervalUnit.WEEKS), start, isHabit = true)

        assertFalse(rule.isHabit)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `medication cannot repeat after done`() {
        Medication(
            name = "Витамин D", times = listOf(LocalTime.of(9, 0)), startDate = start,
            repeat = Repeat.AfterDone(1, IntervalUnit.DAYS),
        )
    }
}
