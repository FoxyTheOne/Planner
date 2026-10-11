package app.planner.feature.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.planner.common.DateTexts
import app.planner.common.TaskCommands
import app.planner.common.TodayClock
import app.planner.common.dayItems
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.recurrence.Occurrence
import app.planner.core.domain.recurrence.Recurrence
import app.planner.core.domain.repository.IRecurrenceRepository
import app.planner.core.domain.repository.ITaskListRepository
import app.planner.core.domain.repository.ITaskRepository
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

/**
 * ViewModel экрана «Неделя» и почасовой шкалы дня.
 *
 * Хранит, какая неделя сейчас открыта ([weekStart]), и собирает для неё задачи, списки,
 * кандидатов «из списков» и хвосты для лотка планирования. Листание недель — это просто
 * смена weekStart: flatMapLatest отменяет старую подписку на базу и создаёт новую.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeekViewModel @Inject constructor(
    private val tasks: ITaskRepository,
    recurrenceRepository: IRecurrenceRepository,
    listRepository: ITaskListRepository,
    private val commands: TaskCommands,
    private val todayClock: TodayClock,
) : ViewModel() {

    // MutableStateFlow — изменяемое значение, на которое можно подписаться.
    // Первый день открытой недели; меняется стрелками в шапке.
    private val weekStart = MutableStateFlow(DateTexts.startOfWeek(todayClock.today()))

    val state: StateFlow<WeekUiState> =
        combine(weekStart, todayClock.todayFlow) { start, today -> start to today }
            .flatMapLatest { (start, today) ->
                val end = start.plusDays(6)
                combine(
                    tasks.observeTasksBetween(start, end),
                    listRepository.observeLists(),
                    tasks.observeUnscheduled(),
                    tasks.observeOverdue(today),
                    // Дни повторений на эту неделю, ещё не отмеченные (как в «Сегодня», только на 7 дней)
                    combine(
                        recurrenceRepository.observeRules(),
                        tasks.observeOccurrenceTasks(start, end)
                    ) { rules, done ->
                        Recurrence.occurrencesBetween(rules, start, end, instances = done)
                    },
                ) { weekTasks, lists, unscheduled, overdue, occurrences ->
                    val days = (0L..6L).map(start::plusDays)
                    val tasksByDay = weekTasks.groupBy { it.date!! }
                    val occurrencesByDay = occurrences.groupBy { it.date }
                    WeekUiState(
                        today = today,
                        weekStart = start,
                        days = days,
                        itemsByDay = days.associateWith { day ->
                            dayItems(tasksByDay[day].orEmpty(), occurrencesByDay[day].orEmpty())
                        },
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
    fun completeOccurrence(occurrence: Occurrence) =
        viewModelScope.launch { commands.completeOccurrence(occurrence) }

    fun pauseRule(ruleId: Long, until: LocalDate?) = viewModelScope.launch { commands.pauseRule(ruleId, until) }
    fun resumeRule(ruleId: Long) = viewModelScope.launch { commands.resumeRule(ruleId) }

    fun delete(task: Task) = viewModelScope.launch { commands.delete(task) }
    fun schedule(task: Task, slot: Slot) = viewModelScope.launch { commands.schedule(task, slot) }

    /** Сохранить из редактора; [newListName] — если там же придумали новый список. */
    fun save(task: Task, newListName: String?, repeat: Repeat? = null) =
        viewModelScope.launch { commands.save(task, newListName, repeat) }

    fun createIn(slot: Slot, title: String) = viewModelScope.launch {
        commands.create(
            Task(
                title = title,
                date = slot.date,
                partOfDay = slot.partOfDay,
                time = slot.time
            )
        )
    }

    /** Перетащили в режиме планирования: день известен, часть дня и время задача сохраняет свои. */
    fun dropOnDay(taskId: Long, day: LocalDate) = viewModelScope.launch {
        val task = tasks.getTask(taskId) ?: return@launch
        commands.schedule(task, Slot(day, task.partOfDay, task.time))
    }
}
