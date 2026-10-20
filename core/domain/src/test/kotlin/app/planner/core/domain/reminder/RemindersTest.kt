package app.planner.core.domain.reminder

import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.PartOfDay
import app.planner.core.domain.model.Reminder
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
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

    @Test
    fun `snoozed reminder rings again later, until the task changes`() {
        val fired = LocalDateTime.of(2026, 10, 19, 10, 0) // врач в 10:30, напомнили в 10:00
        val now = fired.plusMinutes(1)
        val snooze = Snooze(original = fired, until = fired.plusMinutes(10))

        // Отложили — будильник через 10 минут, хотя обычное время уже прошло
        assertEquals(snooze.until, Reminders.alarmAt(doctor, now, snooze))
        // Без отложенного прошедшее напоминание не ставится
        assertNull(Reminders.alarmAt(doctor, now))
        // Отложенное время прошло — больше не звоним
        assertNull(Reminders.alarmAt(doctor, snooze.until, snooze))
        // Задачу перенесли на 15:00 — отложенное забыто, действует новое «за 30 минут»: 14:30
        assertEquals(
            LocalDateTime.of(2026, 10, 19, 14, 30),
            Reminders.alarmAt(doctor.copy(time = LocalTime.of(15, 0)), now, snooze),
        )
        // Задачу отметили — не звоним совсем
        assertNull(Reminders.alarmAt(doctor.copy(status = TaskStatus.DONE), now, snooze))
    }

    // --- Повторения ---

    private val water = RecurrenceRule(
        id = 1, title = "Полить цветы", repeat = Repeat.Daily, startDate = monday.minusDays(7),
        time = LocalTime.of(10, 0), reminder = Reminder.BeforeTime(0),
    )

    @Test
    fun `repeat reminds on its next day that is still ahead`() {
        // Понедельник, 9:00 — сегодняшнее напоминание в 10:00 ещё впереди
        assertEquals(
            monday to LocalDateTime.of(2026, 10, 19, 10, 0),
            Reminders.nextForRule(water, emptyList(), monday.atTime(9, 0)),
        )
        // Уже 11:00 — сегодняшнее прошло, следующее завтра
        assertEquals(
            monday.plusDays(1) to LocalDateTime.of(2026, 10, 20, 10, 0),
            Reminders.nextForRule(water, emptyList(), monday.atTime(11, 0)),
        )
    }

    @Test
    fun `day already done or moved is skipped`() {
        // Сегодняшний полив отметили (или перенесли) — он стал задачей; напоминание — о завтрашнем
        val doneToday = Task(title = "Полить цветы", date = monday, ruleId = 1, occurrenceDate = monday, status = TaskStatus.DONE)
        assertEquals(
            monday.plusDays(1),
            Reminders.nextForRule(water, listOf(doneToday), monday.atTime(9, 0))?.first,
        )
    }

    @Test
    fun `untimed repeat on weekdays reminds at the chosen hour of its day`() {
        val pool = water.copy(
            repeat = Repeat.OnWeekdays(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)),
            time = null,
            reminder = Reminder.AtTime(LocalTime.of(9, 0)),
        )
        assertEquals(
            LocalDateTime.of(2026, 10, 20, 9, 0), // вторник
            Reminders.nextForRule(pool, emptyList(), monday.atTime(12, 0))?.second,
        )
    }

    @Test
    fun `paused days are skipped, no reminder means nothing`() {
        val paused = water.copy(pauses = listOf(Pause(monday, monday.plusDays(2))))
        assertEquals(monday.plusDays(3), Reminders.nextForRule(paused, emptyList(), monday.atTime(9, 0))?.first)
        assertNull(Reminders.nextForRule(water.copy(reminder = null), emptyList(), monday.atTime(9, 0)))
    }
    @Test
    fun `snoozed repeat day rings again until it is done`() {
        val fired = LocalDateTime.of(2026, 10, 19, 10, 0) // полив сегодня в 10:00
        val now = fired.plusMinutes(1)
        val snooze = Snooze(original = fired, until = fired.plusMinutes(10), date = monday)

        // Отложили — звоним через 10 минут о сегодняшнем поливе
        assertEquals(monday to snooze.until, Reminders.alarmForRule(water, emptyList(), now, snooze))
        // Тем временем отметили сегодняшний полив — отложенное ни к чему, следующее завтра
        val done = Task(title = "Полить цветы", date = monday, ruleId = 1, occurrenceDate = monday, status = TaskStatus.DONE)
        assertEquals(monday.plusDays(1), Reminders.alarmForRule(water, listOf(done), now, snooze)?.first)
        // Отложенное время прошло — тоже обычное следующее
        assertEquals(monday.plusDays(1), Reminders.alarmForRule(water, emptyList(), snooze.until, snooze)?.first)
    }
}
