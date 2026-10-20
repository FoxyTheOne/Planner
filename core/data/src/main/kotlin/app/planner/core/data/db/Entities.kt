package app.planner.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.planner.core.domain.model.ListColor
import app.planner.core.domain.model.PartOfDay
import app.planner.core.domain.model.Reminder
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Строка таблицы `tasks` в базе Room.
 *
 * Почему не хранить сразу доменный [Task]? Сущность (Entity) описывает *как лежит в базе*:
 * имя таблицы, индексы, внешние ключи. Доменная модель — *что это для приложения*.
 * Разделение позволяет менять устройство базы, не трогая экраны и бизнес-логику.
 * Переводят одно в другое функции `toDomain()` / `toEntity()` внизу файла.
 *
 * - `foreignKeys` — связь с таблицей списков: у задачи listId должен указывать на существующий список;
 * - `indices` — индексы ускоряют частые выборки по дате, списку и статусу.
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            // Удалили список — задачи не пропадают, а остаются без списка
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = RecurrenceRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            // Удалили правило — отмеченные дни остаются в истории как обычные задачи
            onDelete = ForeignKey.SET_NULL,
        ),
        // Шаги цели. SET_NULL, а не CASCADE: база сама никогда не удаляет задачи «молча».
        // Что делать с шагами удалённой цели, решает приложение — явно, с «Отменить»
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        // Удалили этап — шаги остаются в своей цели, просто без этапа
        ForeignKey(
            entity = GoalStageEntity::class,
            parentColumns = ["id"],
            childColumns = ["stageId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        // Удалили повторение — его задача-подготовка остаётся обычной задачей
        ForeignKey(
            entity = RecurrenceRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["prepRuleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("date"), Index("listId"), Index("status"), Index("ruleId", "occurrenceDate"),
        Index("goalId"), Index("stageId"), Index("prepRuleId"),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val partOfDay: PartOfDay?,
    val listId: Long?,
    val status: TaskStatus,
    val isSample: Boolean,
    val createdAt: Instant,
    val doneAt: Instant?,
    /** День повторения: id правила и день правила, к которому относится задача (см. Task). */
    val ruleId: Long?,
    val occurrenceDate: LocalDate?,
    /** Шаг цели: id цели и этапа (см. Task). Колонки появились в версии базы 6. */
    val goalId: Long?,
    val stageId: Long?,
    /** Место шага в этапе (см. Task.position). Колонка появилась в версии базы 7; у старых строк 0. */
    @ColumnInfo(defaultValue = "0")
    val position: Int = 0,
    /**
     * Напоминание (см. Reminder) — в двух колонках, заполнена не больше одной:
     * - reminderMinutesBefore — «за N минут до времени задачи» (0 — в момент);
     * - reminderAt — «в этот час в день задачи» (у задач без времени).
     * Обе пустые — напоминания нет. Колонки появились в версии базы 8; у старых строк пустые.
     */
    val reminderMinutesBefore: Int? = null,
    val reminderAt: LocalTime? = null,
    /** Задача-подготовка к дню «раз в год» (см. Task.prepRuleId). Колонки появились в версии базы 11. */
    val prepRuleId: Long? = null,
    val prepFor: LocalDate? = null,
)

/** Строка таблицы `task_lists` — список «Когда-нибудь» в базе Room. */
@Entity(tableName = "task_lists")
data class TaskListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: ListColor,
    val position: Int,
    val isInbox: Boolean,
)

// Функции-расширения: вызываются как методы (`entity.toDomain()`), хотя объявлены вне класса

fun TaskEntity.toDomain() = Task(
    id = id,
    title = title,
    note = note,
    date = date,
    time = time,
    partOfDay = partOfDay,
    listId = listId,
    status = status,
    isSample = isSample,
    createdAt = createdAt,
    doneAt = doneAt,
    ruleId = ruleId,
    occurrenceDate = occurrenceDate,
    goalId = goalId,
    stageId = stageId,
    position = position,
    // Какая колонка заполнена, такой и вид напоминания; обе пустые — напоминания нет
    reminder = reminderMinutesBefore?.let { Reminder.BeforeTime(it) }
        ?: reminderAt?.let { Reminder.AtTime(it) },
    prepRuleId = prepRuleId,
    prepFor = prepFor,
)

fun Task.toEntity() = TaskEntity(
    id = id,
    title = title,
    note = note,
    date = date,
    time = time,
    partOfDay = partOfDay,
    listId = listId,
    status = status,
    isSample = isSample,
    createdAt = createdAt,
    doneAt = doneAt,
    ruleId = ruleId,
    occurrenceDate = occurrenceDate,
    goalId = goalId,
    stageId = stageId,
    position = position,
    reminderMinutesBefore = (reminder as? Reminder.BeforeTime)?.minutes,
    reminderAt = (reminder as? Reminder.AtTime)?.time,
    prepRuleId = prepRuleId,
    prepFor = prepFor,
)

fun TaskListEntity.toDomain() =
    TaskList(id = id, name = name, color = color, position = position, isInbox = isInbox)

fun TaskList.toEntity() =
    TaskListEntity(id = id, name = name, color = color, position = position, isInbox = isInbox)
