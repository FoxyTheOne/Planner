package app.planner.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.planner.common.TaskCommands
import app.planner.core.domain.model.TaskStatus
import app.planner.core.domain.recurrence.Recurrence
import app.planner.core.domain.reminder.Reminders
import app.planner.core.domain.repository.IRecurrenceRepository
import app.planner.core.domain.repository.ITaskRepository
import app.planner.reminders.ReminderReceiver.Companion.ACTION_DONE
import app.planner.reminders.ReminderReceiver.Companion.ACTION_REMIND
import app.planner.reminders.ReminderReceiver.Companion.ACTION_REMIND_RULE
import app.planner.reminders.ReminderReceiver.Companion.ACTION_RULE_DONE
import app.planner.reminders.ReminderReceiver.Companion.ACTION_RULE_SNOOZE
import app.planner.reminders.ReminderReceiver.Companion.ACTION_SNOOZE
import app.planner.reminders.ReminderReceiver.Companion.ACTION_TEST
import app.planner.reminders.ReminderReceiver.Companion.EXTRA_DATE
import app.planner.reminders.ReminderReceiver.Companion.EXTRA_RULE_ID
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
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
 * И [ACTION_TEST] — пробное напоминание со страницы «Надёжность напоминаний».
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
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_RULE_ID = "ruleId"

        /** День повторения — числом дней от 1970 года (LocalDate.toEpochDay). */
        const val EXTRA_DATE = "date"
        const val SNOOZE_MINUTES = 10L
    }

    @Inject
    lateinit var tasks: ITaskRepository

    @Inject
    lateinit var rules: IRecurrenceRepository

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
                    ACTION_REMIND, ACTION_DONE, ACTION_SNOOZE -> onTask(intent)
                    ACTION_REMIND_RULE, ACTION_RULE_DONE, ACTION_RULE_SNOOZE -> onRuleDay(intent)
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
                if (fireAt != null) tasks.setDone(taskId, true)
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

    /** «Сейчас» с минутой запаса: будильник мог разбудить чуть раньше. */
    private fun soon(): LocalDateTime = LocalDateTime.now().plusMinutes(1)

    private fun snoozeUntil(): LocalDateTime = LocalDateTime.now().plusMinutes(SNOOZE_MINUTES)
}
