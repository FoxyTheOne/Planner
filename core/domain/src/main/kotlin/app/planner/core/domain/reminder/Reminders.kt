package app.planner.core.domain.reminder

import app.planner.core.domain.model.Reminder
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import app.planner.core.domain.reminder.Reminders.upcoming
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Правила напоминаний: когда именно должно сработать напоминание задачи.
 *
 * Чистая логика — без Android: сам «будильник» (AlarmManager) ставит модуль app,
 * а здесь только ответ на вопрос «в какой момент». Проверяется тестами (RemindersTest).
 */
object Reminders {

    /**
     * Когда сработает напоминание [task]; null — не сработает:
     * - напоминания нет;
     * - у задачи нет даты (она в списке «когда-нибудь» — напоминать не о чем и некогда);
     * - задача уже сделана или пропущена;
     * - «за N минут до времени», а времени у задачи нет (его убрали) — не от чего отсчитывать.
     *
     * Прошло ли это время, здесь не проверяем: это решает тот, кто ставит будильник ([upcoming]).
     */
    fun fireAt(task: Task): LocalDateTime? {
        val reminder = task.reminder ?: return null
        val date = task.date ?: return null
        if (task.status != TaskStatus.OPEN) return null
        // when по sealed interface: компилятор проверит, что разобраны оба вида напоминаний
        return when (reminder) {
            is Reminder.BeforeTime -> task.time?.let {
                date.atTime(it).minusMinutes(reminder.minutes.toLong())
            }

            // «В 9:00» — но не позже начала самой задачи. Добавили задаче время 8:00 — напоминание
            // в 9:00 пришло бы через час после начала, это бессмысленно: тогда напомним в 8:00.
            // Если же 9:00 раньше времени задачи (дело в 15:00) — напоминаем утром, как и просили
            is Reminder.AtTime -> {
                val at = date.atTime(reminder.time)
                val start = task.time?.let { date.atTime(it) }
                if (start != null && at > start) start else at
            }
        }
    }

    /**
     * Напоминание, которое подходит задаче с такими датой и временем, — для редактора:
     * дату или время могли поменять уже после того, как выбрали напоминание.
     * - даты нет — напоминание не нужно (null): напоминать не о чем и некогда;
     * - «за N минут до времени», а времени нет — тоже null: не от чего отсчитывать.
     *   В редакторе строка снова станет «Напомнить» — человек увидит и выберет «в 9:00»;
     * - «в 9:00» подходит и задаче со временем — остаётся как есть.
     */
    fun fitting(reminder: Reminder?, date: LocalDate?, time: LocalTime?): Reminder? = when {
        reminder == null || date == null -> null
        reminder is Reminder.BeforeTime && time == null -> null
        else -> reminder
    }

    /**
     * Нужно ли после переноса задачи спросить, что делать с её напоминанием.
     *
     * Спрашиваем, только когда напоминание «в 9:00» (час, а не «за N минут»), а у задачи поменялось
     * время дня: часть дня (утро → вечер) или точное время. Тогда «в 9:00» может больше не подходить.
     * Не спрашиваем:
     * - «за 30 минут» — оно и так переезжает вместе с задачей;
     * - поменялся только день (утро → утро другого дня) — «в 9:00» в новый день подходит, как было;
     * - напоминание при переносе поменяли сами (в редакторе) — человек уже решил;
     * - у задачи больше нет даты (её вернули в список) — напоминать некогда, вопрос не к месту.
     */
    fun askAfterMove(before: Task, after: Task): Boolean =
        after.reminder is Reminder.AtTime &&
                after.reminder == before.reminder &&
                after.date != null &&
                after.status == TaskStatus.OPEN &&
                (after.time != before.time || after.partOfDay != before.partOfDay)

    /**
     * Напоминания, которые ещё впереди: задача и момент, по порядку времени.
     * Их и нужно поставить будильниками — прошедшие уже не нужны.
     */
    fun upcoming(tasks: List<Task>, now: LocalDateTime): List<Pair<Task, LocalDateTime>> =
        tasks.mapNotNull { task -> fireAt(task)?.takeIf { it > now }?.let { task to it } }
            .sortedBy { it.second }
}
