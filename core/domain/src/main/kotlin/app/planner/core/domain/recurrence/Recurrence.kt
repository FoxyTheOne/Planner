package app.planner.core.domain.recurrence

import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Repeat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Одно «выпадение» правила на конкретный день: например, «Полив, ср 7 октября: фикус, монстера».
 *
 * @param details что именно сделать в этот день — только у цикла (дела этой недели цикла).
 *   У простых повторений null: там каждый раз одно и то же, и хватает названия правила.
 */
data class Occurrence(
    val rule: RecurrenceRule,
    val date: LocalDate,
    val details: String? = null,
)

/**
 * Движок повторений: отвечает на вопросы «выпадает ли правило на этот день?»
 * и «в какие дни оно выпадает с … по …?».
 *
 * Ничего не хранит и ничего не сохраняет — только считает. Неделя спросит его
 * про свои 7 дней, «Сегодня» — про один день. Чистая логика, проверяется тестами
 * (RecurrenceTest, CycleTest, PauseTest).
 */
object Recurrence {

    /** Что выпадает на [date] по этому правилу; null — в этот день ничего. */
    fun occurrence(rule: RecurrenceRule, date: LocalDate): Occurrence? {
        // Сначала период действия и паузы: в это время ничего не бывает
        if (date < rule.startDate) return null
        if (rule.endDate != null && date > rule.endDate) return null
        if (rule.pauses.any { date in it }) return null

        // when по sealed interface: компилятор проверит, что разобраны все варианты Repeat.
        // Когда добавили Cycle, сборка не прошла, пока здесь не появилась ветка для него.
        return when (val repeat = rule.repeat) {
            Repeat.Daily -> Occurrence(rule, date)
            is Repeat.OnWeekdays ->
                if (date.dayOfWeek in repeat.days) Occurrence(rule, date) else null

            is Repeat.EveryNDays -> {
                // Сколько дней правило было активно (без пауз); выпадает каждый N-й: 0, N, 2N…
                val activeDays = ChronoUnit.DAYS.between(rule.startDate, date) -
                        pausedDays(rule.pauses, rule.startDate, date)
                if (activeDays % repeat.n == 0L) Occurrence(rule, date) else null
            }

            is Repeat.Cycle -> {
                val week = repeat.weeks[cycleWeekIndex(repeat, date, rule.pauses)]
                // Дела этой недели цикла на этот день недели; нет дел — нет и выпадения
                week[date.dayOfWeek]?.let { details -> Occurrence(rule, date, details) }
            }
        }
    }

    /** Что выпадает на [date] сразу по всем правилам — для экрана одного дня («Сегодня»). */
    fun occurrencesOn(rules: List<RecurrenceRule>, date: LocalDate): List<Occurrence> =
        rules.mapNotNull { occurrence(it, date) }

    /** Выпадает ли правило на [date]. */
    fun occursOn(rule: RecurrenceRule, date: LocalDate): Boolean = occurrence(rule, date) != null

    /** Все выпадения с [from] по [to] включительно. */
    fun occurrences(rule: RecurrenceRule, from: LocalDate, to: LocalDate): List<Occurrence> =
    // generateSequence — «ленивая» цепочка дней: from, from+1, from+2… пока не дойдём до to.
        // mapNotNull — превратить каждый день в Occurrence и выбросить null (дни, когда ничего нет)
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { it <= to }
            .mapNotNull { occurrence(rule, it) }
            .toList()

    /**
     * Какая неделя цикла идёт в [date]: 0 — первая, 1 — вторая…
     *
     * 1. Сколько недель прошло от опорной недели до недели [date].
     * 2. Вычитаем недели, целиком ушедшие в паузу: «как с музыкой», они в цикле не считаются.
     *    Неделя, в которой пауза только началась или закончилась, считается — она уже начата.
     * 3. Прибавляем к номеру опорной недели и берём остаток от деления на длину цикла.
     *
     * Math.floorMod вместо `%` — чтобы и для дат РАНЬШЕ опорной недели получалось правильно:
     * у `%` остаток бывает отрицательным (-1 % 4 = -1), а floorMod(-1, 4) = 3 — неделя 4, как по кругу.
     */
    fun cycleWeekIndex(
        cycle: Repeat.Cycle,
        date: LocalDate,
        pauses: List<Pause> = emptyList()
    ): Int {
        val monday = mondayOf(date)
        val weeksPassed = ChronoUnit.WEEKS.between(cycle.anchorMonday, monday)
        // Паузы между опорной неделей и датой: вперёд по времени — вычитаем, назад — прибавляем
        val activeWeeks = if (weeksPassed >= 0) {
            weeksPassed - fullyPausedWeeks(pauses, cycle.anchorMonday, monday)
        } else {
            weeksPassed + fullyPausedWeeks(pauses, monday, cycle.anchorMonday)
        }
        return Math.floorMod(cycle.anchorWeekNumber - 1 + activeWeeks, cycle.weeks.size.toLong())
            .toInt()
    }

