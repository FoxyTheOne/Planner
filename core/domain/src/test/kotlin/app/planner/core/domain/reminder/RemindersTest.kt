package app.planner.core.domain.reminder

import app.planner.core.domain.model.PartOfDay
import app.planner.core.domain.model.Reminder
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `reminder at a fixed hour is never later than the task itself`() {
        val atNine = Reminder.AtTime(LocalTime.of(9, 0))
        val gift = Task(title = "Купить подарок", date = monday, reminder = atNine)

        // Дело в 15:00 — напоминание утром, в 9:00, как просили
        assertEquals(LocalDateTime.of(2026, 10, 19, 9, 0), Reminders.fireAt(gift.copy(time = LocalTime.of(15, 0))))
        // Дело в 8:00 — 9:00 было бы уже после начала, поэтому напоминаем в момент начала
        assertEquals(LocalDateTime.of(2026, 10, 19, 8, 0), Reminders.fireAt(gift.copy(time = LocalTime.of(8, 0))))
    }

    @Test
    fun `ask about the reminder only when the time of day changes`() {
        val morning = Task(
            title = "Подарок", date = monday, partOfDay = PartOfDay.MORNING,
            reminder = Reminder.AtTime(LocalTime.of(9, 0)),
        )

        // Утро → утро другого дня: «в 9:00» подходит, не спрашиваем
        assertFalse(Reminders.askAfterMove(morning, morning.copy(date = monday.plusDays(1))))
        // Утро → вечер, или поставили точное время — спрашиваем
        assertTrue(Reminders.askAfterMove(morning, morning.copy(partOfDay = PartOfDay.EVENING)))
        assertTrue(Reminders.askAfterMove(morning, morning.copy(partOfDay = null, time = LocalTime.of(8, 0))))
        // «За 30 минут» едет вместе с задачей — спрашивать нечего
        val doctorMoved = doctor.copy(time = LocalTime.of(15, 0))
        assertFalse(Reminders.askAfterMove(doctor, doctorMoved))
        // Напоминание поменяли сами вместе с переносом — человек уже решил
        assertFalse(
            Reminders.askAfterMove(
                morning,
                morning.copy(partOfDay = PartOfDay.EVENING, reminder = Reminder.AtTime(LocalTime.of(18, 0))),
            ),
        )
        // Вернули в список (без даты) — не к месту
        assertFalse(Reminders.askAfterMove(morning, morning.copy(date = null, partOfDay = null)))
    }

    @Test
    fun `no reminder when nothing to remind about`() {
        assertNull(Reminders.fireAt(doctor.copy(reminder = null)))
        assertNull(Reminders.fireAt(doctor.copy(date = null))) // в списке, без даты
        assertNull(Reminders.fireAt(doctor.copy(status = TaskStatus.DONE)))
        assertNull(Reminders.fireAt(doctor.copy(time = null))) // «за 30 минут» — а времени нет
    }

    @Test
    fun `reminder that does not fit the new date or time is dropped`() {
        val before = Reminder.BeforeTime(30)
        val atNine = Reminder.AtTime(LocalTime.of(9, 0))
        val ten = LocalTime.of(10, 0)

        assertEquals(before, Reminders.fitting(before, monday, ten))
        assertNull(Reminders.fitting(before, monday, time = null)) // время убрали — «за 30 минут» не от чего считать
        assertNull(Reminders.fitting(atNine, date = null, time = null)) // дату убрали — задача в списке
        assertEquals(atNine, Reminders.fitting(atNine, monday, ten)) // время добавили — «в 9:00» по-прежнему годится
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
