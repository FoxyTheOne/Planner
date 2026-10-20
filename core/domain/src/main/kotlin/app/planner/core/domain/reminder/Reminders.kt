package app.planner.core.domain.reminder

import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Reminder
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import app.planner.core.domain.recurrence.Recurrence
import app.planner.core.domain.reminder.Reminders.LOOKAHEAD_DAYS
import app.planner.core.domain.reminder.Reminders.alarmAt
import app.planner.core.domain.reminder.Reminders.fireAt
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

    /** На сколько дней вперёд ищем следующий день повторения: год с небольшим — хватит и «раз в год». */
    const val LOOKAHEAD_DAYS = 400

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
     * [snoozes] — отложенные напоминания по id задачи (см. [alarmAt]).
     */
    fun upcoming(
        tasks: List<Task>,
        now: LocalDateTime,
        snoozes: Map<Long, Snooze> = emptyMap(),
    ): List<Pair<Task, LocalDateTime>> =
        tasks.mapNotNull { task -> alarmAt(task, now, snoozes[task.id])?.let { task to it } }
            .sortedBy { it.second }

    /**
     * Когда звенеть будильнику задачи [task], если считать от [now]; null — не нужно.
     *
     * Обычно это [fireAt], если он ещё впереди. Но если напоминание отложили ([snooze]) —
     * звоним в [Snooze.until]. Отложенное действует, пока задачу не меняли: если её перенесли
     * или поменяли напоминание, fireAt стал другим, и снова действует обычное правило.
     */
    fun alarmAt(task: Task, now: LocalDateTime, snooze: Snooze? = null): LocalDateTime? {
        val at = fireAt(task) ?: return null
        if (snooze != null && snooze.original == at && snooze.until > now) return snooze.until
        return at.takeIf { it > now }
    }

    /**
     * Следующее напоминание повторения [rule] после [now]: какой это день и когда звонить.
     * null — напоминать не о чем (напоминания нет или повторение закончилось).
     *
     * Идём по дням правила начиная с сегодня и берём первый, чьё напоминание ещё впереди.
     * Сегодняшний день тоже годится: «полив в 18:00, напомнить за 30 минут» в 17:00 — ещё не поздно.
     * Дни на паузе правило и так не выдаёт (см. Recurrence.occurrence).
     *
     * Пропускаем дни, которые уже стали задачами ([instances]: отмеченные, пропущенные, перенесённые):
     * - сделали или пропустили — напоминать не о чем;
     * - перенесли — у задачи своё напоминание на новый день (она взяла его у правила, см. Recurrence.toTask),
     *   и о ней напомнит обычный будильник задачи. Иначе пришло бы два напоминания.
     *
     * Смотрим не дальше [LOOKAHEAD_DAYS] дней вперёд — с запасом и для «раз в год».
     */
    fun nextForRule(
        rule: RecurrenceRule,
        instances: List<Task>,
        now: LocalDateTime
    ): Pair<LocalDate, LocalDateTime>? {
        if (rule.reminder == null) return null
        // Дни правила, которые уже стали задачами, — их пропускаем
        val handled =
            instances.filter { it.ruleId == rule.id }.mapNotNull { it.occurrenceDate }.toSet()
        val today = now.toLocalDate()
        return generateSequence(today) { it.plusDays(1) }
            .take(LOOKAHEAD_DAYS)
            .mapNotNull { Recurrence.occurrence(rule, it) }
            .filter { it.date !in handled }
            .mapNotNull { occurrence ->
                // Напоминание дня — по тем же правилам, что у задачи: день повторения как задача
                fireAt(
                    Recurrence.toTask(
                        occurrence,
                        TaskStatus.OPEN
                    )
                )?.let { occurrence.date to it }
            }
            .firstOrNull { (_, at) -> at > now }
    }
}
