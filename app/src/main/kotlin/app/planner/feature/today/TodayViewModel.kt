package app.planner.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.planner.common.DayItem
import app.planner.common.EditorResult
import app.planner.common.MedicationCommands
import app.planner.common.TaskCommands
import app.planner.common.TodayClock
import app.planner.common.dayItems
import app.planner.core.domain.medication.Dose
import app.planner.core.domain.medication.MedicationHistory
import app.planner.core.domain.medication.Medications
import app.planner.core.domain.model.Goal
import app.planner.core.domain.model.Medication
import app.planner.core.domain.model.PartOfDay
import app.planner.core.domain.model.PlannerSettings
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Reminder
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.model.TaskStatus
import app.planner.core.domain.overdue.Overdue
import app.planner.core.domain.recurrence.HabitDay
import app.planner.core.domain.recurrence.HabitHistory
import app.planner.core.domain.recurrence.Occurrence
import app.planner.core.domain.recurrence.Recurrence
import app.planner.core.domain.repository.IGoalRepository
import app.planner.core.domain.repository.IMedicationRepository
import app.planner.core.domain.repository.IRecurrenceRepository
import app.planner.core.domain.repository.ISettingsRepository
import app.planner.core.domain.repository.ITaskListRepository
import app.planner.core.domain.repository.ITaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
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
 * @param habitHistory статистика каждой привычки по id правила — и тех, что сегодня на паузе.
 */
/** Лекарства дня: приёмы на сегодня, все лекарства и история каждого (по id). */
private data class MedicationsOfDay(
    val doses: List<Dose>,
    val medications: List<Medication>,
    val history: Map<Long, MedicationHistory>,
)

