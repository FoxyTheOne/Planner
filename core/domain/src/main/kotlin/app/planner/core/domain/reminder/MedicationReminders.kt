package app.planner.core.domain.reminder

import app.planner.core.domain.medication.Dose
import app.planner.core.domain.medication.DoseKey
import app.planner.core.domain.medication.Medications
import app.planner.core.domain.model.DoseTaken
import app.planner.core.domain.model.Medication
import java.time.LocalDateTime

/**
 * Когда напоминать о приёмах лекарства. Как [Reminders] для задач и повторений: чистая логика,
 * без Android, — будильник ставит модуль app (ReminderSync), а здесь ответ на «в какой момент».
 * Проверяется тестами (MedicationRemindersTest).
 *
 * КАК ЗВОНИТ ПРИЁМ
 * - ровно во время приёма (9:00): «за N минут» у таблеток не нужно — их пьют по часам;
 * - если включён повтор ([Medication.repeatReminder]) и на уведомление не ответили —
 *   ещё через 10, 20 и 30 минут. Не больше трёх раз: напомнить, но не навязывать;
 * - «Отложить» — снова через 10 минут; с этого момента повторы отсчитываются заново;
 * - смахнули уведомление — человек его видел: о приёме больше не звоним ([dismissed]);
 * - отметили «Принято» (в уведомлении или на экране) — тоже.
 *
 * У лекарства, как у повторения, будильник один — на ближайший звонок ([alarmFor]).
 * Сработал — ReminderSync поставит следующий.
 */
object MedicationReminders {

    /** Через сколько минут повторить напоминание, на которое не ответили. */
    const val REPEAT_EVERY_MINUTES = 10L

    /** Сколько раз повторить (кроме самого первого звонка). */
    const val REPEATS = 3

    /**
     * Когда звонить о приёме, если считать от [base]: [base] и, с повтором, ещё +10, +20, +30 минут.
     * [base] — время приёма или, если его отложили, время «Отложить».
     */
    fun ringTimes(medication: Medication, base: LocalDateTime): List<LocalDateTime> {
        val repeats = if (medication.repeatReminder) REPEATS else 0
        return (0..repeats).map { base.plusMinutes(it * REPEAT_EVERY_MINUTES) }
    }

    /**
     * Ближайший звонок о приёмах [medication] после [now]: какой приём и когда звонить.
     * null — звонить не о чем: напоминания выключены, курс закончился, все приёмы приняты или смахнуты.
     *
     * Смотрим со вчерашнего дня: повтор вечернего приёма в 23:50 приходится уже на после полуночи.
     * Приёмы идут по порядку времени, а звонок не бывает раньше самого приёма. Поэтому, как только
     * время следующего приёма позже лучшего найденного звонка, дальше можно не искать.
     *
     * @param snooze отложенное напоминание этого лекарства: какой приём ([Snooze.date] + [Snooze.original])
     *   и до какого момента ([Snooze.until]).
     * @param dismissed приёмы, чьи уведомления смахнули.
     */
    fun alarmFor(
        medication: Medication,
        taken: List<DoseTaken>,
        now: LocalDateTime,
        snooze: Snooze? = null,
        dismissed: Set<DoseKey> = emptySet(),
    ): Pair<Dose, LocalDateTime>? {
        if (!medication.remind) return null
        var best: Pair<Dose, LocalDateTime>? = null
        val days = generateSequence(now.toLocalDate().minusDays(1)) { it.plusDays(1) }
            .take(Reminders.LOOKAHEAD_DAYS + 1)
        for (day in days) {
            for (dose in Medications.doses(medication, day, taken)) {
                val found = best
                if (found != null && dose.at > found.second) return found
                if (dose.isTaken || dose.key in dismissed) continue
                val ring = ringTimes(medication, baseOf(dose, snooze)).firstOrNull { it > now } ?: continue
                if (found == null || ring < found.second) best = dose to ring
            }
        }
        return best
    }

    /**
     * Стоит ли ещё напоминать о приёме [key] — проверка в момент, когда сработал будильник:
     * пока он ждал, лекарство могли поменять, удалить, отметить приём или смахнуть уведомление.
     */
    fun stillDue(
        medication: Medication?,
        key: DoseKey,
        taken: List<DoseTaken>,
        dismissed: Set<DoseKey>,
    ): Boolean {
        if (medication == null || !medication.remind || key in dismissed) return false
        val dose = Medications.doses(medication, key.date, taken).firstOrNull { it.time == key.time }
        return dose != null && !dose.isTaken
    }

    /**
     * Действует ли ещё отложенное: его последний звонок (с повторами) впереди.
     * Прошедшие отложенные ReminderSync забывает.
     */
    fun snoozeAlive(medication: Medication, snooze: Snooze, now: LocalDateTime): Boolean =
        ringTimes(medication, snooze.until).last() > now

    /** От чего считать звонки приёма: от «Отложить», если отложили именно его, иначе — от времени приёма. */
    private fun baseOf(dose: Dose, snooze: Snooze?): LocalDateTime =
        snooze?.takeIf { it.date == dose.date && it.original == dose.at }?.until ?: dose.at
}
