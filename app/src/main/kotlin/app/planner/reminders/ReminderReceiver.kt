package app.planner.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.planner.core.domain.reminder.Reminders
import app.planner.core.domain.repository.ITaskRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Его будит будильник ([ReminderScheduler]): пора напомнить о задаче.
 *
 * BroadcastReceiver — маленький «приёмник» сигналов от системы. Android создаёт его на один
 * сигнал, вызывает [onReceive] и сразу о нём забывает. Работать долго ему нельзя: на всё около
 * 10 секунд. Нам нужно сходить в базу (это не мгновенно), поэтому goAsync(): «я ещё не закончил,
 * подожди» — и pending.finish(), когда всё сделано.
 *
 * `@AndroidEntryPoint` — Hilt сам заполнит поля с `@Inject` (репозиторий, уведомления), как во ViewModel.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var tasks: ITaskRepository
    @Inject lateinit var notifications: ReminderNotifications

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        if (taskId < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Задачу берём свежую из базы: пока ждали будильник, её могли отметить или удалить.
                // Напоминаем, только если ей по-прежнему есть о чём напомнить
                val task = tasks.getTask(taskId)
                if (task != null && Reminders.fireAt(task) != null) notifications.show(task)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "app.planner.action.REMIND"
        const val EXTRA_TASK_ID = "taskId"
    }
}
