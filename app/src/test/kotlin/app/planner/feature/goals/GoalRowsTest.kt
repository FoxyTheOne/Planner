package app.planner.feature.goals

import app.planner.core.domain.goal.StepGroup
import app.planner.core.domain.model.GoalStage
import app.planner.core.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test

/** Строки экрана цели: что получается, когда шаг тащат или двигают «Выше» / «Ниже». */
class GoalRowsTest {

    private val demolition = GoalStage(id = 10, goalId = 1, title = "Демонтаж")
    private val tiles = GoalStage(id = 20, goalId = 1, title = "Плитка", position = 1)
    private val budget = Task(id = 1, title = "Бюджет", goalId = 1)
    private val remove = Task(id = 2, title = "Снять плитку", goalId = 1, stageId = 10)
    private val buy = Task(id = 3, title = "Купить плитку", goalId = 1, stageId = 20)

    private val groups = listOf(
        StepGroup(null, listOf(budget)),
        StepGroup(demolition, listOf(remove)),
        StepGroup(tiles, listOf(buy)),
    )
    private val rows = goalRows(groups)

    /** Коротко, где какой шаг: «этап: шаги». */
    private fun List<StepGroup>.describe() = map { g -> "${g.stage?.title}: ${g.steps.joinToString { it.title }}" }

    @Test
    fun `rows and groups are the same thing written differently`() {
        assertEquals(
            listOf("step_1", "add_step_null", "stage_10", "step_2", "add_step_10", "stage_20", "step_3", "add_step_20"),
            rows.map { it.key },
        )
        assertEquals(groups, stepGroupsOf(rows))
    }

    @Test
    fun `dragging a step under another header moves it to that stage`() {
        // «Купить плитку» перетащили на место «Снять плитку» — теперь она первая в «Демонтаже»
        val after = stepGroupsOf(rows.moved(fromKey = "step_3", toKey = "step_2"))
        assertEquals(listOf("null: Бюджет", "Демонтаж: Купить плитку, Снять плитку", "Плитка: "), after.describe())
    }

    @Test
    fun `up across a header goes to the end of the previous stage`() {
        val after = stepGroupsOf(rows.movedUp("step_3"))
        assertEquals(listOf("null: Бюджет", "Демонтаж: Снять плитку, Купить плитку", "Плитка: "), after.describe())
    }

    @Test
    fun `up from the first stage takes the step out of stages`() {
        val after = stepGroupsOf(rows.movedUp("step_2"))
        assertEquals(listOf("null: Бюджет, Снять плитку", "Демонтаж: ", "Плитка: Купить плитку"), after.describe())
    }

    @Test
    fun `down across a header goes first into the next stage`() {
        val after = stepGroupsOf(rows.movedDown("step_2"))
        assertEquals(listOf("null: Бюджет", "Демонтаж: ", "Плитка: Снять плитку, Купить плитку"), after.describe())
    }

    @Test
    fun `nowhere to go — nothing changes`() {
        assertEquals(rows, rows.movedUp("step_1")) // самый первый шаг
        assertEquals(rows, rows.movedDown("step_3")) // самый последний шаг
    }

    @Test
    fun `step dragged above the first header of a goal without loose steps becomes loose`() {
        val noLoose = goalRows(listOf(StepGroup(demolition, listOf(remove))))
        val after = stepGroupsOf(noLoose.moved(fromKey = "step_2", toKey = "stage_10"))
        assertEquals(listOf("null: Снять плитку", "Демонтаж: "), after.describe())
    }
}
