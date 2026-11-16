package app.planner.common

import app.planner.core.domain.model.Repeat
import app.planner.core.domain.recurrence.Recurrence
import app.planner.core.domain.repository.IRecurrenceRepository
import app.planner.core.domain.repository.ITaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Создаёт задачи «следующий раз» для повторений «после выполнения» (УЗИ раз в год от последнего).
 * Нужна ли задача и на какой день — решает домен (Recurrence.afterDoneTaskDue), здесь только запись в базу.
 *
 * Зовут:
 * - TaskCommands — сразу после сохранения такого повторения (первый раз) и после того, как задачу
 *   отметили сделанной или пропустили (следующий раз);
 * - ReminderReceiver — после «Готово» в уведомлении;
 * - [DailyMaintenance] — раз в день, на всякий случай: например, если задачу отметили, когда пауза
 *   ещё шла, а теперь она закончилась.
 *
 * Mutex — как у [PrepTasks]: два вызова одновременно не создадут две одинаковые задачи.
 */
@Singleton
class AfterDoneTasks @Inject constructor(
    private val tasks: ITaskRepository,
    private val rules: IRecurrenceRepository,
) {
    private val mutex = Mutex()

    suspend fun create() = mutex.withLock {
        rules.observeRules().first()
            .filter { it.repeat is Repeat.AfterDone }
            .forEach { rule ->
                Recurrence.afterDoneTaskDue(rule, tasks.getAfterDoneTasks(rule.id))?.let { tasks.addOrUpdate(it) }
            }
    }
}
