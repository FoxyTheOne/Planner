package app.planner.core.domain.preview

import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import app.planner.core.domain.recurrence.Occurrence
import app.planner.core.domain.recurrence.Recurrence
import java.time.LocalDate

/**
 * Что ждёт в какой-то день — коротко: дела со временем по порядку ([timed]) и сколько всего дел ([total]).
 *
 * Нужен в двух местах, и считается одинаково:
 * - «→ Завтра: 5 задач» с делами со временем внизу «Сегодня»;
 * - вечерняя сводка: «Завтра: 10:30 врач, 18:00 бассейн и ещё 3 дела без времени».
 *
 * Дела — это задачи дня и дни повторений-дел. Привычки не считаются: это не дела, которые нужно
 * «успеть» (так же, как в счёте «2 из 5» на «Сегодня»). Лекарства — тоже: о приёмах свои напоминания.
 * Сделанное заранее не считается: ждать его уже не нужно.
 */
data class DayPreview(val timed: List<Task>, val total: Int) {

    /** Сколько дел без времени: «и ещё 4». */
    val untimedCount: Int get() = total - timed.size

    companion object {
        /**
         * Из того, что экран уже собрал сам.
         *
         * @param tasks задачи этого дня — уже без отметок привычек.
         * @param occurrences ещё не отмеченные дни повторений-дел в этот день.
         */
        fun of(tasks: List<Task>, occurrences: List<Occurrence>): DayPreview {
            val open = tasks.filter { it.status == TaskStatus.OPEN }
            // Дни повторений — как задачи (Recurrence.toTask): у них время правила, и сортируются они вместе
            val timed = (
                    open.filter { it.time != null } +
                            occurrences.filter { it.rule.time != null }.map { Recurrence.toTask(it, TaskStatus.OPEN) }
                    ).sortedBy { it.time }
            return DayPreview(timed, total = open.size + occurrences.size)
        }

        /**
         * Из данных «как в базе» — для вечерней сводки, у которой нет экрана, где всё уже собрано.
         *
         * @param tasksOfDay все задачи с датой [date], в том числе отметки привычек (их отбросим здесь).
         * @param rules все правила повторений.
         * @param instances задачи, в которые превратились дни повторений в [date] (по дню правила,
         *   а не по дате задачи): перенесённый на другой день полив не должен вернуться по правилу.
         */
        fun on(
            date: LocalDate,
            tasksOfDay: List<Task>,
            rules: List<RecurrenceRule>,
            instances: List<Task>,
        ): DayPreview {
            val (habits, plans) = rules.partition { it.isHabit }
            val habitIds = habits.map { it.id }.toSet()
            return of(
                tasks = tasksOfDay.filterNot { it.ruleId in habitIds },
                occurrences = Recurrence.occurrencesOn(plans, date, instances),
            )
        }
    }
}
