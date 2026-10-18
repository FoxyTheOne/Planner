package app.planner.core.domain.reminder

import app.planner.core.domain.model.Reminder
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import app.planner.core.domain.reminder.Reminders.upcoming
import java.time.LocalDateTime

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

            is Reminder.AtTime -> date.atTime(reminder.time)
        }
    }

    /**
     * Напоминания, которые ещё впереди: задача и момент, по порядку времени.
     * Их и нужно поставить будильниками — прошедшие уже не нужны.
     */
    fun upcoming(tasks: List<Task>, now: LocalDateTime): List<Pair<Task, LocalDateTime>> =
        tasks.mapNotNull { task -> fireAt(task)?.takeIf { it > now }?.let { task to it } }
            .sortedBy { it.second }
}
