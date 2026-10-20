package app.planner.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.planner.core.domain.model.PartOfDay
import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Reminder
import app.planner.core.domain.model.Repeat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay

/**
 * Строка таблицы `recurrence_rules` — правило повторения в базе.
 *
 * В домене «как часто» — это [Repeat] с четырьмя вариантами, у каждого свои данные.
 * В таблице так нельзя: у всех строк одинаковые колонки. Поэтому раскладываем так:
 * - [repeatType] — какой это вариант: DAILY, WEEKDAYS, EVERY_N_DAYS, CYCLE или YEARLY
 *   (у YEARLY своих колонок нет: день в году — это [startDate]);
 * - колонки под данные каждого варианта; у «чужих» вариантов они пустые (null).
 *   Например, у «по дням недели» заполнено [weekdays], а [everyNDays] и cycle… — null.
 *
 * Таблица дел цикла ([cycleWeeks]) и паузы ([pauses]) — это списки внутри правила.
 * Отдельно по ним мы никогда не ищем, поэтому храним их текстом в формате JSON
 * (как в файле бэкапа), а не отдельными таблицами. Так проще и миграции, и бэкап.
 *
 * Перевод в доменную модель и обратно — `toDomain()` / `toEntity()` внизу файла.
 */
@Entity(
    tableName = "recurrence_rules",
    foreignKeys = [
        ForeignKey(
            entity = TaskListEntity::class, // на какую таблицу ссылаемся — task_lists («родитель»)
            parentColumns = ["id"], // колонка у родителя
            childColumns = ["listId"], // колонка у нас («ребёнок»)
            // Удалили список — правило не пропадает, а остаётся без списка (как задачи)
            onDelete = ForeignKey.SET_NULL, // что делать с ребёнком, если родителя удалили: NO_ACTION / RESTRICT -	список удалить не дадут, пока на него кто-то ссылается, CASCADE - правило удалится вместе со списком, SET_NULL - правило останется, а listId станет null («без списка»), SET_DEFAULT - listId станет значением по умолчанию
        ),
        // Удалили цель — привязанная к ней привычка остаётся, просто ни к чему не привязана
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("listId"), Index("goalId")], // Для колонки с внешним ключом индекс особенно важен
)
data class RecurrenceRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val time: LocalTime?,
    val partOfDay: PartOfDay?,
    val listId: Long?,
    val repeatType: String,
    /** Дни недели одним числом: каждый день — свой бит (пн = 1, вт = 2, ср = 4, … вс = 64). */
    val weekdays: Int?,
    val everyNDays: Int?,
    /** JSON: `[{"WEDNESDAY":"фикус"}, {"WEDNESDAY":"фикус","SATURDAY":"подкормка"}]`. */
    val cycleWeeks: String?,
    /** Первый день опорной недели цикла. До версии базы 4 колонка называлась cycleAnchorMonday. */
    val cycleAnchorWeekStart: LocalDate?,
    val cycleAnchorWeekNumber: Int?,
    /**
     * С какого дня начинается неделя цикла: MONDAY или SUNDAY.
     * null — цикл создан до версии базы 4, тогда недели у него всегда считались с понедельника.
     */
    val cycleWeekStart: String?,
    /** JSON: `[{"from":20738,"until":20744}]` — пауза 12–18 октября 2026, даты числом дней от 1970 года. */
    val pauses: String,
    /**
     * Привычка (см. [RecurrenceRule.isHabit]). Колонка появилась в версии базы 5.
     *
     * `defaultValue = "0"` — чем заполнить колонку у правил, которые уже лежат в базе, когда
     * миграция её добавит. SQLite хранит Boolean числом: 0 — false, 1 — true.
     * Без значения по умолчанию автомиграция не получится: колонка обязательная (не null),
     * и Room не знает, что записать в старые строки.
     */
    @ColumnInfo(defaultValue = "0")
    val isHabit: Boolean = false,
    /** Цель, которой помогает привычка (см. RecurrenceRule.goalId). Колонка появилась в версии базы 6. */
    val goalId: Long? = null,
    /** Место в ряду привычек (см. RecurrenceRule.position). Колонка появилась в версии базы 9. */
    @ColumnInfo(defaultValue = "0")
    val position: Int = 0,
    /**
     * Напоминание каждого дня повторения — как у задачи, в двух колонках (см. TaskEntity):
     * «за N минут до времени» или «в этот час». Колонки появились в версии базы 10; у старых правил пустые.
     */
    val reminderMinutesBefore: Int? = null,
    val reminderAt: LocalTime? = null,
    /** За сколько дней готовиться к дню «раз в год» (см. RecurrenceRule.prepDays). Колонка — с версии базы 11. */
    val prepDays: Int? = null,
)

// Названия вариантов в колонке repeatType. Их нельзя переименовывать: они уже лежат в базах людей
private const val TYPE_DAILY = "DAILY"
private const val TYPE_WEEKDAYS = "WEEKDAYS"
private const val TYPE_EVERY_N_DAYS = "EVERY_N_DAYS"
private const val TYPE_CYCLE = "CYCLE"
private const val TYPE_YEARLY = "YEARLY"

/** Как пауза лежит внутри JSON. Отдельная модель уровня data, чтобы модель уровня domain не зависела от формата хранения. */
@Serializable
private data class PauseJson(val from: Long, val until: Long? = null)

private val json = Json { ignoreUnknownKeys = true }