    /**
     * «Сейчас идёт неделя N»: если цикл сбился, человек говорит, какая неделя на самом деле.
     * Просто записываем «эта неделя — N-я»; дела по неделям остаются как были.
     *
     * @param weekNumber номер недели, как его видит человек: 1, 2, 3…
     */
    fun setCurrentWeek(cycle: Repeat.Cycle, today: LocalDate, weekNumber: Int): Repeat.Cycle =
    // copy — новый объект с изменёнными полями (data class неизменяемый).
        // Проверку номера недели сделает init в Cycle
        cycle.copy(anchorMonday = mondayOf(today), anchorWeekNumber = weekNumber)

    /**
     * «Продолжить» — снять паузу, которая идёт сейчас (в том числе паузу без даты).
     * Сегодня правило снова работает. Паузы, запланированные на будущее, не трогаем.
     */
    fun resume(rule: RecurrenceRule, today: LocalDate): RecurrenceRule {
        val pauses = rule.pauses.mapNotNull { pause ->
            when {
                today !in pause -> pause // эта пауза сейчас не идёт — оставляем как есть
                pause.from == today -> null // началась только сегодня — убираем совсем
                else -> pause.copy(until = today.minusDays(1)) // заканчиваем вчерашним днём
            }
        }
        return rule.copy(pauses = pauses)
    }

    /** Понедельник той недели, в которую попадает [date] (сам [date], если это понедельник). */
    fun mondayOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    // ---------- Подсчёт пауз ----------

    /**
     * Склеить паузы, которые пересекаются или идут встык, в сплошные отрезки по порядку.
     * Иначе две пересекающиеся паузы («отпуск» и «цветы у соседки») посчитались бы дважды.
     */
    private fun merged(pauses: List<Pause>): List<Pause> {
        val result = mutableListOf<Pause>()
        for (pause in pauses.sortedBy { it.from }) {
            val last = result.lastOrNull()
            val joinsLast =
                last != null && (last.until == null || pause.from <= last.until.plusDays(1))
            if (last != null && joinsLast) {
                // Продлеваем предыдущий отрезок: конец — более поздний из двух (null — «без даты»)
                val until = if (last.until == null || pause.until == null) null else maxOf(
                    last.until,
                    pause.until
                )
                result[result.lastIndex] = Pause(last.from, until)
            } else {
                result += pause
            }
        }
        return result
    }

    /** Сколько дней из [from, toExclusive) пришлось на паузы. */
    private fun pausedDays(pauses: List<Pause>, from: LocalDate, toExclusive: LocalDate): Long =
        merged(pauses).sumOf { pause ->
            val start = maxOf(pause.from, from)
            // Конец паузы «не включая»: день после until; у паузы без даты — до самого toExclusive
            val end = minOf(pause.until?.plusDays(1) ?: toExclusive, toExclusive)
            if (end > start) ChronoUnit.DAYS.between(start, end) else 0L
        }

    /**
     * Сколько недель (пн–вс) между понедельниками [fromMonday] и [toMondayExclusive]
     * целиком пришлось на паузы.
     */
    private fun fullyPausedWeeks(
        pauses: List<Pause>,
        fromMonday: LocalDate,
        toMondayExclusive: LocalDate
    ): Long =
        merged(pauses).sumOf { pause ->
            // Первая целая неделя паузы: её понедельник — сам from или ближайший следующий
            val firstMonday =
                maxOf(pause.from.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)), fromMonday)
            // Понедельник ПОСЛЕ последней целой недели. Если пауза кончается в воскресенье —
            // это следующий понедельник; иначе её последняя неделя неполная и не считается
            val endMonday =
                minOf(pause.until?.let { mondayOf(it.plusDays(1)) } ?: toMondayExclusive,
                    toMondayExclusive)
            if (endMonday > firstMonday) ChronoUnit.WEEKS.between(firstMonday, endMonday) else 0L
        }
}
