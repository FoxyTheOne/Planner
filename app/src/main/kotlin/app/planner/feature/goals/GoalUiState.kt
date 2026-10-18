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

/**
 * Что можно сделать с целью целиком (а не с её шагами).
 *
 * Экран сообщает о таких нажатиях одной функцией `onGoalAction(GoalAction.Achieve)`, а не пятью
 * отдельными параметрами onAchieve, onReopen, onDelete… — у экрана цели их и так много.
 * `sealed interface` — снова «закрытый набор вариантов»: `when (action)` обязан разобрать их все,
 * и если добавить новое действие, компилятор покажет, где его ещё не обрабатывают.
 */
sealed interface GoalAction {
    /** Отметить достигнутой — цель уходит в архив. */
    data object Achieve : GoalAction

    /** Вернуть достигнутую цель в работу. */
    data object Reopen : GoalAction

    /** Срок прошёл — выбрать новый (откроется календарь). */
    data object NewDeadline : GoalAction

    /** Срок прошёл — убрать его совсем. */
    data object NoDeadline : GoalAction

    /** Удалить цель вместе с шагами (с «Отменить»). */
    data object Delete : GoalAction
}
