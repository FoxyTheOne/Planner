package app.planner.core.domain.goal

import app.planner.core.domain.model.Goal
import app.planner.core.domain.model.GoalStage
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Правила целей: порядок шагов, следующий шаг, прогресс и срок. */
class GoalsTest {

    private val today = LocalDate.of(2026, 10, 16)
    private val goal = Goal(id = 1, title = "Ремонт в ванной")

    // Этапы нарочно в «чужом» порядке: важен position, а не порядок в списке
    private val tiles = GoalStage(id = 20, goalId = 1, title = "Плитка", position = 1)
    private val demolition = GoalStage(id = 10, goalId = 1, title = "Демонтаж", position = 0)

    /** Шаг; [order] — когда добавлен (чем больше, тем позже). */
    private fun step(title: String, stage: GoalStage? = null, order: Long, status: TaskStatus = TaskStatus.OPEN) =
        Task(
            id = order, title = title, goalId = 1, stageId = stage?.id, status = status,
            createdAt = Instant.ofEpochSecond(order),
        )

    @Test
    fun `steps without stage go first, then stages by position`() {
        val steps = listOf(
            step("Положить плитку", tiles, order = 1),
            step("Снять старую плитку", demolition, order = 2),
            step("Посчитать бюджет", order = 3),
            step("Вынести мусор", demolition, order = 4),
        )

        val ordered = orderedSteps(listOf(tiles, demolition), steps)

        assertEquals(
            listOf("Посчитать бюджет", "Снять старую плитку", "Вынести мусор", "Положить плитку"),
            ordered.map { it.title },
        )
    }

    @Test
    fun `next step is the first open one, progress counts done steps`() {
        val steps = listOf(
            step("Посчитать бюджет", order = 1, status = TaskStatus.DONE),
            step("Снять старую плитку", demolition, order = 2, status = TaskStatus.DONE),
            step("Вынести мусор", demolition, order = 3),
            step("Положить плитку", tiles, order = 4),
        )

        val overview = GoalOverview.of(goal, listOf(tiles, demolition), steps, today)

        assertEquals("Вынести мусор", overview.nextStep?.title)
        assertEquals(GoalProgress(done = 2, total = 4), overview.progress)
        assertEquals(0.5f, overview.progress.fraction)
    }

    @Test
    fun `goal without steps has no next step and zero progress`() {
        val overview = GoalOverview.of(goal, emptyList(), emptyList(), today)

        assertNull(overview.nextStep)
        assertEquals(0f, overview.progress.fraction) // не NaN от деления на ноль
    }

    @Test
    fun `deadline states`() {
        fun state(deadline: LocalDate?, achieved: LocalDate? = null) =
            DeadlineState.of(goal.copy(deadline = deadline, achievedOn = achieved), today)

        assertEquals(DeadlineState.None, state(null))
        assertEquals(DeadlineState.Ahead(daysLeft = 60, isSoon = false), state(today.plusDays(60)))
        assertEquals(DeadlineState.Ahead(daysLeft = 15, isSoon = false), state(today.plusDays(15)))
        assertEquals(DeadlineState.Ahead(daysLeft = 14, isSoon = true), state(today.plusDays(14)))
        assertEquals(DeadlineState.Ahead(daysLeft = 0, isSoon = true), state(today)) // срок сегодня — ещё не прошёл
        assertEquals(DeadlineState.Passed, state(today.minusDays(1)))
        // Достигнутой цели срок уже не важен — даже прошедший
        assertEquals(DeadlineState.None, state(today.minusDays(5), achieved = today.minusDays(10)))
    }
}
