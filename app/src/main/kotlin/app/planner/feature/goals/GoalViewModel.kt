package app.planner.feature.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.planner.R
import app.planner.common.EditorResult
import app.planner.common.MessageBus
import app.planner.common.TaskCommands
import app.planner.common.TodayClock
import app.planner.common.UiMessage
import app.planner.common.UiText
import app.planner.core.domain.goal.GoalOverview
import app.planner.core.domain.goal.StepGroup
import app.planner.core.domain.goal.nextPosition
import app.planner.core.domain.goal.positionForNewStep
import app.planner.core.domain.goal.rearrangedSteps
import app.planner.core.domain.goal.stepGroups
import app.planner.core.domain.model.Goal
import app.planner.core.domain.model.GoalStage
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.repository.IGoalRepository
import app.planner.core.domain.repository.ITaskRepository
import app.planner.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel экрана одной цели: её шаги по этапам и всё, что с ними делают.
 *
 * Какая цель открыта, берётся из адреса экрана (`goal/{goalId}`) через [SavedStateHandle] —
 * как у экрана списка.
 *
 * Шаги — обычные задачи, поэтому отметить, запланировать, изменить и удалить шаг —
 * это те же [TaskCommands], что и для задач в «Сегодня» и в списках (с тем же «Отменить»).
 */
@HiltViewModel
class GoalViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val goals: IGoalRepository,
    private val tasks: ITaskRepository,
    private val commands: TaskCommands,
    private val messages: MessageBus,
    todayClock: TodayClock,
) : ViewModel() {

    private val goalId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_GOAL_ID))

    val state: StateFlow<GoalUiState> = combine(
        goals.observeGoal(goalId),
        goals.observeStages(goalId),
        tasks.observeGoalSteps(goalId),
        todayClock.todayFlow,
    ) { goal, stages, steps, today ->
        GoalUiState(
            today = today,
            goal = goal,
            overview = goal?.let { GoalOverview.of(it, stages, steps, today) },
            groups = stepGroups(stages, steps),
            stages = stages,
            steps = steps,
            loaded = true,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        GoalUiState(todayClock.today())
    )

    /**
     * Добавить шаг в конец этапа [stageId] (null — к шагам без этапа).
     *
     * Если два шага добавить очень быстро, второй может получить то же место, что и первый
     * (state ещё не успел обновиться). Ничего страшного: при равном месте шаги стоят
     * в порядке добавления (см. orderedSteps) — то есть правильно.
     */
    fun addStep(title: String, stageId: Long?) = viewModelScope.launch {
        commands.create(
            Task(
                title = title,
                goalId = goalId,
                stageId = stageId,
                position = positionForNewStep(state.value.steps, stageId),
            ),
        )
    }

    /** Добавить этап в конец цели. */
    fun addStage(title: String) = viewModelScope.launch {
        goals.saveStage(
            GoalStage(
                goalId = goalId,
                title = title,
                position = nextPosition(state.value.stages.map { it.position })
            ),
        )
    }

    fun saveGoal(goal: Goal) = viewModelScope.launch { goals.save(goal) }

    fun toggle(step: Task) = viewModelScope.launch { commands.toggleDone(step) }
    fun schedule(step: Task, slot: Slot) = viewModelScope.launch { commands.schedule(step, slot) }
    fun saveStep(result: EditorResult) = viewModelScope.launch { commands.save(result) }
    fun deleteStep(step: Task) = viewModelScope.launch { commands.delete(step) }

    /**
     * Сохранить порядок после перетаскивания (или «Выше» / «Ниже» в TalkBack).
     * [groups] — шаги так, как они стоят на экране; что поменялось, считает rearrangedSteps,
     * и записывается всё одной записью — список не мигает промежуточными состояниями.
     */
    fun reorder(groups: List<StepGroup>) = viewModelScope.launch {
        val changed = rearrangedSteps(groups)
        if (changed.isNotEmpty()) tasks.saveAll(changed)
    }

    fun renameStage(stage: GoalStage) = viewModelScope.launch { goals.saveStage(stage) }

    /**
     * Удалить этап. Его шаги не пропадают: база сама уберёт им этап (SET_NULL), и они станут
     * шагами без этапа. «Отменить» возвращает этап с тем же id и снова кладёт в него его шаги.
     */
    fun deleteStage(stage: GoalStage) = viewModelScope.launch {
        val itsSteps = state.value.steps.filter { it.stageId == stage.id }
        goals.deleteStage(stage)
        messages.post(
            UiMessage(
                UiText.Res(R.string.msg_stage_deleted),
                undo = {
                    goals.saveStage(stage)
                    tasks.saveAll(itsSteps)
                },
            ),
        )
    }
}
