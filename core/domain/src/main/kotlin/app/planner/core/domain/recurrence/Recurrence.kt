package app.planner.core.domain.recurrence

import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import app.planner.core.domain.recurrence.Recurrence.occurrencesOn
import app.planner.core.domain.recurrence.Recurrence.toTask
import java.time.DayOfWeek
import java.time.Instant
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

    /**
     * Что выпадает на [date] сразу по всем правилам — для экрана одного дня («Сегодня»).
     *
     * @param instances задачи, в которые уже превратились дни повторений (см. [toTask]).
     *   Если для правила на этот день такая задача есть, «виртуальную» строку не показываем —
     *   вместо неё на экране будет сама задача (отмеченная, а позже — и перенесённая).
     */
    fun occurrencesOn(
        rules: List<RecurrenceRule>,
        date: LocalDate,
        instances: List<Task> = emptyList(),
    ): List<Occurrence> =
        rules.mapNotNull { occurrence(it, date) }
            .filterNot { occ -> instances.any { it.ruleId == occ.rule.id && it.occurrenceDate == date } }

    /**
     * Всё, что выпадает с [from] по [to] по всем правилам, — для «Недели».
     * [instances] — как в [occurrencesOn]: уже отмеченные дни здесь не повторяются.
     */
    fun occurrencesBetween(
        rules: List<RecurrenceRule>,
        from: LocalDate,
        to: LocalDate,
        instances: List<Task> = emptyList(),
    ): List<Occurrence> =
        rules.flatMap { occurrences(it, from, to) }
            .filterNot { occ -> instances.any { it.ruleId == occ.rule.id && it.occurrenceDate == occ.date } }

    /**
     * Собрать правило из того, что человек ввёл в редакторе задачи, когда выбрал «Повторять».
     *
     * Редактор один и тот же для задач и повторений, поэтому он отдаёт задачу-«черновик»:
     * название, список, дату и время. Здесь из него получается правило:
     * - дата черновика — с какого дня правило начинается (не выбрали — с [today]);
     * - время или часть дня — каждого повторения;
     * - [existing] — если правило уже было (его редактируют): сохраняем его id и паузы;
     * - [isHabit] — привычка или дело. У задачи-черновика такого поля нет, поэтому оно передаётся
     *   отдельно. Не передали — остаётся как было у правила (новое правило — дело).
     */
    fun ruleFromDraft(
        draft: Task,
        repeat: Repeat,
        today: LocalDate,
        existing: RecurrenceRule? = null,
        // Значение по умолчанию может опираться на параметры левее: existing уже известен
        isHabit: Boolean = existing?.isHabit ?: false,
    ): RecurrenceRule = RecurrenceRule(
        id = existing?.id ?: 0,
        title = draft.title,
        repeat = repeat,
        startDate = draft.date ?: today,
        endDate = existing?.endDate,
        pauses = existing?.pauses.orEmpty(),
        time = draft.time,
        partOfDay = draft.partOfDay,
        listId = draft.listId,
        isHabit = isHabit,
    )

    /**
     * Правило поменяли — обновить его открытый перенесённый день, чтобы он не «застыл» со старым названием.
     *
     * Берём из правила то, что общее для всех его дней: название, список и, у цикла, дела этого дня
     * (по дню правила [Task.occurrenceDate]). Дату и время НЕ трогаем — это и есть перенос,
     * который человек сделал сам. Сделанные дни сюда не передают: это история, она остаётся как была.
     */
    fun syncInstance(instance: Task, rule: RecurrenceRule): Task {
        val details = instance.occurrenceDate?.let { occurrence(rule, it)?.details }
        return instance.copy(
            title = rule.title,
            listId = rule.listId,
            // Если день правила больше не выпадает (правило поменяли), заметку оставляем прежней
            note = details ?: instance.note,
        )
    }

    /**
     * Обратное: правило → черновик для редактора задачи, чтобы его можно было поправить.
     * В черновике id = 0 (это не задача из базы), а ruleId — какое правило редактируем.
     */
    fun draftFromRule(rule: RecurrenceRule): Task = Task(
        title = rule.title,
        date = rule.startDate,
        time = rule.time,
        partOfDay = rule.partOfDay,
        listId = rule.listId,
        ruleId = rule.id,
    )

    /**
     * Превратить день повторения в обычную задачу — когда с ним что-то сделали (например, отметили).
     *
     * Задача помнит правило ([Task.ruleId]) и день, к которому относится ([Task.occurrenceDate]).
     * Название — как у правила; дела этого дня у цикла («фикус, монстера») идут в заметку.
     */
    fun toTask(occurrence: Occurrence, status: TaskStatus): Task {
        val rule = occurrence.rule
        return Task(
            title = rule.title,
            note = occurrence.details.orEmpty(),
            date = occurrence.date,
            time = rule.time,
            partOfDay = rule.partOfDay,
            listId = rule.listId,
            status = status,
            doneAt = if (status == TaskStatus.DONE) Instant.now() else null,
            ruleId = rule.id,
            occurrenceDate = occurrence.date,
        )
    }

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
     * 1. Сколько недель прошло от опорной недели до недели [date]. Недели считаются так,
     *    как их считает сам цикл: с [Repeat.Cycle.weekStart] (понедельника или воскресенья).
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
        val weekStart = startOfWeek(date, cycle.weekStart)
        val anchor = cycle.anchorWeekStart
        val weeksPassed = ChronoUnit.WEEKS.between(anchor, weekStart)
        // Паузы между опорной неделей и датой: вперёд по времени — вычитаем, назад — прибавляем
        val activeWeeks = if (weeksPassed >= 0) {
            weeksPassed - fullyPausedWeeks(pauses, anchor, weekStart, cycle.weekStart)
        } else {
            weeksPassed + fullyPausedWeeks(pauses, weekStart, anchor, cycle.weekStart)
        }
        return Math.floorMod(cycle.anchorWeekNumber - 1 + activeWeeks, cycle.weeks.size.toLong())
            .toInt()
    }

    /**
     * Новый цикл из редактора: таблица дел, какая неделя цикла идёт сейчас и с какого дня
     * начинается неделя (как в «Неделе» по языку телефона).
     *
     * Пустые и состоящие из пробелов дела выбрасываем: пустое поле в редакторе значит
     * «в этот день ничего». Опорная неделя — текущая: «эта неделя — [currentWeekNumber]-я».
     */
    fun newCycle(
        weeks: List<Map<DayOfWeek, String>>,
        today: LocalDate,
        weekStart: DayOfWeek,
        currentWeekNumber: Int = 1,
    ): Repeat.Cycle = Repeat.Cycle(
        weeks = cleanWeeks(weeks),
        anchorWeekStart = startOfWeek(today, weekStart),
        anchorWeekNumber = currentWeekNumber,
        weekStart = weekStart,
    )

    /** Таблица дел без пустых полей и лишних пробелов по краям. */
    fun cleanWeeks(weeks: List<Map<DayOfWeek, String>>): List<Map<DayOfWeek, String>> =
        weeks.map { week -> week.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() } }

    /** Есть ли в таблице хоть одно дело — иначе цикл ничего бы не показывал. */
    fun hasAnyTask(weeks: List<Map<DayOfWeek, String>>): Boolean =
        cleanWeeks(weeks).any { it.isNotEmpty() }

    /** Какая неделя цикла идёт сейчас — номер, как видит человек: 1, 2, 3… */
    fun currentWeekNumber(
        cycle: Repeat.Cycle,
        today: LocalDate,
        pauses: List<Pause> = emptyList()
    ): Int =
        cycleWeekIndex(cycle, today, pauses) + 1

    /**
     * «Сейчас идёт неделя N»: если цикл сбился, человек говорит, какая неделя на самом деле.
     * Просто записываем «эта неделя — N-я»; дела по неделям и начало недели цикла остаются как были.
     *
     * @param weekNumber номер недели, как его видит человек: 1, 2, 3…
     */
    fun setCurrentWeek(cycle: Repeat.Cycle, today: LocalDate, weekNumber: Int): Repeat.Cycle =
    // copy — новый объект с изменёнными полями (data class неизменяемый).
        // Проверку номера недели сделает init в Cycle
        cycle.copy(
            anchorWeekStart = startOfWeek(today, cycle.weekStart),
            anchorWeekNumber = weekNumber
        )

    /** Пауза, которая идёт в [date], или null, если правило в этот день работает. */
    fun activePause(rule: RecurrenceRule, date: LocalDate): Pause? =
        rule.pauses.firstOrNull { date in it }

    /**
     * «Поставить на паузу» с [today] по [until] включительно; until = null — пока не нажмут «Продолжить».
     * Пауза просто добавляется к остальным: если они пересекаются, движок сам их склеит.
     */
    fun pause(rule: RecurrenceRule, today: LocalDate, until: LocalDate?): RecurrenceRule =
        rule.copy(pauses = rule.pauses + Pause(today, until))

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

    /**
     * Первый день недели, в которую попадает [date], если неделя начинается с [weekStart].
     * Например, для субботы 10 октября 2026: с понедельника → пн 5 октября, с воскресенья → вс 4 октября.
     */
    fun startOfWeek(date: LocalDate, weekStart: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(weekStart))

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
     * Сколько целых недель между [fromWeekStart] и [toWeekStartExclusive] пришлось на паузы.
     * Неделя — 7 дней начиная с [weekStart] (понедельника или воскресенья — как у цикла).
     */
    private fun fullyPausedWeeks(
        pauses: List<Pause>,
        fromWeekStart: LocalDate,
        toWeekStartExclusive: LocalDate,
        weekStart: DayOfWeek,
    ): Long =
        merged(pauses).sumOf { pause ->
            // Первая целая неделя паузы: её первый день — сам from или ближайшее следующее начало недели
            val firstWeek =
                maxOf(pause.from.with(TemporalAdjusters.nextOrSame(weekStart)), fromWeekStart)
            // Начало недели ПОСЛЕ последней целой недели. Если пауза кончается в последний день
            // недели — это начало следующей; иначе её последняя неделя неполная и не считается
            val endWeek =
                minOf(pause.until?.let { startOfWeek(it.plusDays(1), weekStart) }
                    ?: toWeekStartExclusive,
                    toWeekStartExclusive)
            if (endWeek > firstWeek) ChronoUnit.WEEKS.between(firstWeek, endWeek) else 0L
        }
}
