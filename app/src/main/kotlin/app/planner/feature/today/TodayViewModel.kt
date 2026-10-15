package app.planner.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.planner.common.EditorResult
import app.planner.common.DayItem
import app.planner.common.TaskCommands
import app.planner.common.TodayClock
import app.planner.common.dayItems
import app.planner.core.domain.model.PartOfDay
import app.planner.core.domain.model.PlannerSettings
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.model.TaskStatus
import app.planner.core.domain.overdue.Overdue
import app.planner.core.domain.recurrence.HabitDay
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
/**
 * Повторения для «Сегодня»: ещё не отмеченные дни сегодня и завтра (завтрашние — для превью
 * «Завтра: …»), привычки на сегодня и правила, которые сейчас на паузе.
 *
 * @param habitIds id правил-привычек: их отмеченные дни приходят вместе с задачами,
 *   и по этим id мы убираем их из разделов (они уже показаны «таблетками»).
 */
private data class RepeatsOfDay(
    val occurrences: List<Occurrence>,
    val tomorrow: List<Occurrence>,
    val habits: List<HabitDay>,
    val habitIds: Set<Long>,
    val paused: List<RecurrenceRule>,
)

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
            // Повторения на сегодня (тоже combine): какие дни ещё не отмечены и какие правила на паузе
            combine(
                recurrenceRepository.observeRules(),
                tasks.observeOccurrenceTasks(today, today.plusDays(1))
            ) { rules, done ->
                // partition делит список на два: (подходит, не подходит) — привычки отдельно от дел
                val (habits, plans) = rules.partition { it.isHabit }
                RepeatsOfDay(
                    occurrences = Recurrence.occurrencesOn(plans, today, instances = done),
                    tomorrow = Recurrence.occurrencesOn(plans, today.plusDays(1), instances = done),
                    habits = Recurrence.habitsOn(habits, today, instances = done),
                    habitIds = habits.map { it.id }.toSet(),
                    paused = rules.filter { Recurrence.activePause(it, today) != null },
                )
            },
        ) { twoDays, overdue, settings, lists, repeats ->
            buildState(
                today,
                twoDays,
                overdue,
                settings,
                lists,
                repeats
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
        repeats: RepeatsOfDay,
    ): TodayUiState {
        val occurrences = repeats.occurrences
        // Отмеченные привычки — тоже задачи (с ruleId), но они уже нарисованы «таблетками»
        val (todayTasks, tomorrowTasks) = twoDays
            .filterNot { it.ruleId in repeats.habitIds }
            .partition { it.date == today }
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
            // В превью «Завтра» — и задачи, и дни повторений (бассейн в 18:30 тоже важно увидеть заранее)
            tomorrowCount = tomorrowTasks.size + repeats.tomorrow.size,
            tomorrowTimed = (
                    tomorrowTasks.filter { it.time != null && !it.isDone } +
                            repeats.tomorrow.filter { it.rule.time != null }
                                .map { Recurrence.toTask(it, TaskStatus.OPEN) }
                    ).sortedBy { it.time },
            overdue = Overdue.grouped(overdue, today),
            showOverdueBanner = overdue.isNotEmpty() && settings.overdueBannerHiddenOn != today,
            lists = lists,
            paused = repeats.paused,
            habits = repeats.habits,
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

    /**
     * Нажали на «таблетку» привычки.
     * - Отмечена — снимаем: toggleDone удалит задачу, и «таблетка» снова будет пустой (по правилу).
     * - Не отмечена — отмечаем через completeOccurrence, даже если задача на этот день уже есть.
     *   Так бывает, если правило раньше было делом и этот день перенесли или пропустили.
     *   completeOccurrence возьмёт id той задачи и запишет её заново: сделано, на свой день.
     *   (toggleDone тут не подошёл бы: отметил бы сделанным перенесённый день — на чужую дату.)
     */
    fun toggleHabit(habit: HabitDay) = viewModelScope.launch {
        val task = habit.task
        if (task != null && task.isDone) commands.toggleDone(task)
        else commands.completeOccurrence(habit.occurrence)
    }

    fun skipOccurrence(task: Task) = viewModelScope.launch { commands.skipOccurrence(task) }
    fun pauseRule(ruleId: Long, until: LocalDate?) =
        viewModelScope.launch { commands.pauseRule(ruleId, until) }

    fun resumeRule(ruleId: Long) = viewModelScope.launch { commands.resumeRule(ruleId) }

    fun moveToTomorrow(task: Task) = viewModelScope.launch { commands.moveToTomorrow(task) }
    fun schedule(task: Task, slot: Slot) = viewModelScope.launch { commands.schedule(task, slot) }
    fun returnToList(task: Task) = viewModelScope.launch { commands.returnToList(task) }
    fun delete(task: Task) = viewModelScope.launch { commands.delete(task) }

    /** Сохранить то, что ввели в редакторе (задачу или повторение), — см. TaskCommands.save. */
    fun save(result: EditorResult) = viewModelScope.launch { commands.save(result) }

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
