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
import app.planner.core.domain.recurrence.HabitHistory
import app.planner.core.domain.repository.IGoalRepository
import app.planner.core.domain.repository.IRecurrenceRepository
import app.planner.core.domain.repository.ITaskListRepository
import app.planner.core.domain.repository.ITaskRepository
import app.planner.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
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
    private val rules: IRecurrenceRepository,
    private val listRepository: ITaskListRepository,
    private val commands: TaskCommands,
    private val messages: MessageBus,
    todayClock: TodayClock,
) : ViewModel() {

    private val goalId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_GOAL_ID))

    /*
     * Как в «Сегодня»: дата — снаружи (flatMapLatest), всё остальное — внутри.
     * Статистике привычек нужны отметки «за последние 30 дней», а это запрос с датами —
     * в полночь его надо пересоздать с новыми датами. flatMapLatest так и делает: пришёл новый
     * день — старый combine (со вчерашним запросом) отменяется, создаётся новый.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<GoalUiState> = todayClock.todayFlow.flatMapLatest { today ->
        combine(
            goals.observeGoal(goalId),
            goals.observeStages(goalId),
            tasks.observeGoalSteps(goalId),
            linkedHabits(today),
            listRepository.observeLists(),
        ) { goal, stages, steps, habits, lists ->
            GoalUiState(
                today = today,
                goal = goal,
                overview = goal?.let { GoalOverview.of(it, stages, steps, today) },
                groups = stepGroups(stages, steps),
                stages = stages,
                steps = steps,
                habits = habits,
                lists = lists,
                loaded = true,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        GoalUiState(todayClock.today())
    )

    /**
     * Привычки, которые помогают этой цели, и мягкая статистика каждой за 30 дней — та же,
     * что в редакторе привычки (HabitHistory). Отметки берём «с запасом» на весь период.
     */
    private fun linkedHabits(today: LocalDate) = combine(
        rules.observeRules(),
        tasks.observeOccurrenceTasks(today.minusDays(HabitHistory.DAYS - 1L), today),
    ) { allRules, done ->
        allRules
            .filter { it.isHabit && it.goalId == goalId }
            .map { LinkedHabit(it, HabitHistory.of(it, done, today)) }
    }

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

    /** Отметить цель достигнутой: она уходит в архив. «Отменить» — вернуть как было. */
    fun achieve() = viewModelScope.launch {
        val goal = state.value.goal ?: return@launch
        goals.save(goal.copy(achievedOn = state.value.today))
        messages.post(
            UiMessage(
                UiText.Res(R.string.msg_goal_achieved),
                undo = { goals.save(goal) })
        )
    }

    /** Вернуть достигнутую цель в работу — например, отметили по ошибке или появились новые шаги. */
    fun reopen() = viewModelScope.launch {
        state.value.goal?.let { goals.save(it.copy(achievedOn = null)) }
    }

    /** Новый срок или «Без срока» (null) — из строки «срок был …», когда срок прошёл. */
    fun setDeadline(deadline: LocalDate?) = viewModelScope.launch {
        state.value.goal?.let { goals.save(it.copy(deadline = deadline)) }
    }

    /**
     * Удалить цель вместе с шагами. Этапы удалит сама база (CASCADE), а у привычек, которые
     * помогали этой цели, база обнулит goalId (SET_NULL).
     *
     * «Отменить» возвращает всё как было: цель с тем же id, её этапы и шаги, а привычкам —
     * связь с целью. Поэтому всё это запоминаем ДО удаления.
     *
     * NonCancellable — «эту работу не прерывать». Как только цель удалена, экран видит, что
     * цели больше нет, и уходит назад, а вместе с экраном закрывается и эта ViewModel (и её корутины).
     * Без NonCancellable корутину могло бы оборвать между удалением и сообщением —
     * цель удалена, а «Отменить» человек так и не увидел.
     */
    fun deleteGoal() = viewModelScope.launch {
        val goal = state.value.goal ?: return@launch
        val stages = state.value.stages
        val steps = state.value.steps
        withContext(NonCancellable) {
            val habits = rules.observeRules().first().filter { it.goalId == goal.id }
            tasks.deleteGoalSteps(goal.id)
            goals.delete(goal)
            messages.post(
                UiMessage(
                    UiText.Res(R.string.msg_goal_deleted),
                    undo = {
                        // Порядок важен: сначала цель, потом то, что на неё ссылается
                        goals.save(goal)
                        stages.forEach { goals.saveStage(it) }
                        tasks.saveAll(steps)
                        habits.forEach { rules.save(it) }
                    },
                ),
            )
        }
    }

    fun toggle(step: Task) = viewModelScope.launch { commands.toggleDone(step) }
    fun schedule(step: Task, slot: Slot) = viewModelScope.launch { commands.schedule(step, slot) }
    fun saveStep(result: EditorResult) = viewModelScope.launch { commands.save(result) }
    fun deleteStep(step: Task) = viewModelScope.launch { commands.delete(step) }

    // Привычки цели правятся тем же редактором и теми же командами, что в «Сегодня»:
    // сохранить правило, удалить его (с «Отменить»), поставить на паузу и продолжить
    fun saveHabit(result: EditorResult) = viewModelScope.launch { commands.save(result) }
    fun deleteHabit(draft: Task) = viewModelScope.launch { commands.delete(draft) }
    fun pauseHabit(ruleId: Long, until: LocalDate?) =
        viewModelScope.launch { commands.pauseRule(ruleId, until) }

    fun resumeHabit(ruleId: Long) = viewModelScope.launch { commands.resumeRule(ruleId) }

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
