package app.planner.feature.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.planner.common.DateTexts
import app.planner.common.TaskCommands
import app.planner.common.TodayClock
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.repository.TaskListRepository
import app.planner.core.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Всё, что нужно экрану «Неделя» (и экрану одного дня), одним объектом. */
data class WeekUiState(
    val today: LocalDate,
    val weekStart: LocalDate,
    val days: List<LocalDate> = emptyList(),
    val tasksByDay: Map<LocalDate, List<Task>> = emptyMap(),
    val lists: List<TaskList> = emptyList(),
    /** Кандидаты для шторки «Из списков» и лотка планирования. */
    val unscheduled: List<Task> = emptyList(),
    val overdue: List<Task> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
/**
 * ViewModel экрана «Неделя» и почасовой шкалы дня.
 *
 * Хранит, какая неделя сейчас открыта ([weekStart]), и собирает для неё задачи, списки,
 * кандидатов «из списков» и хвосты для лотка планирования. Листание недель — это просто
 * смена weekStart: flatMapLatest отменяет старую подписку на базу и создаёт новую.
 */
@HiltViewModel
class WeekViewModel @Inject constructor(
    private val tasks: TaskRepository,
    listRepository: TaskListRepository,
    private val commands: TaskCommands,
    private val todayClock: TodayClock,
) : ViewModel() {

    // MutableStateFlow — изменяемое значение, на которое можно подписаться.
    // Первый день открытой недели; меняется стрелками в шапке.
    private val weekStart = MutableStateFlow(DateTexts.startOfWeek(todayClock.today()))

    val state: StateFlow<WeekUiState> = combine(weekStart, todayClock.todayFlow) { start, today -> start to today }
        .flatMapLatest { (start, today) ->
            combine(
                tasks.observeTasksBetween(start, start.plusDays(6)),
                listRepository.observeLists(),
                tasks.observeUnscheduled(),
                tasks.observeOverdue(today),
            ) { weekTasks, lists, unscheduled, overdue ->
                WeekUiState(
                    today = today,
                    weekStart = start,
                    days = (0L..6L).map(start::plusDays),
                    tasksByDay = weekTasks.groupBy { it.date!! },
                    lists = lists,
                    unscheduled = unscheduled,
                    overdue = overdue,
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WeekUiState(todayClock.today(), weekStart.value),
        )

    fun previousWeek() = weekStart.update { it.minusWeeks(1) }
    fun nextWeek() = weekStart.update { it.plusWeeks(1) }
    fun thisWeek() = showWeekOf(todayClock.today())

    fun showWeekOf(date: LocalDate) {
        weekStart.value = DateTexts.startOfWeek(date)
    }

    fun toggle(task: Task) = viewModelScope.launch { commands.toggleDone(task) }
    fun delete(task: Task) = viewModelScope.launch { commands.delete(task) }
    fun schedule(task: Task, slot: Slot) = viewModelScope.launch { commands.schedule(task, slot) }

    /** Сохранить из редактора; [newListName] — если там же придумали новый список. */
    fun save(task: Task, newListName: String?) = viewModelScope.launch { commands.save(task, newListName) }

    fun createIn(slot: Slot, title: String) = viewModelScope.launch {
        commands.create(Task(title = title, date = slot.date, partOfDay = slot.partOfDay, time = slot.time))
    }

    /** Перетащили в режиме планирования: день известен, часть дня и время задача сохраняет свои. */
    fun dropOnDay(taskId: Long, day: LocalDate) = viewModelScope.launch {
        val task = tasks.getTask(taskId) ?: return@launch
        commands.schedule(task, Slot(day, task.partOfDay, task.time))
    }
}
