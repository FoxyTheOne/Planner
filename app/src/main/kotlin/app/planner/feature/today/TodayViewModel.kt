package app.planner.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.planner.common.TaskCommands
import app.planner.common.TodayClock
import app.planner.core.domain.model.PartOfDay
import app.planner.core.domain.model.PlannerSettings
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.overdue.Overdue
import app.planner.core.domain.overdue.OverdueGroup
import app.planner.core.domain.repository.ISettingsRepository
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

/** Раздел экрана «Сегодня»: утро, день, вечер или «в любое время». */
enum class DaySection { MORNING, DAY, EVENING, ANYTIME }

/**
 * Всё, что нужно экрану «Сегодня», одним неизменяемым объектом.
 * Экран просто рисует это состояние; когда данные меняются — ViewModel присылает новый объект.
 */
data class TodayUiState(
    val today: LocalDate,
    val sections: Map<DaySection, List<Task>> = emptyMap(),
    val doneCount: Int = 0,
    val totalCount: Int = 0,
    val tomorrowCount: Int = 0,
    /** События завтра со временем — их важно увидеть заранее (приём у врача). */
    val tomorrowTimed: List<Task> = emptyList(),
    val overdue: Map<OverdueGroup, List<Task>> = emptyMap(),
    val showOverdueBanner: Boolean = false,
    val lists: List<TaskList> = emptyList(),
) {
    val overdueCount: Int get() = overdue.values.sumOf { it.size }
    val isEmpty: Boolean get() = totalCount == 0
}

/**
 * ViewModel экрана «Сегодня»: собирает данные для экрана и выполняет действия пользователя.
 *
 * ViewModel переживает поворот экрана и смену темы (в отличие от самого экрана),
 * поэтому данные не загружаются заново. Hilt создаёт её сам (`@HiltViewModel`)
 * и передаёт в конструктор репозитории.
 *
 * Действия (отметить, перенести, удалить) запускаются в `viewModelScope` — корутинах,
 * которые автоматически отменяются, когда экран закрыт навсегда.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val tasks: ITaskRepository,
    listRepository: ITaskListRepository,
    private val settingsRepository: ISettingsRepository,
    private val commands: TaskCommands,
    todayClock: TodayClock,
) : ViewModel() {

    /*
     * Состояние экрана собирается из нескольких потоков данных:
     * - flatMapLatest: когда наступает новый день, старая подписка отменяется и создаётся новая
     *   (иначе после полуночи экран показывал бы вчерашние задачи);
     * - combine: берёт последние значения всех четырёх потоков и, если любой изменился,
     *   заново собирает TodayUiState;
     * - stateIn: превращает поток в StateFlow — «поток с текущим значением», который экран
     *   может прочитать сразу. WhileSubscribed(5_000) держит подписку ещё 5 секунд после
     *   ухода с экрана — при повороте экрана данные не перезапрашиваются.
     */
    val state: StateFlow<TodayUiState> = todayClock.todayFlow.flatMapLatest { today ->
        combine(
            tasks.observeTasksBetween(today, today.plusDays(1)),
            tasks.observeOverdue(today),
            settingsRepository.settings,
            listRepository.observeLists(),
        ) { twoDays, overdue, settings, lists ->
            buildState(today, twoDays, overdue, settings, lists)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(todayClock.today()))

    private fun buildState(
        today: LocalDate,
        twoDays: List<Task>,
        overdue: List<Task>,
        settings: PlannerSettings,
        lists: List<TaskList>,
    ): TodayUiState {
        val (todayTasks, tomorrowTasks) = twoDays.partition { it.date == today }
        val sections = todayTasks
            // Сделанное опускается вниз своего раздела, но не пропадает — приятно видеть
            .sortedWith(compareBy<Task> { it.isDone }.thenBy(nullsLast()) { it.time })
            .groupBy { sectionOf(it, settings) }
        return TodayUiState(
            today = today,
            sections = sections,
            doneCount = todayTasks.count { it.isDone },
            totalCount = todayTasks.size,
            tomorrowCount = tomorrowTasks.size,
            tomorrowTimed = tomorrowTasks.filter { it.time != null && !it.isDone },
            overdue = Overdue.grouped(overdue, today),
            showOverdueBanner = overdue.isNotEmpty() && settings.overdueBannerHiddenOn != today,
            lists = lists,
        )
    }

    private fun sectionOf(task: Task, settings: PlannerSettings): DaySection {
        val part = task.time?.let(settings::partOfDayFor) ?: task.partOfDay
        return when (part) {
            PartOfDay.MORNING -> DaySection.MORNING
            PartOfDay.DAY -> DaySection.DAY
            PartOfDay.EVENING -> DaySection.EVENING
            null -> DaySection.ANYTIME
        }
    }

    // --- Действия пользователя. launch запускает корутину и сразу возвращается,
    // поэтому экран не «замирает», пока идёт запись в базу. ---

    fun toggle(task: Task) = viewModelScope.launch { commands.toggleDone(task) }
    fun moveToTomorrow(task: Task) = viewModelScope.launch { commands.moveToTomorrow(task) }
    fun schedule(task: Task, slot: Slot) = viewModelScope.launch { commands.schedule(task, slot) }
    fun returnToList(task: Task) = viewModelScope.launch { commands.returnToList(task) }
    fun delete(task: Task) = viewModelScope.launch { commands.delete(task) }

    /** Сохранить из редактора; [newListName] — если там же придумали новый список. */
    fun save(task: Task, newListName: String?) = viewModelScope.launch { commands.save(task, newListName) }

    fun allOverdueToToday() = viewModelScope.launch {
        val today = state.value.today
        tasks.moveOverdueToDate(today, today)
    }

    fun allOverdueBackToLists() = viewModelScope.launch {
        tasks.returnAllOverdueToLists(state.value.today)
    }

    fun hideOverdueBannerForToday() = viewModelScope.launch {
        val today = state.value.today
        settingsRepository.update { it.copy(overdueBannerHiddenOn = today) }
    }
}
