package app.planner.feature.today

import app.planner.common.DayItem
import app.planner.common.dayItems
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
import app.planner.core.domain.recurrence.Occurrence
import app.planner.core.domain.recurrence.Recurrence
import app.planner.core.domain.repository.IRecurrenceRepository
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
    recurrenceRepository: IRecurrenceRepository,
    listRepository: ITaskListRepository,
    private val settingsRepository: ISettingsRepository,
    private val commands: TaskCommands,
    todayClock: TodayClock,
) : ViewModel() {

    /*
     * КАК СОБИРАЕТСЯ СОСТОЯНИЕ ЭКРАНА — конвейер из трёх частей (читать изнутри наружу):
     *
     *   todayFlow                      Flow<LocalDate>          дата; меняется в полночь
     *     .flatMapLatest { today ->
     *         combine(5 потоков) {…}   Flow<TodayUiState>       готовое состояние на этот день
     *     }
     *     .stateIn(…)                  StateFlow<TodayUiState>  последнее состояние, читается сразу
     *
     * 1. combine следит за ПЯТЬЮ потоками (задачи, хвосты, настройки, списки, повторения).
     *    Пятый — сам маленький combine: правила + уже отмеченные дни → что ещё осталось отметить.
     *    Изменился любой — берёт последние значения всех пяти и вызывает лямбду.
     *    Последняя строка лямбды — её результат: TodayUiState из buildState.
     *    combine выпускает его в свой выходной поток как новое значение.
     *
     * 2. flatMapLatest следит только за ДАТОЙ. В полночь todayFlow присылает новый день —
     *    flatMapLatest отменяет старый combine (со вчерашней подпиской на задачи) и создаёт новый.
     *    «Latest» — работает только самый последний внутренний поток.
     *    Без него после полуночи экран показывал бы вчерашние задачи.
     *
     * 3. stateIn хранит последнее значение: у обычного Flow «текущего значения» нет,
     *    а state.value можно прочитать в любой момент (так делает, например, allOverdueToToday).
     *    - viewModelScope — подписка живёт, пока жива ViewModel;
     *    - WhileSubscribed(5_000) — экран никто не смотрит 5 секунд → подписка на базу отключается;
     *      5 секунд — чтобы при повороте экрана не переподписываться впустую;
     *    - TodayUiState(today) — начальное значение, пока база ещё ничего не прислала.
     *
     * Путь одного изменения: отметили задачу → Room прислал новый список → combine вызвал
     * buildState → новый TodayUiState → state.value → экран (collectAsStateWithLifecycle) перерисовался.
     */
    val state: StateFlow<TodayUiState> = todayClock.todayFlow.flatMapLatest { today ->
        combine(
            tasks.observeTasksBetween(
                today,
                today.plusDays(1)
            ), // задачи именно этого дня (и завтрашнего)
            tasks.observeOverdue(today),
            settingsRepository.settings,
            listRepository.observeLists(),
            // Дни повторений на сегодня, ещё не отмеченные: правила + уже отмеченные дни (тоже combine)
            combine(
                recurrenceRepository.observeRules(),
                tasks.observeOccurrenceTasks(today, today)
            ) { rules, done ->
                Recurrence.occurrencesOn(rules, today, instances = done)
            },
        ) { twoDays, overdue, settings, lists, occurrences ->
            buildState(
                today,
                twoDays,
                overdue,
                settings,
                lists,
                occurrences
            ) // ← результат уходит в поток combine
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TodayUiState(todayClock.today())
    )

    private fun buildState(
        today: LocalDate,
        twoDays: List<Task>,
        overdue: List<Task>,
        settings: PlannerSettings,
        lists: List<TaskList>,
        occurrences: List<Occurrence>,
    ): TodayUiState {
        val (todayTasks, tomorrowTasks) = twoDays.partition { it.date == today }
        // Задачи и повторения — в одну кучу, дальше они сортируются и раскладываются по частям дня вместе
        val items = dayItems(todayTasks, occurrences)
        val sections = items
            // Сделанное опускается вниз своего раздела, но не пропадает — приятно видеть
            .sortedWith(compareBy<DayItem> { it.isDone }.thenBy(nullsLast()) { it.time })
            .groupBy { sectionOf(it, settings) }
        return TodayUiState(
            today = today,
            sections = sections,
            // «1 из 5»: отмеченные повторения уже стали задачами, неотмеченные — ещё строки по правилу
            doneCount = todayTasks.count { it.isDone },
            totalCount = todayTasks.size + occurrences.size,
            tomorrowCount = tomorrowTasks.size,
            tomorrowTimed = tomorrowTasks.filter { it.time != null && !it.isDone },
            overdue = Overdue.grouped(overdue, today),
            showOverdueBanner = overdue.isNotEmpty() && settings.overdueBannerHiddenOn != today,
            lists = lists,
        )
    }

    /** В какую часть дня попадает строка: по точному времени, а если его нет — по выбранной части дня. */
    private fun sectionOf(item: DayItem, settings: PlannerSettings): DaySection {
        val partOfDay = when (item) {
            is DayItem.TaskItem -> item.task.partOfDay
            is DayItem.RepeatItem -> item.occurrence.rule.partOfDay
        }
        val part = item.time?.let(settings::partOfDayFor) ?: partOfDay
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
    fun completeOccurrence(occurrence: Occurrence) =
        viewModelScope.launch { commands.completeOccurrence(occurrence) }

    fun moveToTomorrow(task: Task) = viewModelScope.launch { commands.moveToTomorrow(task) }
    fun schedule(task: Task, slot: Slot) = viewModelScope.launch { commands.schedule(task, slot) }
    fun returnToList(task: Task) = viewModelScope.launch { commands.returnToList(task) }
    fun delete(task: Task) = viewModelScope.launch { commands.delete(task) }

    /** Сохранить из редактора; [newListName] — если там же придумали новый список. */
    fun save(task: Task, newListName: String?) =
        viewModelScope.launch { commands.save(task, newListName) }

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
