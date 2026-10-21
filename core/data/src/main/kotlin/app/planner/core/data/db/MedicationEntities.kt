package app.planner.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.planner.core.domain.model.DoseTaken
import app.planner.core.domain.model.FoodRelation
import app.planner.core.domain.model.Medication
import app.planner.core.domain.model.Repeat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Строка таблицы `medications` — лекарство в базе (см. доменную модель Medication).
 *
 * Расписание раскладывается по колонкам так же, как у правил повторения (RecurrenceRuleEntity):
 * [repeatType] — вариант, а [weekdays] и [everyNDays] — его данные. Названия вариантов и паузы
 * в JSON — общие с правилами (TYPE_DAILY…, pausesToJson): одни и те же данные хранятся одинаково.
 *
 * @param food BEFORE / WITH / AFTER или null — «неважно».
 * @param times времена приёмов: секунды от полуночи через запятую, «32400,75600» — это 9:00 и 21:00.
 *   Список внутри строки, а не отдельная таблица: по временам мы никогда не ищем.
 */
@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dose: String,
    val food: String?,
    val times: String,
    val repeatType: String,
    /** Дни недели битами — как у правил повторения (пн = 1, вт = 2, ср = 4…). */
    val weekdays: Int?,
    val everyNDays: Int?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    /** JSON, как у правил: `[{"from":20738,"until":20744}]`. */
    val pauses: String,
    val remind: Boolean,
    val repeatReminder: Boolean,
    val note: String,
    val position: Int,
)

/**
 * Строка таблицы `doses_taken` — отметка «принято» (см. доменную DoseTaken).
 *
 * Своего id нет: приём и так однозначно узнаётся по тройке «лекарство + день + время» —
 * это и есть первичный ключ (primaryKeys). Заодно база сама не даст отметить один приём дважды.
 *
 * Удалили лекарство — его отметки удаляются вместе с ним (CASCADE): без лекарства они ни к чему.
 * Индекс по [date] — экран «Сегодня» и напоминания спрашивают отметки за конкретные дни.
 * Отдельный индекс по medicationId не нужен: он и так первый в первичном ключе.
 */
@Entity(
    tableName = "doses_taken",
    primaryKeys = ["medicationId", "date", "time"],
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("date")],
)
data class DoseTakenEntity(
    val medicationId: Long,
    val date: LocalDate,
    val time: LocalTime,
    val takenAt: Instant,
)

fun Medication.toEntity(): MedicationEntity {
    // Локальная переменная — для smart cast (свойство класса из другого модуля, см. RecurrenceRule.toEntity)
    val r = repeat
    return MedicationEntity(
        id = id,
        name = name,
        dose = dose,
        food = food?.name,
        times = doseTimes.joinToString(",") { it.toSecondOfDay().toString() },
        repeatType = when (r) {
            Repeat.Daily -> TYPE_DAILY
            is Repeat.OnWeekdays -> TYPE_WEEKDAYS
            is Repeat.EveryNDays -> TYPE_EVERY_N_DAYS
            // Таких у лекарства не бывает — их не пропустит проверка в самом Medication
            is Repeat.Yearly, is Repeat.Cycle -> error("Лекарство не повторяется так: $r")
        },
        weekdays = (r as? Repeat.OnWeekdays)?.let { daysToBits(it.days) },
        everyNDays = (r as? Repeat.EveryNDays)?.n,
        startDate = startDate,
        endDate = endDate,
        pauses = pausesToJson(pauses),
        remind = remind,
        repeatReminder = repeatReminder,
        note = note,
        position = position,
    )
}

fun MedicationEntity.toDomain() = Medication(
    id = id,
    name = name,
    dose = dose,
    // Неизвестное значение (например, после отката версии) — «неважно», а не падение
    food = food?.let { runCatching { FoodRelation.valueOf(it) }.getOrNull() },
    times = times.split(",").map { LocalTime.ofSecondOfDay(it.toLong()) },
    repeat = when (repeatType) {
        TYPE_WEEKDAYS -> Repeat.OnWeekdays(bitsToDays(weekdays!!))
        TYPE_EVERY_N_DAYS -> Repeat.EveryNDays(everyNDays!!)
        else -> Repeat.Daily
    },
    startDate = startDate,
    endDate = endDate,
    pauses = pausesFromJson(pauses),
    remind = remind,
    repeatReminder = repeatReminder,
    note = note,
    position = position,
)

fun DoseTaken.toEntity() = DoseTakenEntity(medicationId, date, time, takenAt)

fun DoseTakenEntity.toDomain() = DoseTaken(medicationId, date, time, takenAt)
