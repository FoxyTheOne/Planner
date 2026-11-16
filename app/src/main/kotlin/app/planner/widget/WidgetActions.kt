package app.planner.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.ToggleableStateKey
import androidx.glance.appwidget.action.actionRunCallback
import app.planner.core.domain.medication.Dose
import app.planner.core.domain.recurrence.HabitDay
import app.planner.core.domain.recurrence.Recurrence
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.LocalTime

/*
 * Галочки на виджете «Сегодня».
 *
 * КАК ЭТО УСТРОЕНО. Виджет — чужой процесс (рабочий стол), он не может просто вызвать нашу функцию.
 * Поэтому галочке даётся «действие» — actionRunCallback<Класс>(параметры). Нажали — система будит
 * наше приложение, создаёт этот класс (ActionCallback) и вызывает onAction с параметрами:
 * какую задачу, какой день, какой приём. Класс создаёт сама система, поэтому у него нет конструктора
 * с зависимостями: нужное он берёт сам, через WidgetEntryPoint.
 *
 * Действия те же, что на экране «Сегодня» (TaskCommands, MedicationCommands): отметить на виджете —
 * то же самое, что отметить в приложении. Перерисовывать виджет после отметки не нужно: база изменилась,
 * а виджет подписан на неё (см. PlannerWidget) — он обновится сам.
 *
 * Данные берём из базы заново, а не из виджета: пока виджет висел на экране, задачу могли поменять.
 *
 * «ПОСТАВИТЬ», А НЕ «ПЕРЕКЛЮЧИТЬ». Вместе с нажатием Glance сообщает, какой стала галочка
 * (ToggleableStateKey: true — поставили, false — сняли). Делаем именно это, а если в базе уже так —
 * ничего. Иначе быстрое двойное нажатие (виджет ещё не успел перерисоваться, и оба раза галочка
 * была пустой) отметило бы дело и тут же сняло отметку обратно. Если ключа почему-то нет —
 * просто переключаем, как на «Сегодня».
 */

/** Параметры действий: что именно отметить. День — числом дней от 1970 года, время — секундами от полуночи. */
private object Keys {
    val TASK_ID = ActionParameters.Key<Long>("taskId")
    val RULE_ID = ActionParameters.Key<Long>("ruleId")
    val MEDICATION_ID = ActionParameters.Key<Long>("medicationId")
    val DATE = ActionParameters.Key<Long>("date")
    val TIME = ActionParameters.Key<Int>("time")
}

private fun entry(context: Context) =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)

private fun ActionParameters.date(): LocalDate? = this[Keys.DATE]?.let(LocalDate::ofEpochDay)

/**
 * Нужно ли что-то менять: галочку поставили или сняли, а в базе сейчас [isChecked].
 * Совпадает — уже сделано (например, второе нажатие подряд). Нет ключа — меняем (переключаем).
 */
private fun ActionParameters.needsChange(isChecked: Boolean): Boolean =
    this[ToggleableStateKey]?.let { it != isChecked } ?: true

/** Галочка задачи (в том числе уже отмеченного или перенесённого дня повторения) — как на «Сегодня». */
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entry = entry(context)
        val task = parameters[Keys.TASK_ID]?.let { entry.tasks().getTask(it) } ?: return
        if (parameters.needsChange(task.isDone)) entry.taskCommands().toggleDone(task)
    }
}

/**
 * Галочка ещё не отмеченного дня повторения: в базе его нет, есть правило и день.
 * Только «отметить»: снять тут нечего (отмеченный день — уже задача, у неё ToggleTaskAction).
 * Отметить второй раз не страшно: completeOccurrence возьмёт id уже созданной задачи, дубля не будет.
 */
class CompleteRepeatDayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (parameters[ToggleableStateKey] == false) return
        val entry = entry(context)
        val rule = parameters[Keys.RULE_ID]?.let { entry.rules().getRule(it) } ?: return
        val occurrence = parameters.date()?.let { Recurrence.occurrence(rule, it) } ?: return
        entry.taskCommands().completeOccurrence(occurrence)
    }
}

/** Галочка привычки: отметить или снять отметку (TaskCommands.toggleHabit). */
class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entry = entry(context)
        val ruleId = parameters[Keys.RULE_ID] ?: return
        val date = parameters.date() ?: return
        val rule = entry.rules().getRule(ruleId) ?: return
        val occurrence = Recurrence.occurrence(rule, date) ?: return
        val mark = entry.tasks().getOccurrenceTasks(ruleId).firstOrNull { it.occurrenceDate == date }
        val habit = HabitDay(occurrence, mark)
        if (parameters.needsChange(habit.isDone)) entry.taskCommands().toggleHabit(habit)
    }
}

/** Галочка приёма лекарства: принято или снять отметку (MedicationCommands.toggle). */
class ToggleDoseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entry = entry(context)
        val medicationId = parameters[Keys.MEDICATION_ID] ?: return
        val date = parameters.date() ?: return
        val time = parameters[Keys.TIME]?.let { LocalTime.ofSecondOfDay(it.toLong()) } ?: return
        val medication = entry.medications().getMedication(medicationId) ?: return
        val taken = entry.medications().getTaken(medicationId, date).firstOrNull { it.date == date && it.time == time }
        if (parameters.needsChange(taken != null)) entry.medicationCommands().toggle(Dose(medication, date, time, taken))
    }
}

// --- Готовые действия для строк виджета (см. PlannerWidget) ---

fun toggleTask(taskId: Long) = actionRunCallback<ToggleTaskAction>(actionParametersOf(Keys.TASK_ID to taskId))

fun completeRepeatDay(ruleId: Long, date: LocalDate) = actionRunCallback<CompleteRepeatDayAction>(
    actionParametersOf(Keys.RULE_ID to ruleId, Keys.DATE to date.toEpochDay()),
)

fun toggleHabit(ruleId: Long, date: LocalDate) = actionRunCallback<ToggleHabitAction>(
    actionParametersOf(Keys.RULE_ID to ruleId, Keys.DATE to date.toEpochDay()),
)

fun toggleDose(medicationId: Long, date: LocalDate, time: LocalTime) = actionRunCallback<ToggleDoseAction>(
    actionParametersOf(
        Keys.MEDICATION_ID to medicationId,
        Keys.DATE to date.toEpochDay(),
        Keys.TIME to time.toSecondOfDay(),
    ),
)
