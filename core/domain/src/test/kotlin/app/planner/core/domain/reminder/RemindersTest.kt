package app.planner.core.domain.reminder

import app.planner.core.domain.model.Reminder
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Когда срабатывает напоминание задачи. 19 октября 2026 — понедельник. */
class RemindersTest {

    private val monday = LocalDate.of(2026, 10, 19)
    private val doctor = Task(
        title = "Врач", date = monday, time = LocalTime.of(10, 30),
        reminder = Reminder.BeforeTime(30),
    )

    @Test
    fun `before time counts back from the task time`() {
        assertEquals(LocalDateTime.of(2026, 10, 19, 10, 0), Reminders.fireAt(doctor))
        // Перенесли врача на 11:00 — напоминание «за 30 минут» сдвинулось вместе с ним
        assertEquals(LocalDateTime.of(2026, 10, 19, 10, 30), Reminders.fireAt(doctor.copy(time = LocalTime.of(11, 0))))
    }

    @Test
    fun `before time can cross midnight`() {
        val early = doctor.copy(time = LocalTime.of(0, 20), reminder = Reminder.BeforeTime(60))
        assertEquals(LocalDateTime.of(2026, 10, 18, 23, 20), Reminders.fireAt(early))
    }

    @Test
    fun `task without time reminds at the chosen hour of its day`() {
        val gift = Task(title = "Купить подарок", date = monday, reminder = Reminder.AtTime(LocalTime.of(9, 0)))
        assertEquals(LocalDateTime.of(2026, 10, 19, 9, 0), Reminders.fireAt(gift))
    }

    @Test
    fun `no reminder when nothing to remind about`() {
        assertNull(Reminders.fireAt(doctor.copy(reminder = null)))
        assertNull(Reminders.fireAt(doctor.copy(date = null))) // в списке, без даты
        assertNull(Reminders.fireAt(doctor.copy(status = TaskStatus.DONE)))
        assertNull(Reminders.fireAt(doctor.copy(time = null))) // «за 30 минут» — а времени нет
    }

    @Test
    fun `upcoming skips the past and goes in order of time`() {
        val now = LocalDateTime.of(2026, 10, 19, 9, 30)
        val gift = Task(title = "Подарок", date = monday, reminder = Reminder.AtTime(LocalTime.of(9, 0))) // уже прошло
        val call = Task(title = "Позвонить", date = monday, reminder = Reminder.AtTime(LocalTime.of(18, 0)))

        val upcoming = Reminders.upcoming(listOf(call, gift, doctor), now)

        assertEquals(listOf("Врач", "Позвонить"), upcoming.map { it.first.title })
    }
}
