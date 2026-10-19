package app.planner.reminders

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import app.planner.core.domain.model.Task
import app.planner.core.domain.reminder.Reminders
import app.planner.core.domain.reminder.Snooze
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
    private val notifications: ReminderNotifications,
) {
    private companion object {
        const val TAG = "ReminderSync"
        const val KEY_SCHEDULED = "scheduled_task_ids"
        const val KEY_SNOOZES = "snoozes"
        const val MAX_ALARMS = 300
    }

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

    /**
     * «Отложить на 10 минут»: напомнить о задаче [taskId] снова в [until].
     *
     * Почему не поставить будильник прямо здесь: будильниками распоряжается только [apply],
     * и при первом же изменении в базе он снял бы «лишний» будильник — по задаче-то напоминание
     * уже прошло. Поэтому отложенное записываем туда же, где [apply] его увидит (в SharedPreferences),
     * а будильник он поставит сам. Заодно отложенное переживёт и перезагрузку телефона.
     *
     * @param original когда напоминание сработало по задаче — по нему [apply] поймёт,
     *   что задачу с тех пор не меняли (см. Reminders.alarmAt).
     */
    suspend fun snooze(taskId: Long, original: LocalDateTime, until: LocalDateTime) {
        mutex.withLock { saveSnoozes(loadSnoozes() + (taskId to Snooze(original, until))) }
        syncNow()
    }

    private suspend fun apply(withReminders: List<Task>) = mutex.withLock {
        val now = LocalDateTime.now()
        val snoozes = loadSnoozes()
        // Когда звенеть, решает домен; прошедшие не нужны. Берём ближайшие MAX_ALARMS:
        // у Android ограничение на число будильников одного приложения (около 500)
        val upcoming = Reminders.upcoming(withReminders, now, snoozes).take(MAX_ALARMS)
        val wanted = upcoming.map { it.first.id }.toSet()
        val previous =
            prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().map { it.toLong() }.toSet()

        // Было, а теперь не нужно (задачу удалили, отметили, убрали напоминание) — снимаем
        (previous - wanted).forEach(scheduler::cancel)
        // Ставим все нужные заново: время могло поменяться, а повторная постановка просто заменяет будильник
        upcoming.forEach { (task, at) -> scheduler.schedule(task.id, at) }

        // Отложенные храним, только пока они действуют: будильник стоит ровно на их время.
        // Прошедшие и те, чью задачу с тех пор поменяли или отметили, забываем
        val alarmById = upcoming.associate { (task, at) -> task.id to at }
        val stillSnoozed = snoozes.filter { (id, snooze) -> alarmById[id] == snooze.until }

        // edit { } — короткая запись: открыть правку, записать, сохранить (apply — в фоне, не тормозя)
        prefs.edit { putStringSet(KEY_SCHEDULED, wanted.map { it.toString() }.toSet()) }
        saveSnoozes(stillSnoozed)

        // Уведомления в шторке — только о том, о чём ещё есть смысл напоминать. Отметили задачу
        // в приложении, удалили, вернули в список — её уведомление уходит само
        notifications.dismissAllExcept(withReminders.map { it.id }.toSet())
    }

    /**
     * Отложенные напоминания из SharedPreferences. Там они строками «id|когда было|когда напомнить»,
     * например «12|2026-10-19T10:00|2026-10-19T10:10»: SharedPreferences умеет хранить набор строк,
     * а вот своих классов — нет. Испорченную строку пропускаем, а не падаем.
     */
    private fun loadSnoozes(): Map<Long, Snooze> =
        prefs.getStringSet(KEY_SNOOZES, emptySet()).orEmpty().mapNotNull { line ->
            runCatching {
                val (id, original, until) = line.split('|')
                id.toLong() to Snooze(LocalDateTime.parse(original), LocalDateTime.parse(until))
            }.getOrNull()
        }.toMap()

    private fun saveSnoozes(snoozes: Map<Long, Snooze>) {
        prefs.edit {
            putStringSet(
                KEY_SNOOZES,
                snoozes.map { (id, s) -> "$id|${s.original}|${s.until}" }.toSet()
            )
        }
    }
}
