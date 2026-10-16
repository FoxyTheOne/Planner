package app.planner.core.domain.goal

import app.planner.core.domain.model.Goal
import app.planner.core.domain.model.GoalStage
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Правила целей: в каком порядке идут шаги, какой из них «следующий», сколько сделано
 * и как обстоит дело со сроком. Чистые функции — ничего не читают из базы, проверяются тестами (GoalsTest).
 */

/** Сколько шагов цели сделано: «3 из 8». Привязанные к цели привычки сюда не входят. */
data class GoalProgress(val done: Int, val total: Int) {
    /** Доля от 0 до 1 — для полоски прогресса. У цели без шагов — 0, а не деление на ноль. */
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/**
 * Как обстоит дело со сроком цели. Мягко: срок помогает, а не стыдит.
 *
 * `sealed interface`, как и Repeat: у вариантов разные данные — у «впереди» число дней,
 * у остальных ничего.
 */
sealed interface DeadlineState {
    /** Срока нет, или цель уже достигнута — тогда срок больше не важен. */
    data object None : DeadlineState

    /**
     * Срок впереди (или сегодня).
     * @param daysLeft сколько дней осталось: 0 — срок сегодня.
     * @param isSoon осталось не больше [SOON_DAYS] дней — подпись становится заметнее, но не тревожной.
     */
    data class Ahead(val daysLeft: Int, val isSoon: Boolean) : DeadlineState

    /**
     * Срок прошёл. Без «просрочено на 23 дня»: на экране — спокойное «срок был …»
     * и выбор «Новый срок / Без срока / Достигнута». Сам срок никогда не меняется без человека.
     */
    data object Passed : DeadlineState

    companion object {
        /** За сколько дней до срока он считается «скоро». */
        const val SOON_DAYS = 14

        fun of(goal: Goal, today: LocalDate): DeadlineState {
            val deadline = goal.deadline
            if (deadline == null || goal.isAchieved) return None
            // ChronoUnit.DAYS.between — сколько полных дней от первой даты до второй
            val daysLeft = ChronoUnit.DAYS.between(today, deadline).toInt()
            return if (daysLeft < 0) Passed else Ahead(daysLeft, isSoon = daysLeft <= SOON_DAYS)
        }
    }
}

/**
 * Шаги цели в том порядке, в каком их показывать и делать:
 * 1. сначала шаги без этапа — обычно это общее, с чего всё начинается («Посчитать бюджет»);
 * 2. потом этапы по порядку ([GoalStage.position]), внутри этапа — в порядке добавления.
 *
 * Пропущенные (SKIPPED) шаги отбрасываются: у шагов такого статуса пока не бывает,
 * но если появится — «пропущено» не должно считаться ни сделанным, ни следующим.
 */
fun orderedSteps(stages: List<GoalStage>, steps: List<Task>): List<Task> {
    // associate — словарь «id этапа → его место»: искать место по id быстро и просто
    val stageOrder = stages.associate { it.id to it.position }
    return steps
        .filter { it.status != TaskStatus.SKIPPED }
        // compareBy с несколькими условиями: сначала по первому, при равенстве — по следующему.
        // Шаг без этапа получает место -1 — меньше любого этапа, поэтому встаёт первым
        .sortedWith(
            compareBy<Task>(
                { step -> step.stageId?.let { stageOrder[it] } ?: -1 },
                { it.stageId ?: 0L }, // этапы с одинаковым position — не перемешивать их шаги
                { it.createdAt },
            ),
        )
}

/**
 * Всё, что нужно карточке цели: сколько сделано, следующий шаг и срок.
 *
 * @param nextStep первый ещё не сделанный шаг (в порядке [orderedSteps]); null — шагов нет
 *   или все сделаны. Карточка тогда подскажет «Добавьте первый шаг» или «Всё сделано — достигнута?».
 */
data class GoalOverview(
    val goal: Goal,
    val progress: GoalProgress,
    val nextStep: Task?,
    val deadline: DeadlineState,
) {
    companion object {
        /**
         * @param stages этапы этой цели; [steps] — её шаги (задачи с goalId этой цели).
         */
        fun of(goal: Goal, stages: List<GoalStage>, steps: List<Task>, today: LocalDate): GoalOverview {
            val ordered = orderedSteps(stages, steps)
            return GoalOverview(
                goal = goal,
                progress = GoalProgress(done = ordered.count { it.isDone }, total = ordered.size),
                nextStep = ordered.firstOrNull { !it.isDone },
                deadline = DeadlineState.of(goal, today),
            )
        }
    }
}
