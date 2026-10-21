package app.planner.core.domain.medication

import app.planner.core.domain.model.DoseTaken
import app.planner.core.domain.model.Medication
import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.recurrence.Recurrence
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Один приём: лекарство [medication] в день [date] в [time]. Строка в блоке «лекарства» на «Сегодня».
 *
 * @param taken отметка «принято»; null — ещё не приняли (или пропустили — это одно и то же).
 */
data class Dose(
    val medication: Medication,
    val date: LocalDate,
    val time: LocalTime,
    val taken: DoseTaken? = null,
) {
    val isTaken: Boolean get() = taken != null
    val at: LocalDateTime get() = date.atTime(time)
}

/**
 * Как принимали лекарство последние дни: «За 30 дней — 28 из 30».
 *
 * Здесь, в отличие от привычек, есть «из»: для лекарства важно видеть, сколько приёмов пропущено.
 * Но без процентов, серий и красного цвета — просто два числа.
 *
 * @param from первый день периода: 30 дней назад, но не раньше начала курса.
 * @param to последний день — сегодня.
 * @param planned сколько приёмов было по расписанию. Сегодняшние ещё не наступившие не считаются:
 *   вечерний приём в обед ещё не «пропущен». Но если его уже отметили заранее — считается.
 * @param taken сколько из них отмечены «принято».
 */
data class MedicationHistory(
    val from: LocalDate,
    val to: LocalDate,
    val planned: Int,
    val taken: Int,
) {
    /** Курс начался внутри периода — «С 10 октября — 8 из 9», а не «За 30 дней». */
    val startedRecently: Boolean get() = from > to.minusDays(DAYS - 1L)

    companion object {
        /** За сколько дней показываем историю — столько же, сколько у привычек. */
        const val DAYS = 30
    }
}

/**
 * Расчёты для лекарств: в какие дни и во сколько приёмы ([doses], [dosesOn]), сколько осталось
 * курса ([daysLeft]), как принимали последние дни ([history]), паузы ([pause], [resume]).
 *
 * Расписание — то же, что у повторений: дни считает Recurrence. Чтобы не писать второй такой же
 * движок, лекарство на время расчёта «превращается» в правило повторения ([asRule]) — с теми же
 * днями, курсом и паузами. Всё остальное (доза, еда, несколько приёмов в день) — здесь.
 *
 * Чистая логика, без Android и базы; проверяется тестами (MedicationsTest).
 */
object Medications {

    /** Принимают ли [medication] в [date]: день по расписанию, внутри курса и не на паузе. */
    fun takesOn(medication: Medication, date: LocalDate): Boolean =
        Recurrence.occursOn(asRule(medication), date)

    /** Приёмы лекарства в [date] по порядку времени, с отметками из [taken]. В «не его» день — пусто. */
    fun doses(medication: Medication, date: LocalDate, taken: List<DoseTaken>): List<Dose> {
        if (!takesOn(medication, date)) return emptyList()
        return medication.doseTimes.map { time ->
            Dose(
                medication,
                date,
                time,
                taken.firstOrNull { it.medicationId == medication.id && it.date == date && it.time == time },
            )
        }
    }

    /**
     * Все приёмы дня по всем лекарствам — для блока «лекарства» на «Сегодня»: по времени,
     * а в одно время — в порядке списка лекарств (как их расставили).
     */
    fun dosesOn(medications: List<Medication>, date: LocalDate, taken: List<DoseTaken>): List<Dose> =
        medications.flatMap { doses(it, date, taken) }
            .sortedWith(compareBy({ it.time }, { it.medication.position }, { it.medication.id }))

    /**
     * Сколько дней курса осталось, считая [today]: «ещё 5 дней». null — курс бессрочный.
     * 0 — курс закончился. До начала курса — вся его длина.
     */
    fun daysLeft(medication: Medication, today: LocalDate): Int? {
        val end = medication.endDate ?: return null
        val from = maxOf(today, medication.startDate)
        return if (end < from) 0 else ChronoUnit.DAYS.between(from, end).toInt() + 1
    }

    /** Курс закончился: последний день уже позади. Такие лекарства уходят в «закончились». */
    fun isFinished(medication: Medication, today: LocalDate): Boolean =
        medication.endDate?.let { it < today } ?: false

    /** Ещё не начали: курс начнётся позже. */
    fun isUpcoming(medication: Medication, today: LocalDate): Boolean = medication.startDate > today

    /** «Курс 10 дней» с [start] → последний день курса: 10 дней — это start и ещё 9. */
    fun courseEnd(start: LocalDate, days: Int): LocalDate {
        require(days >= 1) { "Курс — хотя бы один день" }
        return start.plusDays(days - 1L)
    }

    /** Пауза, которая идёт в [date], или null. */
    fun activePause(medication: Medication, date: LocalDate): Pause? =
        medication.pauses.firstOrNull { date in it }

    /** Перерыв с [today] по [until] включительно; until = null — пока не нажмут «Продолжить». */
    fun pause(medication: Medication, today: LocalDate, until: LocalDate?): Medication =
        medication.copy(pauses = medication.pauses + Pause(today, until))

    /** «Продолжить» — снять перерыв, который идёт сейчас (так же, как у повторений). */
    fun resume(medication: Medication, today: LocalDate): Medication =
        medication.copy(pauses = Recurrence.resumed(medication.pauses, today))

    /**
     * Как принимали [medication] за последние [MedicationHistory.DAYS] дней по сегодня ([now]) включительно.
     *
     * Считаем приёмы по расписанию (дни паузы не считаются — их и не было) и сколько из них отмечено.
     * Сегодняшний приём, время которого ещё не пришло, в счёт не идёт, если его не отметили заранее.
     */
    fun history(medication: Medication, taken: List<DoseTaken>, now: LocalDateTime): MedicationHistory {
        val today = now.toLocalDate()
        val from = maxOf(today.minusDays(MedicationHistory.DAYS - 1L), medication.startDate)
        val doses = generateSequence(from) { it.plusDays(1) }
            .takeWhile { it <= today }
            .flatMap { doses(medication, it, taken) }
            .filter { it.at <= now || it.isTaken }
            .toList()
        return MedicationHistory(
            from = from,
            to = today,
            planned = doses.size,
            taken = doses.count { it.isTaken },
        )
    }

    /**
     * Лекарство как правило повторения — только чтобы спросить у Recurrence, выпадает ли день.
     * Названия, напоминания и прочего движку не нужно: дни зависят от расписания, курса и пауз.
     */
    private fun asRule(medication: Medication) = RecurrenceRule(
        id = medication.id,
        title = medication.name,
        repeat = medication.repeat,
        startDate = medication.startDate,
        endDate = medication.endDate,
        pauses = medication.pauses,
    )
}
