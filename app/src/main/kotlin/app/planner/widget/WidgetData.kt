package app.planner.widget

import app.planner.common.DayItem
import app.planner.common.MedicationCommands
import app.planner.common.TaskCommands
import app.planner.common.dayItems
import app.planner.core.domain.medication.Dose
import app.planner.core.domain.medication.Medications
import app.planner.core.domain.model.DoseTaken
import app.planner.core.domain.model.Medication
import app.planner.core.domain.model.PlannerSettings
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Task
import app.planner.core.domain.recurrence.HabitDay
import app.planner.core.domain.recurrence.Recurrence
import app.planner.core.domain.repository.IMedicationRepository
import app.planner.core.domain.repository.IRecurrenceRepository
import app.planner.core.domain.repository.ISettingsRepository
import app.planner.core.domain.repository.ITaskRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * «Точка входа» Hilt для виджета. Виджет — не экран и не ViewModel: Hilt не может сам передать ему
 * хранилища в конструктор (виджет создаёт система). Поэтому виджет просит их сам:
 * EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).tasks().
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun tasks(): ITaskRepository
    fun rules(): IRecurrenceRepository
    fun medications(): IMedicationRepository
    fun settings(): ISettingsRepository
    fun taskCommands(): TaskCommands
    fun medicationCommands(): MedicationCommands
}

/**
 * Один день на виджете: дела и дни повторений ([items]), приёмы лекарств ([doses]), привычки ([habits]).
 * Лекарства и привычки пустые, если в настройках виджета они выключены.
 */
data class WidgetDay(
    val date: LocalDate,
    val items: List<DayItem>,
    val doses: List<Dose>,
    val habits: List<HabitDay>,
) {
    val isEmpty: Boolean get() = items.isEmpty() && doses.isEmpty() && habits.isEmpty()
}

/** Всё, что рисует виджет: сегодня, завтра (если его сейчас показывать) и настройки приложения (тема). */
data class WidgetContent(
    val today: WidgetDay,
    val tomorrow: WidgetDay?,
    val settings: PlannerSettings,
)

/**
 * Всё, из чего собирается виджет, — «сырые» данные из базы за сегодня и завтра.
 * Отдельно от [WidgetContent]: данные приходят подпиской ([observeWidgetSource]), а что из них
 * показать, зависит ещё от настроек конкретного виджета и от часа ([toContent]).
 */
data class WidgetSource(
    val today: LocalDate,
    val tasks: List<Task>,
    val instances: List<Task>,
    val rules: List<RecurrenceRule>,
    val medications: List<Medication>,
    val taken: List<DoseTaken>,
    val settings: PlannerSettings,
)

/**
 * Подписка на данные виджета за [today] и завтра. Поменялось что-то в базе или в настройках —
 * поток присылает новые данные, и виджет, пока он «живой» (см. PlannerWidget), перерисуется сам.
 * У combine пять «мест», а потоков шесть — настройки добавляем вторым combine.
 *
 * Её же слушает WidgetUpdater, чтобы знать, когда будить виджет: так список «на что смотрит виджет»
 * записан в одном месте, и новое на виджете не забудется в обновлении.
 */
fun observeWidgetSource(
    tasks: ITaskRepository,
    rules: IRecurrenceRepository,
    medications: IMedicationRepository,
    settings: ISettingsRepository,
    today: LocalDate,
): Flow<WidgetSource> {
    val tomorrow = today.plusDays(1)
    return combine(
        combine(
            tasks.observeTasksBetween(today, tomorrow),
            tasks.observeOccurrenceTasks(today, tomorrow),
            rules.observeRules(),
            medications.observeMedications(),
            medications.observeTaken(today, tomorrow),
        ) { dayTasks, instances, allRules, allMedications, taken ->
            WidgetSource(today, dayTasks, instances, allRules, allMedications, taken, PlannerSettings())
        },
        settings.settings,
    ) { source, current -> source.copy(settings = current) }
}

/** То же для виджета: хранилища — из [WidgetEntryPoint]. */
fun observeWidgetSource(entry: WidgetEntryPoint, today: LocalDate): Flow<WidgetSource> =
    observeWidgetSource(entry.tasks(), entry.rules(), entry.medications(), entry.settings(), today)

/**
 * Что показать на виджете с настройками [config] «как сейчас» ([now]).
 *
 * Дни собираются так же, как на «Сегодня»: задачи дня и ещё не отмеченные дни повторений-дел
 * (dayItems), отметки привычек — отдельно, «таблетками». Несделанное — сверху по времени,
 * сделанное — внизу (как на «Сегодня»: приятно видеть, но не мешает).
 *
 * Завтра показываем по настройке виджета: всегда, вечером (с часа вечерней сводки) или никогда —
 * и только если на завтра вообще что-то есть.
 */
fun WidgetSource.toContent(config: WidgetConfig, now: LocalDateTime): WidgetContent {
    val showTomorrow = when (config.tomorrow) {
        TomorrowMode.ALWAYS -> true
        TomorrowMode.EVENING -> now.toLocalTime() >= settings.eveningSummaryTime
        TomorrowMode.NEVER -> false
    }
    return WidgetContent(
        today = day(today, config),
        tomorrow = if (showTomorrow) day(today.plusDays(1), config).takeUnless { it.isEmpty } else null,
        settings = settings,
    )
}

/** Один день из [WidgetSource]: задачи и отметки берём только этого дня. */
private fun WidgetSource.day(date: LocalDate, config: WidgetConfig): WidgetDay {
    val dayTasks = tasks.filter { it.date == date }
    val dayInstances = instances.filter { it.occurrenceDate == date }
    val (habits, plans) = rules.partition { it.isHabit }
    val habitIds = habits.map { it.id }.toSet()
    val items = dayItems(
        dayTasks.filterNot { it.ruleId in habitIds },
        Recurrence.occurrencesOn(plans, date, dayInstances),
    ).sortedWith(compareBy<DayItem> { it.isDone }.thenBy(nullsLast()) { it.time })
    return WidgetDay(
        date = date,
        items = items,
        doses = if (config.medications) Medications.dosesOn(medications, date, taken) else emptyList(),
        habits = if (config.habits) HabitDay.on(habits, date, dayInstances) else emptyList(),
    )
}