private data class RepeatsOfDay(
    val occurrences: List<Occurrence>,
    val tomorrow: List<Occurrence>,
    val habits: List<HabitDay>,
    val habitHistory: Map<Long, HabitHistory>,
    val paused: List<RecurrenceRule>,
    /** Все правила по id — чтобы из дня повторения открыть его правило («Изменить все дни»). */
    val rules: Map<Long, RecurrenceRule>,
) {
    /**
     * id правил-привычек: их отмеченные дни приходят вместе с задачами, и по этим id
     * мы убираем их из разделов (они уже показаны «таблетками»). Ключи словаря — это и есть все привычки.
     */
    val habitIds: Set<Long> get() = habitHistory.keys
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val tasks: ITaskRepository,
    recurrenceRepository: IRecurrenceRepository,
    listRepository: ITaskListRepository,
    goalRepository: IGoalRepository,
    private val settingsRepository: ISettingsRepository,
    private val medicationRepository: IMedicationRepository,
    private val commands: TaskCommands,
    private val medicationCommands: MedicationCommands,
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
     * 1. combine следит за ПЯТЬЮ потоками (задачи, хвосты, настройки, списки с целями, повторения).
     *    Два из них — сами маленькие combine: «списки + цели» (пара) и «правила + уже отмеченные дни →
     *    что ещё осталось отметить».
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
            // Списки, цели и задачи без даты — одним потоком: у combine всего пять «мест».
            // ::Triple — «склей три значения вместе»; ниже разбираем обратно: (lists, goals, unscheduled).
            // Задачи без даты — для окна «Добавить»: взять готовую из списков на сегодня
            combine(
                listRepository.observeLists(),
                goalRepository.observeGoals(),
                tasks.observeUnscheduled(),
                ::Triple,
            ),
            // Повторения на сегодня (тоже combine): какие дни ещё не отмечены и какие правила на паузе
            combine(
                recurrenceRepository.observeRules(),
                // С запасом на 30 дней назад — для статистики привычек. Для сегодня и завтра лишние
                // дни не мешают: occurrencesOn и HabitDay.on сами берут отметки только своего дня
                tasks.observeOccurrenceTasks(
                    today.minusDays(HabitHistory.DAYS - 1L),
                    today.plusDays(1)
                )
            ) { rules, done ->
                // partition делит список на два: (подходит, не подходит) — привычки отдельно от дел
                val (habits, plans) = rules.partition { it.isHabit }
                RepeatsOfDay(
                    occurrences = Recurrence.occurrencesOn(plans, today, instances = done),
                    tomorrow = Recurrence.occurrencesOn(plans, today.plusDays(1), instances = done),
                    habits = HabitDay.on(habits, today, instances = done),
                    // associate — список → словарь: каждому правилу (по id) его история
                    habitHistory = habits.associate { it.id to HabitHistory.of(it, done, today) },
                    paused = rules.filter { Recurrence.activePause(it, today) != null },
                    rules = rules.associateBy { it.id },
                )
            },
        ) { twoDays, overdue, settings, (lists, goals, unscheduled), repeats ->
            buildState(
                today,
                twoDays,
                overdue,
                settings,
                lists,
                goals,
                unscheduled,
                repeats
            ) // ← результат уходит в поток combine
        }
            // И лекарства — ещё одним combine поверх готового плана дня: у первого все пять «мест» заняты.
            // План и лекарства меняются независимо, а на экран всегда уходит их последняя пара
            .combine(medicationsOf(today)) { plan, meds ->
                plan.copy(
                    doses = meds.doses,
                    medications = meds.medications,
                    medicationHistory = meds.history,
                )
            }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TodayUiState(todayClock.today())
    )

    /**
     * Лекарства на [today]: приёмы дня и история каждого лекарства за 30 дней.
     * Отметки берём сразу за 30 дней — для истории; приёмам дня Medications сам выберет отметки своего дня.
     */
    private fun medicationsOf(today: LocalDate): Flow<MedicationsOfDay> =
        combine(
            medicationRepository.observeMedications(),
            medicationRepository.observeTaken(today.minusDays(MedicationHistory.DAYS - 1L), today),
        ) { medications, taken ->
            MedicationsOfDay(
                doses = Medications.dosesOn(medications, today, taken),
                medications = medications,
                history = medications.associate {
                    it.id to Medications.history(
                        it,
                        taken,
                        LocalDateTime.now()
                    )
                },
            )
        }

    // --- Действия пользователя. launch запускает корутину и сразу возвращается,
    // поэтому экран не «замирает», пока идёт запись в базу. ---

    /** Отметить приём лекарства или снять отметку. */
    fun toggleDose(dose: Dose) = viewModelScope.launch { medicationCommands.toggle(dose) }
    fun saveMedication(medication: Medication) =
        viewModelScope.launch { medicationCommands.save(medication) }

    fun deleteMedication(medication: Medication) =
        viewModelScope.launch { medicationCommands.delete(medication) }

    fun pauseMedication(id: Long, until: LocalDate?) =
        viewModelScope.launch { medicationCommands.pause(id, until) }

    fun resumeMedication(id: Long) = viewModelScope.launch { medicationCommands.resume(id) }

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
    fun schedule(task: Task, slot: Slot, reminder: Reminder? = task.reminder) =
        viewModelScope.launch { commands.schedule(task, slot, reminder) }

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

    private fun buildState(
        today: LocalDate,
        twoDays: List<Task>,
        overdue: List<Task>,
        settings: PlannerSettings,
        lists: List<TaskList>,
        goals: List<Goal>,
        unscheduled: List<Task>,
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
            // Цели: по ним подписываются шаги целей и выбирается «Помогает цели» у привычки
            goals = goals,
            unscheduled = unscheduled,
            paused = repeats.paused,
            rules = repeats.rules,
            habits = repeats.habits,
            habitHistory = repeats.habitHistory,
        )
    }

    /** В какую часть дня попадает строка: по точному времени, а если его нет — по выбранной части дня. */
    private fun sectionOf(item: DayItem, settings: PlannerSettings): DaySection {
        val part = item.time?.let(settings::partOfDayFor) ?: item.partOfDay
        return when (part) {
            PartOfDay.MORNING -> DaySection.MORNING
            PartOfDay.DAY -> DaySection.DAY
            PartOfDay.EVENING -> DaySection.EVENING
            null -> DaySection.ANYTIME
        }
    }
}
