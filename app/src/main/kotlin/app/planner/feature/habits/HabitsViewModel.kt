package app.planner.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.planner.common.EditorResult
import app.planner.common.MedicationCommands
import app.planner.common.TaskCommands
import app.planner.common.TodayClock
import app.planner.core.domain.medication.MedicationHistory
import app.planner.core.domain.medication.Medications
import app.planner.core.domain.model.Goal
import app.planner.core.domain.model.Medication
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.recurrence.HabitHistory
import app.planner.core.domain.repository.IGoalRepository
import app.planner.core.domain.repository.IMedicationRepository
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
import java.time.LocalDateTime
import javax.inject.Inject

/** Одна привычка на экране: само правило и его мягкая статистика за 30 дней. */
data class HabitItem(val rule: RecurrenceRule, val history: HabitHistory)

/** Одно лекарство на экране: само лекарство и как его принимали за 30 дней. */
data class MedicationItem(val medication: Medication, val history: MedicationHistory)

/**
 * Состояние экрана «Привычки».
 *
 * @param medications лекарства — второй раздел экрана. Закончившиеся курсы — в конце, отдельно
 *   (см. [finishedMedications]): их не удаляют сразу, вдруг курс повторят.
 * @param lists и [goals] нужны только редактору привычки (строка «Помогает цели»).
 */
data class HabitsUiState(
    val today: LocalDate,
    val habits: List<HabitItem> = emptyList(),
    val medications: List<MedicationItem> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val loaded: Boolean = false,
) {
    /** Лекарства, которые принимают сейчас (или начнут позже, или на перерыве). */
    val activeMedications: List<MedicationItem>
        get() = medications.filterNot { Medications.isFinished(it.medication, today) }

    /** Курсы, которые уже закончились. */
    val finishedMedications: List<MedicationItem>
        get() = medications.filter { Medications.isFinished(it.medication, today) }
}

/**
 * ViewModel экрана «Привычки»: все привычки — и те, что идут, и те, что начнутся позже,
 * и те, что на паузе, — и все лекарства. На «Сегодня» видны только сегодняшние «таблетки» и приёмы,
 * а здесь — всё сразу, чтобы было где поправить, даже если сегодня этого нет.
 *
 * Конвейер тот же, что в «Сегодня»: todayFlow → flatMapLatest { combine(…) } → stateIn.
 * Отметки за 30 дней нужны для статистики (HabitHistory, MedicationHistory).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HabitsViewModel @Inject constructor(
    tasks: ITaskRepository,
    recurrenceRepository: IRecurrenceRepository,
    listRepository: ITaskListRepository,
    goalRepository: IGoalRepository,
    medicationRepository: IMedicationRepository,
    private val commands: TaskCommands,
    private val medicationCommands: MedicationCommands,
    todayClock: TodayClock,
) : ViewModel() {

    val state: StateFlow<HabitsUiState> = todayClock.todayFlow
        .flatMapLatest { today ->
            val from = today.minusDays(HabitHistory.DAYS - 1L)
            combine(
                recurrenceRepository.observeRules(),
                tasks.observeOccurrenceTasks(from, today),
                // Лекарства с их отметками — одним потоком: у combine пять «мест», а потоков шесть
                combine(
                    medicationRepository.observeMedications(),
                    medicationRepository.observeTaken(from, today),
                ) { medications, taken ->
                    val now = LocalDateTime.now()
                    medications.map { MedicationItem(it, Medications.history(it, taken, now)) }
                },
                listRepository.observeLists(),
                goalRepository.observeGoals(),
            ) { rules, done, medications, lists, goals ->
                HabitsUiState(
                    today = today,
                    // Порядок — как у «таблеток» на «Сегодня» (так правила приходят из базы)
                    habits = rules.filter { it.isHabit }
                        .map { HabitItem(it, HabitHistory.of(it, done, today)) },
                    medications = medications,
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
    fun pauseRule(ruleId: Long, until: LocalDate?) =
        viewModelScope.launch { commands.pauseRule(ruleId, until) }

    fun resumeRule(ruleId: Long) = viewModelScope.launch { commands.resumeRule(ruleId) }

    /** Новый порядок привычек (перетащили) — так же пойдут и «таблетки» на «Сегодня». */
    fun reorder(ids: List<Long>) = viewModelScope.launch { commands.reorderRules(ids) }

    fun saveMedication(medication: Medication) =
        viewModelScope.launch { medicationCommands.save(medication) }

    fun deleteMedication(medication: Medication) =
        viewModelScope.launch { medicationCommands.delete(medication) }

    fun pauseMedication(id: Long, until: LocalDate?) =
        viewModelScope.launch { medicationCommands.pause(id, until) }

    fun resumeMedication(id: Long) = viewModelScope.launch { medicationCommands.resume(id) }
}
