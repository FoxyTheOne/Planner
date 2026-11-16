package app.planner.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.planner.common.TaskCommands
import app.planner.core.domain.medication.Dose
import app.planner.core.domain.medication.DoseKey
import app.planner.core.domain.model.TaskStatus
import app.planner.core.domain.recurrence.Recurrence
import app.planner.core.domain.preview.DayPreview
import app.planner.core.domain.reminder.MedicationReminders
import app.planner.core.domain.reminder.Reminders
import app.planner.core.domain.repository.IMedicationRepository
import app.planner.core.domain.repository.IRecurrenceRepository
import app.planner.core.domain.repository.ISettingsRepository
import app.planner.core.domain.repository.ITaskRepository
import app.planner.reminders.ReminderReceiver.Companion.ACTION_DONE
import app.planner.reminders.ReminderReceiver.Companion.ACTION_DOSE_DISMISSED
import app.planner.reminders.ReminderReceiver.Companion.ACTION_DOSE_SNOOZE
import app.planner.reminders.ReminderReceiver.Companion.ACTION_DOSE_TAKEN
import app.planner.reminders.ReminderReceiver.Companion.ACTION_EVENING_SUMMARY
import app.planner.reminders.ReminderReceiver.Companion.ACTION_REMIND
import app.planner.reminders.ReminderReceiver.Companion.ACTION_REMIND_DOSE
import app.planner.reminders.ReminderReceiver.Companion.ACTION_REMIND_RULE
import app.planner.reminders.ReminderReceiver.Companion.ACTION_RULE_DONE
import app.planner.reminders.ReminderReceiver.Companion.ACTION_RULE_SNOOZE
import app.planner.reminders.ReminderReceiver.Companion.ACTION_SNOOZE
import app.planner.reminders.ReminderReceiver.Companion.ACTION_TEST
import app.planner.reminders.ReminderReceiver.Companion.EXTRA_DATE
import app.planner.reminders.ReminderReceiver.Companion.EXTRA_MEDICATION_ID
import app.planner.reminders.ReminderReceiver.Companion.EXTRA_RULE_ID
import app.planner.reminders.ReminderReceiver.Companion.EXTRA_TIME
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/**
 * Приёмник всего, что связано с напоминаниями.
 *
 * О задаче:
 * - [ACTION_REMIND] — будильник ([ReminderScheduler]): пора напомнить, показываем уведомление;
 * - [ACTION_DONE] — кнопка «Готово» в уведомлении: отмечаем задачу сделанной;
 * - [ACTION_SNOOZE] — кнопка «Отложить на 10 минут»: напомним ещё раз через 10 минут.
 *
 * О дне повторения или привычки — то же самое, только про день правила ([EXTRA_RULE_ID] + [EXTRA_DATE]):
 * - [ACTION_REMIND_RULE], [ACTION_RULE_DONE], [ACTION_RULE_SNOOZE].
 *
 * О приёме лекарства — по лекарству, дню и времени приёма ([EXTRA_MEDICATION_ID] + [EXTRA_DATE] + [EXTRA_TIME]):
 * - [ACTION_REMIND_DOSE] — будильник: время приёма, повтор или «Отложить»;
 * - [ACTION_DOSE_TAKEN] — «Принято»; [ACTION_DOSE_SNOOZE] — «Отложить на 10 минут»;
 * - [ACTION_DOSE_DISMISSED] — уведомление смахнули: повторов больше не нужно.
 *
 * И [ACTION_TEST] — пробное напоминание со страницы «Надёжность напоминаний».
 * И [ACTION_EVENING_SUMMARY] — вечерняя сводка: что завтра.
 *
 * BroadcastReceiver — маленький «приёмник» сигналов от системы. Android создаёт его на один
 * сигнал, вызывает [onReceive] и сразу о нём забывает. Работать долго ему нельзя: на всё около
 * 10 секунд. Нам нужно сходить в базу (это не мгновенно), поэтому goAsync(): «я ещё не закончил,
 * подожди» — и pending.finish(), когда всё сделано.
 *
 * Будильники после «Готово» снимать не нужно: задача (или день повторения) перестала быть открытой,
 * база сообщит об этом [ReminderSync], и он сам уберёт или переставит будильник — как при отметке на экране.
 *
 * `@AndroidEntryPoint` — Hilt сам заполнит поля с `@Inject` (репозитории, уведомления), как во ViewModel.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_REMIND = "app.planner.action.REMIND"
        const val ACTION_DONE = "app.planner.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "app.planner.action.REMINDER_SNOOZE"
        const val ACTION_REMIND_RULE = "app.planner.action.REMIND_RULE"
        const val ACTION_RULE_DONE = "app.planner.action.RULE_REMINDER_DONE"
        const val ACTION_RULE_SNOOZE = "app.planner.action.RULE_REMINDER_SNOOZE"
        const val ACTION_TEST = "app.planner.action.REMINDER_TEST"
        const val ACTION_REMIND_DOSE = "app.planner.action.REMIND_DOSE"
        const val ACTION_DOSE_TAKEN = "app.planner.action.DOSE_TAKEN"
        const val ACTION_DOSE_SNOOZE = "app.planner.action.DOSE_SNOOZE"
        const val ACTION_DOSE_DISMISSED = "app.planner.action.DOSE_DISMISSED"
        const val ACTION_EVENING_SUMMARY = "app.planner.action.EVENING_SUMMARY"
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_RULE_ID = "ruleId"
        const val EXTRA_MEDICATION_ID = "medicationId"

        /** Время приёма лекарства — секундами от полуночи (LocalTime.toSecondOfDay). */
        const val EXTRA_TIME = "time"

        /** День повторения — числом дней от 1970 года (LocalDate.toEpochDay). */
        const val EXTRA_DATE = "date"
        const val SNOOZE_MINUTES = 10L
    }

    @Inject
    lateinit var tasks: ITaskRepository

    @Inject
    lateinit var rules: IRecurrenceRepository

    @Inject
    lateinit var medications: IMedicationRepository

    @Inject
    lateinit var settings: ISettingsRepository

    @Inject
    lateinit var commands: TaskCommands

    @Inject
    lateinit var notifications: ReminderNotifications

    @Inject
    lateinit var sync: ReminderSync

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    // Пробное — без задачи: просто показать уведомление (с выбранным в настройках звуком)
                    ACTION_TEST -> notifications.showTest()
                    ACTION_EVENING_SUMMARY -> onEveningSummary()
                    ACTION_REMIND, ACTION_DONE, ACTION_SNOOZE -> onTask(intent)
                    ACTION_REMIND_RULE, ACTION_RULE_DONE, ACTION_RULE_SNOOZE -> onRuleDay(intent)
                    ACTION_REMIND_DOSE, ACTION_DOSE_TAKEN, ACTION_DOSE_SNOOZE, ACTION_DOSE_DISMISSED -> onDose(intent)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun onTask(intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        if (taskId < 0) return
        // Задачу берём свежую из базы: пока ждали будильник, её могли отметить или удалить.
        // fireAt == null — напоминать больше не о чем (отметили, убрали напоминание, удалили)
        val task = tasks.getTask(taskId)
        val fireAt = task?.let(Reminders::fireAt)
        when (intent.action) {
            // Второе условие — напоминание не «из будущего». Так бывает, если задачу перенесли
            // на потом, а приложение закрылось раньше, чем успело переставить будильник: старый
            // будильник сработает в прежнее время. Тогда молчим — ReminderSync при запуске
            // поставит новый. Минута запаса — на случай, если система разбудила чуть раньше
            ACTION_REMIND -> if (task != null && fireAt != null && fireAt <= soon()) {
                notifications.show(task)
            }

            ACTION_DONE -> {
                notifications.dismiss(taskId)
                // Через TaskCommands, как галочка на экране: у «после выполнения» сразу появится следующий раз
                if (task != null && fireAt != null) commands.toggleDone(task)
            }

            ACTION_SNOOZE -> {
                notifications.dismiss(taskId)
                if (fireAt != null) sync.snooze(taskId, original = fireAt, until = snoozeUntil())
            }
        }
    }

    /**
     * День повторения. Его ещё нет в базе — он «виртуальный», пока его не отметят (см. Recurrence).
     * Поэтому берём правило и день из Intent и проверяем: правило ещё есть, в этот день выпадает,
     * и день не стал задачей (его не отметили, не пропустили и не перенесли, пока ждали будильник).
     */
    private suspend fun onRuleDay(intent: Intent) {
        val ruleId = intent.getLongExtra(EXTRA_RULE_ID, -1)
        val epochDay = intent.getLongExtra(EXTRA_DATE, Long.MIN_VALUE)
        if (ruleId < 0 || epochDay == Long.MIN_VALUE) return
        val date = LocalDate.ofEpochDay(epochDay)
        val rule = rules.getRule(ruleId)
        val handled = tasks.getOccurrenceTasks(ruleId).any { it.occurrenceDate == date }
        val occurrence = rule?.let { Recurrence.occurrence(it, date) }?.takeIf { !handled }
        val fireAt = occurrence?.let { Reminders.fireAt(Recurrence.toTask(it, TaskStatus.OPEN)) }
        when (intent.action) {
            ACTION_REMIND_RULE -> {
                if (occurrence != null && fireAt != null && fireAt <= soon()) notifications.showRuleDay(
                    occurrence
                )
                // У повторения будильник один, на ближайший день. Этот сработал —
                // пусть ReminderSync поставит следующий (завтрашний, через неделю…)
                sync.syncNow()
            }

            ACTION_RULE_DONE -> {
                notifications.dismissRuleDay(ruleId)
                // Отметить день — как «таблеткой» или галочкой на «Сегодня»
                if (occurrence != null) commands.completeOccurrence(occurrence)
            }

            ACTION_RULE_SNOOZE -> {
                notifications.dismissRuleDay(ruleId)
                if (fireAt != null) sync.snoozeRuleDay(
                    ruleId,
                    date,
                    original = fireAt,
                    until = snoozeUntil()
                )
            }
        }
    }

    /**
     * Вечерняя сводка: что ждёт завтра (DayPreview — так же, как «Завтра: …» на «Сегодня»).
     * Показываем, только если сводка включена и завтра есть дела со временем: иначе готовиться не к чему.
     * Потом — syncNow: встанет будильник на следующий вечер.
     */
    private suspend fun onEveningSummary() {
        if (settings.settings.first().eveningSummary) {
            val tomorrow = LocalDate.now().plusDays(1)
            val preview = DayPreview.on(
                tomorrow,
                tasksOfDay = tasks.observeTasksBetween(tomorrow, tomorrow).first(),
                rules = rules.observeRules().first(),
                instances = tasks.observeOccurrenceTasks(tomorrow, tomorrow).first(),
            )
            if (preview.timed.isNotEmpty()) notifications.showEveningSummary(tomorrow, preview)
        }
        sync.syncNow()
    }

    /**
     * Приём лекарства. Как и день повторения, в базе его нет, пока его не отметили, — поэтому лекарство,
     * день и время берём из Intent и проверяем, что напоминать ещё есть о чём (MedicationReminders.stillDue):
     * лекарство не удалили, приём не отметили и не смахнули, время приёма не поменяли.
     */
    private suspend fun onDose(intent: Intent) {
        val medicationId = intent.getLongExtra(EXTRA_MEDICATION_ID, -1)
        val epochDay = intent.getLongExtra(EXTRA_DATE, Long.MIN_VALUE)
        val second = intent.getIntExtra(EXTRA_TIME, -1)
        if (medicationId < 0 || epochDay == Long.MIN_VALUE || second < 0) return
        val key = DoseKey(medicationId, LocalDate.ofEpochDay(epochDay), LocalTime.ofSecondOfDay(second.toLong()))
        val medication = medications.getMedication(medicationId)
        when (intent.action) {
            ACTION_REMIND_DOSE -> {
                val taken = medications.getTaken(medicationId, key.date)
                // Второе условие — приём не «из будущего» (как у задач: см. onTask)
                if (medication != null && key.at <= soon() &&
                    MedicationReminders.stillDue(medication, key, taken, sync.dismissedDoses())
                ) {
                    notifications.showDose(Dose(medication, key.date, key.time))
                }
                // Будильник у лекарства один: этот сработал — пусть встанет следующий (повтор или другой приём)
                sync.syncNow()
            }

            ACTION_DOSE_TAKEN -> {
                notifications.dismissDose(medicationId)
                // Лекарство могли удалить, пока висело уведомление, — тогда отмечать нечего
                if (medication != null) medications.setTaken(medicationId, key.date, key.time, taken = true)
            }

            ACTION_DOSE_SNOOZE -> {
                notifications.dismissDose(medicationId)
                if (medication != null) sync.snoozeDose(key, until = snoozeUntil())
            }

            ACTION_DOSE_DISMISSED -> sync.dismissDose(key)
        }
    }

    /** «Сейчас» с минутой запаса: будильник мог разбудить чуть раньше. */
    private fun soon(): LocalDateTime = LocalDateTime.now().plusMinutes(1)

    private fun snoozeUntil(): LocalDateTime = LocalDateTime.now().plusMinutes(SNOOZE_MINUTES)
}
