package app.planner.feature.goals

import app.planner.core.domain.goal.GoalOverview
import app.planner.core.domain.goal.StepGroup
import app.planner.core.domain.model.Goal
import app.planner.core.domain.model.GoalStage
import app.planner.core.domain.model.Task
import java.time.LocalDate

/**
 * Состояние экрана одной цели.
 *
 * @param goal сама цель; null — её нет (ещё не загрузилась или её удалили).
 * @param overview прогресс и срок — то же, что на карточке.
 * @param groups шаги, разложенные по этапам, — в том порядке, в каком их рисовать.
 * @param stages и [steps] — «сырые» этапы и шаги: по ним ViewModel считает место для нового.
 */
data class GoalUiState(
    val today: LocalDate,
    val goal: Goal? = null,
    val overview: GoalOverview? = null,
    val groups: List<StepGroup> = emptyList(),
    val stages: List<GoalStage> = emptyList(),
    val steps: List<Task> = emptyList(),
    val loaded: Boolean = false,
)
