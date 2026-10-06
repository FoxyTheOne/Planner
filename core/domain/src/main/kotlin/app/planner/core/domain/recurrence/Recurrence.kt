package app.planner.core.domain.recurrence

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Движок повторений: отвечает на вопросы «выпадает ли правило на этот день?»
 * и «в какие дни оно выпадает с … по …?».
 *
 * Ничего не хранит и ничего не сохраняет — только считает. Неделя спросит его
 * про свои 7 дней, «Сегодня» — про один день. Чистая логика, проверяется тестами (RecurrenceTest).
 */
object Recurrence {

    /** Выпадает ли правило на [date]. */
    fun occursOn(rule: RecurrenceRule, date: LocalDate): Boolean {
        // Сначала период действия: до начала и после конца правила ничего не бывает
        if (date < rule.startDate) return false
        if (rule.endDate != null && date > rule.endDate) return false

        // when по sealed interface: компилятор проверит, что разобраны все варианты Repeat
        return when (val repeat = rule.repeat) {
            Repeat.Daily -> true
            is Repeat.OnWeekdays -> date.dayOfWeek in repeat.days
            is Repeat.EveryNDays -> {
                // Сколько дней прошло от начала; выпадает каждый N-й: 0, N, 2N…
                val daysPassed = ChronoUnit.DAYS.between(rule.startDate, date)
                daysPassed % repeat.n == 0L
            }
        }
    }

    /** Все дни с [from] по [to] включительно, на которые выпадает правило. */
    fun occurrences(rule: RecurrenceRule, from: LocalDate, to: LocalDate): List<LocalDate> =
        // generateSequence — «ленивая» цепочка дней: from, from+1, from+2… пока не дойдём до to
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { it <= to }
            .filter { occursOn(rule, it) }
            .toList()
}
