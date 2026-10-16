package app.planner.core.domain.recurrence

import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Task
import app.planner.core.domain.recurrence.HabitHistory.Companion.DAYS
import java.time.LocalDate

/*
 * Привычки: что показать «таблеткой» сегодня и мягкая статистика за последние дни.
 * Сами правила привычек — обычные RecurrenceRule с isHabit = true, движок повторений общий.
 */

/**
 * Привычка в конкретный день — «таблетка» над планом дня.
 *
 * Чем отличается от обычного дня повторения: «таблетка» видна весь день, и отмеченная тоже.
 * Её отмечают и снимают отметку одним нажатием, поэтому нужно знать и то, что выпадает
 * по правилу ([occurrence]), и задачу-отметку, если она уже есть ([task]).
 *
 * @param task задача, в которую превратился этот день, когда его отметили (см. [Recurrence.toTask]);
 *   null — сегодня ещё не отмечена.
 */
data class HabitDay(val occurrence: Occurrence, val task: Task?) {
    val rule: RecurrenceRule get() = occurrence.rule
    val isDone: Boolean get() = task?.isDone == true

    companion object {
        /**
         * Привычки на [date] — и ещё не отмеченные, и уже отмеченные (в отличие от
         * [Recurrence.occurrencesOn], где отмеченный день исчезает: там вместо него на экране стоит
         * сама задача). Правила, которые не привычки, пропускаются.
         *
         * @param instances задачи дней повторений; из них берётся отметка привычки на этот день.
         */
        fun on(
            rules: List<RecurrenceRule>,
            date: LocalDate,
            instances: List<Task> = emptyList()
        ): List<HabitDay> =
            rules.filter { it.isHabit }.mapNotNull { rule ->
                // ?.let — «если не null, сделай с ним»: в этот день по правилу ничего нет → null → пропуск
                Recurrence.occurrence(rule, date)?.let { occ ->
                    HabitDay(
                        occ,
                        instances.firstOrNull { it.ruleId == rule.id && it.occurrenceDate == date })
                }
            }
    }
}

/**
 * Как привычка шла последние дни — для мягкой статистики «За 30 дней — 18 раз».
 *
 * МЯГКАЯ — значит, считаем только то, что СДЕЛАНО. Нет «из 26», процентов и серий
 * («7 дней подряд!»): серия обнуляется от одного пропуска, и человек чувствует, что «всё сломал».
 * А «18 раз за месяц» после одного пропуска так и остаётся 18-ю разами.
 *
 * @param from первый день периода. Обычно 30 дней назад, но не раньше начала привычки:
 *   привычке три дня — и период три дня («С 10 октября — 2 раза»).
 * @param to последний день — сегодня.
 * @param planned дни, когда привычка выпадала по правилу (дни паузы сюда не входят).
 * @param done дни, когда её отметили.
 */
data class HabitHistory(
    val from: LocalDate,
    val to: LocalDate,
    val planned: Set<LocalDate>,
    val done: Set<LocalDate>,
) {
    val doneCount: Int get() = done.size

    /** Период короче полного: привычка появилась внутри него («С 10 октября», а не «За 30 дней»). */
    val startedRecently: Boolean get() = from > to.minusDays(DAYS - 1L)

    /** Все дни периода по порядку — для полоски квадратиков. */
    val days: List<LocalDate>
        get() = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toList()

    companion object {
        /** За сколько дней показываем статистику. */
        const val DAYS = 30

        /**
         * Посчитать историю привычки [rule] за последние [DAYS] дней по [today] включительно.
         *
         * @param instances задачи дней повторений (сделанные, перенесённые, пропущенные) —
         *   хотя бы за этот период; чужие правила и дни вне периода отбрасываются здесь.
         */
        fun of(rule: RecurrenceRule, instances: List<Task>, today: LocalDate): HabitHistory {
            // maxOf — позднее из двух: 30 дней назад или день, когда привычку завели
            val from = maxOf(today.minusDays(DAYS - 1L), rule.startDate)
            return HabitHistory(
                from = from,
                to = today,
                planned = Recurrence.occurrences(rule, from, today).map { it.date }.toSet(),
                done = instances
                    .filter { it.ruleId == rule.id && it.isDone }
                    .mapNotNull { it.occurrenceDate }
                    .filter { it in from..today } // `..` — диапазон дат, `in` — попадает ли в него
                    .toSet(),
            )
        }
    }
}
