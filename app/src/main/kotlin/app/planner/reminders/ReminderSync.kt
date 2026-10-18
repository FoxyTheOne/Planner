package app.planner.reminders

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import app.planner.core.domain.reminder.Reminders
import app.planner.core.domain.model.Task
import app.planner.core.domain.repository.ITaskRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Держит будильники в порядке: какие задачи с напоминаниями есть в базе — такие будильники и стоят.
 *
 * Идея та же, что у экранов: не «помнить, где что поменять», а подписаться на данные.
 * [start] подписывается на задачи с напоминаниями; что бы ни случилось — поставили напоминание
 * в редакторе, перенесли задачу смахиванием, отметили, удалили, восстановили бэкап — база пришлёт
 * новый список, и [apply] переставит будильники. Ни экранам, ни TaskCommands думать о будильниках не нужно.
 *
 * Что ещё нужно помнить — какие будильники мы ставили в прошлый раз. Иначе не узнать, что задачу
 * удалили: в новом списке её просто нет, а её будильник в системе остался. Номера задач с поставленными
 * будильниками храним в SharedPreferences — маленьком файле настроек: он переживает и перезапуск приложения.
 *
 * Живёт, пока жив процесс приложения (@Singleton, запускается в PlannerApplication).
 */
@Singleton
class ReminderSync @Inject constructor(
    @ApplicationContext context: Context,
    private val tasks: ITaskRepository,
    private val scheduler: ReminderScheduler,
) {
    // Своя «область» корутин на всё время жизни процесса. SupervisorJob — ошибка в одной корутине
    // не отменит остальные; Dispatchers.Default — работа не на главном потоке, экран не тормозит
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    // Mutex — «по одному»: если подписка и перезагрузка телефона одновременно захотят переставить
    // будильники, второй подождёт первого, а не будет менять будильники параллельно с ним
    private val mutex = Mutex()

    /** Подписаться на задачи с напоминаниями. Вызывается один раз, при запуске приложения. */
    fun start() {
        scope.launch {
            tasks.observeTasksWithReminders()
                // Напоминания — фоновая работа: если с ними что-то пошло не так (испорченная запись
                // в базе, отказ системы), не роняем из-за этого всё приложение — оно падало бы
                // при каждом запуске. Пишем ошибку в журнал (Logcat), а приложение продолжает работать
                .catch { Log.e(TAG, "Не удалось переставить будильники напоминаний", it) }
                .collect { apply(it) }
        }
    }

    /**
     * Переставить будильники прямо сейчас — после перезагрузки телефона или смены времени.
     * Данные при этом не менялись, поэтому подписка сама не сработала бы.
     */
    suspend fun syncNow() = apply(tasks.observeTasksWithReminders().first())

    private suspend fun apply(withReminders: List<Task>) = mutex.withLock {
        // Когда звенеть, решает домен; прошедшие не нужны. Берём ближайшие MAX_ALARMS:
        // у Android ограничение на число будильников одного приложения (около 500)
        val upcoming = Reminders.upcoming(withReminders, LocalDateTime.now()).take(MAX_ALARMS)
        val wanted = upcoming.map { it.first.id }.toSet()
        val previous = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().map { it.toLong() }.toSet()

        // Было, а теперь не нужно (задачу удалили, отметили, убрали напоминание) — снимаем
        (previous - wanted).forEach(scheduler::cancel)
        // Ставим все нужные заново: время могло поменяться, а повторная постановка просто заменяет будильник
        upcoming.forEach { (task, at) -> scheduler.schedule(task.id, at) }

        // edit { } — короткая запись: открыть правку, записать, сохранить (apply — в фоне, не тормозя)
        prefs.edit { putStringSet(KEY_SCHEDULED, wanted.map { it.toString() }.toSet()) }
    }

    private companion object {
        const val TAG = "ReminderSync"
        const val KEY_SCHEDULED = "scheduled_task_ids"
        const val MAX_ALARMS = 300
    }
}
