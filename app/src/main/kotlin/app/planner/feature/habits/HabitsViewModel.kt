package app.planner.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.planner.common.EditorResult
import app.planner.common.TaskCommands
import app.planner.common.TodayClock
import app.planner.core.domain.model.Goal
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.recurrence.HabitHistory
import app.planner.core.domain.repository.IGoalRepository
import app.planner.core.domain.repository.IRecurrenceRepository
import app.planner.core.domain.repository.ITaskListRepository
import app.planner.core.domain.repository.ITaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Одна привычка на экране: само правило и его мягкая статистика за 30 дней. */
data class HabitItem(val rule: RecurrenceRule, val history: HabitHistory)

/**
 * Состояние экрана «Привычки».
 *
 * @param lists и [goals] нужны только редактору привычки (строка «Помогает цели»).
 */
data class HabitsUiState(
    val today: LocalDate,
    val habits: List<HabitItem> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * ViewModel экрана «Привычки»: все привычки — и те, что идут, и те, что начнутся позже,
 * и те, что на паузе. На «Сегодня» видны только сегодняшние «таблетки», а здесь — всё сразу,
 * чтобы было где поправить привычку, даже если сегодня её нет.
 *
 * Конвейер тот же, что в «Сегодня»: todayFlow → flatMapLatest { combine(…) } → stateIn.
 * Отметки за 30 дней нужны для статистики (HabitHistory).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HabitsViewModel @Inject constructor(
    tasks: ITaskRepository,
    recurrenceRepository: IRecurrenceRepository,
    listRepository: ITaskListRepository,
    goalRepository: IGoalRepository,
    private val commands: TaskCommands,
    todayClock: TodayClock,
) : ViewModel() {

    val state: StateFlow<HabitsUiState> = todayClock.todayFlow
        .flatMapLatest { today ->
            combine(
                recurrenceRepository.observeRules(),
                tasks.observeOccurrenceTasks(today.minusDays(HabitHistory.DAYS - 1L), today),
                listRepository.observeLists(),
                goalRepository.observeGoals(),
            ) { rules, done, lists, goals ->
                HabitsUiState(
                    today = today,
                    // Порядок — как у «таблеток» на «Сегодня» (так правила приходят из базы)
                    habits = rules.filter { it.isHabit }.map { HabitItem(it, HabitHistory.of(it, done, today)) },
                    lists = lists,
                    goals = goals,
                    loaded = true,
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HabitsUiState(todayClock.today()),
        )

    fun save(result: EditorResult) = viewModelScope.launch { commands.save(result) }
    fun delete(task: Task) = viewModelScope.launch { commands.delete(task) }
    fun pauseRule(ruleId: Long, until: LocalDate?) = viewModelScope.launch { commands.pauseRule(ruleId, until) }
    fun resumeRule(ruleId: Long) = viewModelScope.launch { commands.resumeRule(ruleId) }

    /** Новый порядок привычек (перетащили) — так же пойдут и «таблетки» на «Сегодня». */
    fun reorder(ids: List<Long>) = viewModelScope.launch { commands.reorderRules(ids) }
}
