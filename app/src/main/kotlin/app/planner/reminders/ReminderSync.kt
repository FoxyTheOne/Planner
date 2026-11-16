package app.planner.reminders

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import app.planner.core.domain.medication.DoseKey
import app.planner.core.domain.model.DoseTaken
import app.planner.core.domain.model.Medication
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Task
import app.planner.core.domain.reminder.MedicationReminders
import app.planner.core.domain.reminder.Reminders
import app.planner.core.domain.reminder.Snooze
import app.planner.core.domain.repository.IMedicationRepository
import app.planner.core.domain.repository.IRecurrenceRepository
import app.planner.core.domain.repository.ITaskRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Держит будильники в порядке: какие напоминания есть в базе — такие будильники и стоят.
 *
 * Идея та же, что у экранов: не «помнить, где что поменять», а подписаться на данные.
 * [start] подписывается на задачи с напоминаниями, на повторения и на лекарства; что бы ни случилось — поставили
 * напоминание в редакторе, перенесли задачу смахиванием, отметили, удалили, восстановили бэкап — база
 * пришлёт новые данные, и [apply] переставит будильники. Ни экранам, ни TaskCommands думать о будильниках не нужно.
 *
 * Будильники бывают трёх видов:
 * - у задачи — один, на её напоминание;
 * - у повторения (и привычки) — тоже один, на ближайший день (Reminders.alarmForRule). Когда он сработает,
 *   ReminderReceiver зовёт [syncNow], и встаёт следующий — завтрашний, через неделю…
 * - у лекарства — один, на ближайший звонок о приёме (MedicationReminders.alarmFor): само время приёма,
 *   повтор через 10 минут или «Отложить». Сработал — тоже [syncNow], и встаёт следующий.
 *
 * Что ещё нужно помнить — какие будильники мы ставили в прошлый раз. Иначе не узнать, что задачу
 * удалили: в новом списке её просто нет, а её будильник в системе остался. Номера задач, повторений и лекарств
 * с поставленными будильниками (а ещё отложенные и смахнутые напоминания) храним в SharedPreferences —
 * маленьком файле настроек: он переживает и перезапуск приложения.
 *
 * Живёт, пока жив процесс приложения (@Singleton, запускается в PlannerApplication).
 */
