package app.planner.feature.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.planner.common.TodayClock
import app.planner.core.domain.goal.GoalOverview
import app.planner.core.domain.goal.positionForNewGoal
import app.planner.core.domain.model.Goal
import app.planner.core.domain.repository.IGoalRepository
import app.planner.core.domain.repository.ITaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel экрана «Цели»: собирает карточки целей и сохраняет новые цели.
 *
 * Карточке нужно знать прогресс и следующий шаг, а для этого — шаги и этапы.
 * Поэтому здесь три потока из базы (цели, все этапы, все шаги) плюс сегодняшняя дата
 * (от неё зависит, «скоро» ли срок). combine склеивает их: изменилось что-то одно —
 * карточки пересчитываются. Сам пересчёт — в домене (GoalOverview.of), здесь только раскладка.
 */
@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goals: IGoalRepository,
    tasks: ITaskRepository,
    todayClock: TodayClock,
) : ViewModel() {

    val state: StateFlow<GoalsUiState> = combine(
        goals.observeGoals(),
        goals.observeAllStages(),
        tasks.observeAllGoalSteps(),
        todayClock.todayFlow,
    ) { allGoals, stages, steps, today ->
        // groupBy — список → словарь «id цели → её этапы / её шаги», чтобы не искать заново для каждой карточки
        val stagesByGoal = stages.groupBy { it.goalId }
        val stepsByGoal = steps.groupBy { it.goalId }
        val overviews = allGoals.map { goal ->
            GoalOverview.of(goal, stagesByGoal[goal.id].orEmpty(), stepsByGoal[goal.id].orEmpty(), today)
        }
        val (achieved, active) = overviews.partition { it.goal.isAchieved }
        GoalsUiState(
            today = today,
            active = active,
            achieved = achieved.sortedByDescending { it.goal.achievedOn },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState(todayClock.today()))

    /** Сохранить из шторки. Новая цель встаёт в конец списка (см. positionForNewGoal). */
    fun save(goal: Goal) = viewModelScope.launch {
        val all = state.value.let { it.active + it.achieved }.map { it.goal }
        goals.save(if (goal.id == 0L) goal.copy(position = positionForNewGoal(all)) else goal)
    }
}
