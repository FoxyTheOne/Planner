package app.planner.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.planner.core.domain.reminder.Reminders
import app.planner.core.domain.repository.ITaskRepository
import app.planner.reminders.ReminderReceiver.Companion.ACTION_DONE
import app.planner.reminders.ReminderReceiver.Companion.ACTION_REMIND
import app.planner.reminders.ReminderReceiver.Companion.ACTION_SNOOZE
import app.planner.reminders.ReminderReceiver.Companion.ACTION_TEST
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Приёмник всего, что связано с напоминанием одной задачи:
 * - [ACTION_REMIND] — будильник ([ReminderScheduler]): пора напомнить, показываем уведомление;
 * - [ACTION_DONE] — кнопка «Готово» в уведомлении: отмечаем задачу сделанной;
 * - [ACTION_SNOOZE] — кнопка «Отложить на 10 минут»: напомним ещё раз через 10 минут.
 * - [ACTION_TEST] — пробное напоминание со страницы «Надёжность напоминаний».
 *
 * BroadcastReceiver — маленький «приёмник» сигналов от системы. Android создаёт его на один
 * сигнал, вызывает [onReceive] и сразу о нём забывает. Работать долго ему нельзя: на всё около
 * 10 секунд. Нам нужно сходить в базу (это не мгновенно), поэтому goAsync(): «я ещё не закончил,
 * подожди» — и pending.finish(), когда всё сделано.
 *
 * Будильники после «Готово» снимать не нужно: задача перестала быть открытой, база сообщит
 * об этом [ReminderSync], и он сам уберёт её будильник — как при отметке на экране.
 *
 * `@AndroidEntryPoint` — Hilt сам заполнит поля с `@Inject` (репозиторий, уведомления), как во ViewModel.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_REMIND = "app.planner.action.REMIND"
        const val ACTION_DONE = "app.planner.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "app.planner.action.REMINDER_SNOOZE"
        const val ACTION_TEST = "app.planner.action.REMINDER_TEST"
        const val EXTRA_TASK_ID = "taskId"
        const val SNOOZE_MINUTES = 10L
    }

    @Inject
    lateinit var tasks: ITaskRepository

    @Inject
    lateinit var notifications: ReminderNotifications

    @Inject
    lateinit var sync: ReminderSync

    override fun onReceive(context: Context, intent: Intent) {
        // Пробное — без задачи и без базы: просто показать уведомление
        if (intent.action == ACTION_TEST) return notifications.showTest()
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        if (taskId < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Задачу берём свежую из базы: пока ждали будильник, её могли отметить или удалить.
                // fireAt == null — напоминать больше не о чем (отметили, убрали напоминание, удалили)
                val task = tasks.getTask(taskId)
                val fireAt = task?.let(Reminders::fireAt)
                when (intent.action) {
                    // Второе условие — напоминание не «из будущего». Так бывает, если задачу перенесли
                    // на потом, а приложение закрылось раньше, чем успело переставить будильник: старый
                    // будильник сработает в прежнее время. Тогда молчим — ReminderSync при запуске
                    // поставит новый. Минута запаса — на случай, если система разбудила чуть раньше
                    ACTION_REMIND -> if (task != null && fireAt != null && fireAt <= LocalDateTime.now()
                            .plusMinutes(1)
                    ) {
                        notifications.show(task)
                    }

                    ACTION_DONE -> {
                        notifications.dismiss(taskId)
                        if (fireAt != null) tasks.setDone(taskId, true)
                    }

                    ACTION_SNOOZE -> {
                        notifications.dismiss(taskId)
                        if (fireAt != null) {
                            sync.snooze(
                                taskId,
                                original = fireAt,
                                until = LocalDateTime.now().plusMinutes(SNOOZE_MINUTES)
                            )
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