fun RecurrenceRule.toEntity(): RecurrenceRuleEntity {
    // Сначала общие поля, потом заполняем колонки своего варианта через copy
    val base = RecurrenceRuleEntity(
        id = id,
        title = title,
        startDate = startDate,
        endDate = endDate,
        time = time,
        partOfDay = partOfDay,
        listId = listId,
        isHabit = isHabit,
        goalId = goalId,
        position = position,
        reminderMinutesBefore = (reminder as? Reminder.BeforeTime)?.minutes,
        reminderAt = (reminder as? Reminder.AtTime)?.time,
        prepDays = prepDays,
        repeatType = "",
        weekdays = null,
        everyNDays = null,
        cycleWeeks = null,
        cycleAnchorWeekStart = null,
        cycleAnchorWeekNumber = null,
        cycleWeekStart = null,
        pauses = json.encodeToString(pauses.map {
            // toEpochDay() превращает дату в число дней, прошедших с 1 января 1970 года. 12 октября 2026 → 20738, 18 октября → 20744. Числом удобнее, чем строкой "2026-10-12": короче, и так же даты хранятся во всей нашей базе (см. Converters)
            PauseJson(
                it.from.toEpochDay(),
                it.until?.toEpochDay()
            )
        }),
    )
    // Зачем val r = repeat, а не просто when (repeat). repeat — свойство класса RecurrenceRule, а класс объявлен в другом модуле (domain). Для свойства из другого модуля Kotlin не делает smart cast: он не может быть уверен, что между проверкой is и обращением .n значение никто не подменит. Например, в будущей версии модуля это свойство могут переделать так, что оно будет каждый раз возвращать новый объект. Локальная переменная r не изменится точно, поэтому с ней smart cast работает. val внутри when (...) видна только внутри этого when.
    return when (val r = repeat) {
        Repeat.Daily -> base.copy(repeatType = TYPE_DAILY) // результат, если r — это вариант Daily. Daily - это data object, сравнение без is: это тот самый единственный объект?
        is Repeat.OnWeekdays -> base.copy(repeatType = TYPE_WEEKDAYS, weekdays = daysToBits(r.days))
        is Repeat.EveryNDays -> base.copy(
            repeatType = TYPE_EVERY_N_DAYS,
            everyNDays = r.n
        ) // результат, если r — это вариант EveryNDays. EveryNDays - это data class, проверка с is: это какой-нибудь EveryNDays?
        // День в году отдельно не храним: это день начала правила (startDate)
        is Repeat.Yearly -> base.copy(repeatType = TYPE_YEARLY)
        is Repeat.Cycle -> base.copy(
            repeatType = TYPE_CYCLE,
            // Ключи словаря — названия дней (MONDAY…), чтобы JSON было легко прочитать глазами
            cycleWeeks = json.encodeToString(r.weeks.map { week -> week.mapKeys { it.key.name } }),
            cycleAnchorWeekStart = r.anchorWeekStart,
            cycleAnchorWeekNumber = r.anchorWeekNumber,
            cycleWeekStart = r.weekStart.name,
        )
    }
}

fun RecurrenceRuleEntity.toDomain(): RecurrenceRule {
    // `!!` — «здесь точно не null»: для своего варианта колонка всегда заполнена (см. toEntity)
    val repeat = when (repeatType) {
        TYPE_DAILY -> Repeat.Daily
        TYPE_WEEKDAYS -> Repeat.OnWeekdays(bitsToDays(weekdays!!))
        TYPE_EVERY_N_DAYS -> Repeat.EveryNDays(everyNDays!!)
        TYPE_YEARLY -> Repeat.Yearly(MonthDay.from(startDate))
        TYPE_CYCLE -> Repeat.Cycle(
            weeks = json.decodeFromString<List<Map<String, String>>>(cycleWeeks!!)
                .map { week -> week.mapKeys { DayOfWeek.valueOf(it.key) } },
            anchorWeekStart = cycleAnchorWeekStart!!,
            anchorWeekNumber = cycleAnchorWeekNumber!!,
            weekStart = cycleWeekStart?.let(DayOfWeek::valueOf) ?: DayOfWeek.MONDAY,
        )

        else -> error("Неизвестный тип повторения: $repeatType")
    }
    return RecurrenceRule(
        id = id, title = title, repeat = repeat, startDate = startDate, endDate = endDate,
        pauses = json.decodeFromString<List<PauseJson>>(pauses).map {
            Pause(LocalDate.ofEpochDay(it.from), it.until?.let(LocalDate::ofEpochDay))
        },
        time = time, partOfDay = partOfDay, listId = listId, isHabit = isHabit, goalId = goalId,
        position = position,
        reminder = reminderMinutesBefore?.let { Reminder.BeforeTime(it) }
            ?: reminderAt?.let { Reminder.AtTime(it) },
        prepDays = prepDays,
    )
}

/**
 * Набор дней недели → одно число. У DayOfWeek есть номер ordinal: пн = 0 … вс = 6.
 * `1 shl n` — единица, сдвинутая на n разрядов: пн → 1, вт → 2, ср → 4… `or` складывает биты.
 * Пн + ср + пт = 1 + 4 + 16 = 21.
 */
private fun daysToBits(days: Set<DayOfWeek>): Int =
    days.fold(0) { bits, day -> bits or (1 shl day.ordinal) }

/** Обратно: оставляем дни, чей бит включён. 21 → пн, ср, пт. */
private fun bitsToDays(bits: Int): Set<DayOfWeek> =
    DayOfWeek.entries.filter { bits and (1 shl it.ordinal) != 0 }.toSet()