@Singleton
class ReminderSync @Inject constructor(
    @ApplicationContext context: Context,
    private val tasks: ITaskRepository,
    private val rules: IRecurrenceRepository,
    private val medications: IMedicationRepository,
    private val scheduler: ReminderScheduler,
    private val notifications: ReminderNotifications,
) {
    private companion object {
        const val TAG = "ReminderSync"
        const val KEY_SCHEDULED = "scheduled_task_ids"
        const val KEY_SCHEDULED_RULES = "scheduled_rule_ids"
        const val KEY_SNOOZES = "snoozes"
        const val KEY_RULE_SNOOZES = "rule_snoozes"
        const val KEY_SCHEDULED_MEDICATIONS = "scheduled_medication_ids"
        const val KEY_DOSE_SNOOZES = "dose_snoozes"
        const val KEY_DISMISSED_DOSES = "dismissed_doses"
        const val MAX_ALARMS = 300
    }

    /**
     * Всё, что нужно для будильников: задачи с напоминаниями, правила и уже отмеченные дни правил,
     * лекарства и отметки «принято».
     */
    private data class Inputs(
        val tasks: List<Task>,
        val rules: List<RecurrenceRule>,
        val instances: List<Task>,
        val medications: List<Medication>,
        val taken: List<DoseTaken>,
    )

    // Своя «область» корутин на всё время жизни процесса. SupervisorJob — ошибка в одной корутине
    // не отменит остальные; Dispatchers.Default — работа не на главном потоке, экран не тормозит
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    // Mutex — «по одному»: если подписка и перезагрузка телефона одновременно захотят переставить
    // будильники, второй подождёт первого, а не будет менять будильники параллельно с ним
    private val mutex = Mutex()

    /** Подписаться на напоминания. Вызывается один раз, при запуске приложения. */
    fun start() {
        scope.launch {
            inputs()
                // Напоминания — фоновая работа: если с ними что-то пошло не так (испорченная запись
                // в базе, отказ системы), не роняем из-за этого всё приложение — оно падало бы
                // при каждом запуске. Пишем ошибку в журнал (Logcat), а приложение продолжает работать
                .catch { Log.e(TAG, "Не удалось переставить будильники напоминаний", it) }
                .collect { apply(it) }
        }
    }

    /**
     * Переставить будильники прямо сейчас — после перезагрузки телефона, смены времени или
     * когда сработал будильник повторения. Данные при этом не менялись, поэтому подписка сама не сработала бы.
     */
    suspend fun syncNow() = apply(inputs().first())

    /**
     * Пять потоков в один. Отмеченные дни правил и отметки «принято» берём со вчера и на год с запасом вперёд —
     * столько, сколько Reminders.nextForRule смотрит вперёд.
     */
    private fun inputs(): Flow<Inputs> {
        val today = LocalDate.now()
        val until = today.plusDays(Reminders.LOOKAHEAD_DAYS.toLong())
        return combine(
            tasks.observeTasksWithReminders(),
            rules.observeRules(),
            tasks.observeOccurrenceTasks(today.minusDays(1), until),
            medications.observeMedications(),
            medications.observeTaken(today.minusDays(1), until),
            ::Inputs,
        )
    }

    /**
     * «Отложить на 10 минут» у приёма [key]: напомнить снова в [until] (и, с повтором, ещё трижды
     * после этого). Записываем, как отложенное задачи, — будильник поставит [apply].
     */
    suspend fun snoozeDose(key: DoseKey, until: LocalDateTime) {
        mutex.withLock {
            saveSnoozes(
                KEY_DOSE_SNOOZES,
                loadSnoozes(KEY_DOSE_SNOOZES) + (key.medicationId to Snooze(key.at, until, key.date)),
            )
        }
        syncNow()
    }

    /** Уведомление о приёме [key] смахнули — человек его видел: повторов больше не будет. */
    suspend fun dismissDose(key: DoseKey) {
        mutex.withLock { saveDismissed(loadDismissed() + key) }
        syncNow()
    }

    /** Смахнутые приёмы — для проверки в ReminderReceiver, когда сработал будильник. */
    fun dismissedDoses(): Set<DoseKey> = loadDismissed()

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
        mutex.withLock {
            saveSnoozes(
                KEY_SNOOZES,
                loadSnoozes(KEY_SNOOZES) + (taskId to Snooze(original, until))
            )
        }
        syncNow()
    }

    /** То же для дня [date] повторения [ruleId] (см. Reminders.alarmForRule). */
    suspend fun snoozeRuleDay(
        ruleId: Long,
        date: LocalDate,
        original: LocalDateTime,
        until: LocalDateTime
    ) {
        mutex.withLock {
            saveSnoozes(
                KEY_RULE_SNOOZES,
                loadSnoozes(KEY_RULE_SNOOZES) + (ruleId to Snooze(original, until, date))
            )
        }
        syncNow()
    }

    private suspend fun apply(inputs: Inputs) = mutex.withLock {
        val now = LocalDateTime.now()

        // --- Задачи ---
        val snoozes = loadSnoozes(KEY_SNOOZES)
        // Когда звенеть, решает домен; прошедшие не нужны. Берём ближайшие MAX_ALARMS:
        // у Android ограничение на число будильников одного приложения (около 500)
        val upcoming = Reminders.upcoming(inputs.tasks, now, snoozes).take(MAX_ALARMS)
        val wanted = upcoming.map { it.first.id }.toSet()
        // Было, а теперь не нужно (задачу удалили, отметили, убрали напоминание) — снимаем
        (loadIds(KEY_SCHEDULED) - wanted).forEach(scheduler::cancel)
        // Ставим все нужные заново: время могло поменяться, а повторная постановка просто заменяет будильник
        upcoming.forEach { (task, at) -> scheduler.schedule(task.id, at) }
        // Отложенные храним, только пока они действуют: будильник стоит ровно на их время.
        // Прошедшие и те, чью задачу с тех пор поменяли или отметили, забываем
        val alarmById = upcoming.associate { (task, at) -> task.id to at }
        saveSnoozes(KEY_SNOOZES, snoozes.filter { (id, snooze) -> alarmById[id] == snooze.until })
        saveIds(KEY_SCHEDULED, wanted)

        // --- Повторения и привычки: по будильнику на правило, на ближайший день ---
        val ruleSnoozes = loadSnoozes(KEY_RULE_SNOOZES)
        val withReminder = inputs.rules.filter { it.reminder != null }
        // Словарь «id правила → (день, момент)»: mapNotNull выбрасывает тех, кому звонить не нужно,
        // toMap превращает пары (id, будильник) в словарь
        val ruleAlarms = withReminder
            .mapNotNull { rule ->
                Reminders.alarmForRule(
                    rule,
                    inputs.instances,
                    now,
                    ruleSnoozes[rule.id]
                )?.let { rule.id to it }
            }
            .toMap()
        (loadIds(KEY_SCHEDULED_RULES) - ruleAlarms.keys).forEach(scheduler::cancelRule)
        ruleAlarms.forEach { (ruleId, alarm) ->
            scheduler.scheduleRule(
                ruleId,
                alarm.first,
                alarm.second
            )
        }
        saveSnoozes(
            KEY_RULE_SNOOZES,
            ruleSnoozes.filter { (id, snooze) -> ruleAlarms[id]?.second == snooze.until })
        saveIds(KEY_SCHEDULED_RULES, ruleAlarms.keys)

        // --- Лекарства: по будильнику на лекарство, на ближайший звонок о приёме ---
        val doseSnoozes = loadSnoozes(KEY_DOSE_SNOOZES)
        val dismissed = loadDismissed()
        val medicationAlarms = inputs.medications
            .mapNotNull { medication ->
                MedicationReminders.alarmFor(medication, inputs.taken, now, doseSnoozes[medication.id], dismissed)
                    ?.let { medication.id to it }
            }
            .toMap()
        (loadIds(KEY_SCHEDULED_MEDICATIONS) - medicationAlarms.keys).forEach(scheduler::cancelMedication)
        medicationAlarms.forEach { (medicationId, alarm) ->
            val (dose, at) = alarm
            scheduler.scheduleMedication(medicationId, dose.date, dose.time, at)
        }
        saveIds(KEY_SCHEDULED_MEDICATIONS, medicationAlarms.keys)
        // Отложенное помним, пока впереди его последний звонок (с повторами); лекарство удалили — забываем
        val medicationsById = inputs.medications.associateBy { it.id }
        saveSnoozes(
            KEY_DOSE_SNOOZES,
            doseSnoozes.filter { (id, snooze) ->
                medicationsById[id]?.let { MedicationReminders.snoozeAlive(it, snooze, now) } == true
            },
        )
        // Смахнутые — только со вчерашнего дня: более старые приёмы и так не звонят
        saveDismissed(dismissed.filter { it.date >= now.toLocalDate().minusDays(1) }.toSet())

        // Уведомления в шторке — только о том, о чём ещё есть смысл напоминать. Отметили задачу
        // или день повторения в приложении, удалили, вернули в список — его уведомление уходит само.
        // Приём лекарства — так же: отметили «принято» на экране, удалили лекарство, выключили напоминания
        val ruleIdsWithReminder = withReminder.map { it.id }.toSet()
        val handledDays =
            inputs.instances.mapNotNull { day -> day.ruleId?.let { it to day.occurrenceDate } }
                .toSet()
        notifications.dismissAllExcept(
            keepTaskIds = inputs.tasks.map { it.id }.toSet(),
            keepRuleDay = { ruleId, date -> ruleId in ruleIdsWithReminder && (ruleId to date) !in handledDays },
            keepDose = { key ->
                MedicationReminders.stillDue(medicationsById[key.medicationId], key, inputs.taken, dismissed)
            },
        )
    }

    /**
     * Смахнутые приёмы: строки «id лекарства|день|время», например «3|2026-10-19|09:00».
     * Испорченную строку пропускаем.
     */
    private fun loadDismissed(): Set<DoseKey> =
        prefs.getStringSet(KEY_DISMISSED_DOSES, emptySet()).orEmpty().mapNotNull { line ->
            runCatching {
                val (id, date, time) = line.split('|')
                DoseKey(id.toLong(), LocalDate.parse(date), LocalTime.parse(time))
            }.getOrNull()
        }.toSet()

    private fun saveDismissed(keys: Set<DoseKey>) {
        prefs.edit {
            putStringSet(KEY_DISMISSED_DOSES, keys.map { "${it.medicationId}|${it.date}|${it.time}" }.toSet())
        }
    }

    private fun loadIds(key: String): Set<Long> =
        prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    // edit { } — короткая запись: открыть правку, записать, сохранить (apply — в фоне, не тормозя)
    private fun saveIds(key: String, ids: Set<Long>) {
        prefs.edit { putStringSet(key, ids.map { it.toString() }.toSet()) }
    }

    /**
     * Отложенные напоминания из SharedPreferences. Там они строками «id|когда было|когда напомнить»,
     * например «12|2026-10-19T10:00|2026-10-19T10:10», а у повторений ещё и день в конце:
     * «5|2026-10-19T10:00|2026-10-19T10:10|2026-10-19». SharedPreferences умеет хранить набор строк,
     * а вот своих классов — нет. Испорченную строку пропускаем, а не падаем.
     */
    private fun loadSnoozes(key: String): Map<Long, Snooze> =
        prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull { line ->
            runCatching {
                val parts = line.split('|')
                parts[0].toLong() to Snooze(
                    original = LocalDateTime.parse(parts[1]),
                    until = LocalDateTime.parse(parts[2]),
                    date = parts.getOrNull(3)?.let(LocalDate::parse),
                )
            }.getOrNull()
        }.toMap()

    private fun saveSnoozes(key: String, snoozes: Map<Long, Snooze>) {
        prefs.edit {
            putStringSet(
                key,
                snoozes.map { (id, s) ->
                    listOfNotNull(
                        id,
                        s.original,
                        s.until,
                        s.date
                    ).joinToString("|")
                }.toSet(),
            )
        }
    }
}
