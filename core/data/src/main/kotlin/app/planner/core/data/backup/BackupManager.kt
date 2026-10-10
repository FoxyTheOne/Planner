package app.planner.core.data.backup

import androidx.room.withTransaction
import app.planner.core.data.db.PlannerDatabase
import app.planner.core.data.db.TaskEntity
import app.planner.core.data.db.TaskListEntity
import app.planner.core.data.db.toDomain
import app.planner.core.data.db.toEntity
import app.planner.core.domain.model.ListColor
import app.planner.core.domain.model.PartOfDay
import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.TaskStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Резервная копия в JSON. Формат не зависит от схемы Room:
 * [BackupSnapshot.formatVersion] позволяет читать копии, сделанные старыми версиями приложения.
 *
 * История формата:
 * - 1 — списки и задачи;
 * - 2 — добавлены правила повторения (`rules`). В файлах версии 1 этого поля нет —
 *   при чтении оно берётся по умолчанию (пустой список), поэтому старые копии читаются как раньше.
 *
 * Даты в файле записаны текстом (`"2026-10-12"`), а не числами, как в базе: файл — это
 * «на всякий случай», и его должно быть можно прочитать глазами и через много лет.
 */
@Singleton
class BackupManager @Inject constructor(
    private val db: PlannerDatabase,
) {
    companion object {
        const val FORMAT_VERSION = 2
    }

    private val json = backupJson

    suspend fun export(output: OutputStream) {
        val snapshot = db.withTransaction {
            BackupSnapshot(
                formatVersion = FORMAT_VERSION,
                exportedAt = Instant.now().toString(),
                lists = db.taskListDao().getAll().map { it.toDto() },
                tasks = db.taskDao().getAll().map { it.toDto() },
                // Правила: строка таблицы → доменная модель → то, как правило записано в файле
                rules = db.recurrenceRuleDao().getAll().map { it.toDomain().toDto() },
            )
        }
        output.bufferedWriter().use {
            it.write(json.encodeToString(BackupSnapshot.serializer(), snapshot))
        }
    }

    /** Читает копию, не трогая базу: чтобы показать, что внутри, до восстановления. */
    fun read(input: InputStream): BackupSnapshot {
        val snapshot = json.decodeFromString(
            BackupSnapshot.serializer(),
            input.bufferedReader().use { it.readText() })
        require(snapshot.formatVersion <= FORMAT_VERSION) { "Backup is newer than the app" }
        return snapshot
    }

    /** Полностью заменяет данные содержимым копии. */
    suspend fun restore(snapshot: BackupSnapshot) = db.withTransaction {
        db.taskDao().deleteAll()
        db.recurrenceRuleDao().deleteAll()
        db.taskListDao().deleteAll()
        // Сначала списки: задачи и правила ссылаются на них (внешний ключ listId)
        db.taskListDao().insertAll(snapshot.lists.map { it.toEntity() })
        db.taskDao().insertAll(snapshot.tasks.map { it.toEntity() })
        db.recurrenceRuleDao().insertAll(snapshot.rules.map { it.toDomain().toEntity() })
    }
}

// Настройки перевода объектов в JSON-текст и обратно.
// internal — чтобы тесты модуля могли читать JSON так же, как приложение
internal val backupJson = Json {
    // false — писать JSON одной строкой, без отступов: файл меньше. Прочитать глазами его всё равно можно — любым просмотрщиком JSON
    prettyPrint = false
    // Если в файле есть поле, которого нет в наших классах (копию сделала более новая
    // версия приложения с дополнительным полем), — пропустить его, а не падать с ошибкой
    ignoreUnknownKeys = true
    // Записывать и поля, равные значениям по умолчанию (например, note = "").
    // Иначе они в файл не попадут, и если мы потом поменяем значение по умолчанию,
    // старая копия прочитается уже с новым — и смысл данных незаметно изменится
    encodeDefaults = true
}

@Serializable
data class BackupSnapshot(
    val formatVersion: Int,
    val exportedAt: String,
    val lists: List<TaskListDto>,
    val tasks: List<TaskDto>,
    // = emptyList(): в копиях формата 1 правил нет — тогда читаем пустой список
    val rules: List<RuleDto> = emptyList(),
)

@Serializable
data class TaskListDto(
    val id: Long,
    val name: String,
    val color: String,
    val position: Int,
    val isInbox: Boolean,
)

@Serializable
data class TaskDto(
    val id: Long,
    val title: String,
    val note: String = "",
    val date: String? = null,
    val time: String? = null,
    val partOfDay: String? = null,
    val listId: Long? = null,
    val status: String = TaskStatus.OPEN.name,
    val isSample: Boolean = false,
    val createdAt: String,
    val doneAt: String? = null,
)

private fun TaskListEntity.toDto() = TaskListDto(id, name, color.name, position, isInbox)

private fun TaskListDto.toEntity() = TaskListEntity(
    id = id,
    name = name,
    color = runCatching { ListColor.valueOf(color) }.getOrDefault(ListColor.SAGE),
    position = position,
    isInbox = isInbox,
)

private fun TaskEntity.toDto() = TaskDto(
    id = id,
    title = title,
    note = note,
    date = date?.toString(),
    time = time?.toString(),
    partOfDay = partOfDay?.name,
    listId = listId,
    status = status.name,
    isSample = isSample,
    createdAt = createdAt.toString(),
    doneAt = doneAt?.toString(),
)

private fun TaskDto.toEntity() = TaskEntity(
    id = id,
    title = title,
    note = note,
    date = date?.let(LocalDate::parse),
    time = time?.let(LocalTime::parse),
    partOfDay = partOfDay?.let { runCatching { PartOfDay.valueOf(it) }.getOrNull() },
    listId = listId,
    status = runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.OPEN),
    isSample = isSample,
    createdAt = Instant.parse(createdAt),
    doneAt = doneAt?.let(Instant::parse),
)

/**
 * Правило повторения в файле. Вариант повторения ([RepeatDto]) записан так же, как в базе:
 * тип + поля этого типа, у остальных типов поля пустые.
 */
@Serializable
data class RuleDto(
    val id: Long,
    val title: String,
    val startDate: String,
    val endDate: String? = null,
    val time: String? = null,
    val partOfDay: String? = null,
    val listId: Long? = null,
    val repeat: RepeatDto,
    val pauses: List<PauseDto> = emptyList(),
)

/**
 * «Как часто» в файле. [type]: DAILY, WEEKDAYS, EVERY_N_DAYS или CYCLE.
 * Дни недели — названиями (`"MONDAY"`), а не битами, как в базе: так файл понятен человеку.
 */
@Serializable
data class RepeatDto(
    val type: String,
    val days: List<String>? = null,
    val n: Int? = null,
    val weeks: List<Map<String, String>>? = null,
    val anchorMonday: String? = null,
    val anchorWeekNumber: Int? = null,
)

@Serializable
data class PauseDto(val from: String, val until: String? = null)

// internal — видно во всём модуле data (в том числе в его тестах), но не в других модулях
// DTO - Data Transfer Object
internal fun RecurrenceRule.toDto() = RuleDto(
    id = id,
    title = title,
    startDate = startDate.toString(),
    endDate = endDate?.toString(),
    time = time?.toString(),
    partOfDay = partOfDay?.name,
    listId = listId,
    repeat = when (val r = repeat) {
        Repeat.Daily -> RepeatDto("DAILY")
        is Repeat.OnWeekdays -> RepeatDto("WEEKDAYS", days = r.days.sorted().map { it.name })
        is Repeat.EveryNDays -> RepeatDto("EVERY_N_DAYS", n = r.n)
        is Repeat.Cycle -> RepeatDto(
            "CYCLE",
            weeks = r.weeks.map { week -> week.mapKeys { it.key.name } },
            anchorMonday = r.anchorMonday.toString(),
            anchorWeekNumber = r.anchorWeekNumber,
        )
    },
    pauses = pauses.map { PauseDto(it.from.toString(), it.until?.toString()) },
)

internal fun RuleDto.toDomain() = RecurrenceRule(
    id = id,
    title = title,
    repeat = when (repeat.type) {
        "DAILY" -> Repeat.Daily
        "WEEKDAYS" -> Repeat.OnWeekdays(repeat.days.orEmpty().map(DayOfWeek::valueOf).toSet())
        "EVERY_N_DAYS" -> Repeat.EveryNDays(repeat.n ?: 1)
        "CYCLE" -> Repeat.Cycle(
            weeks = repeat.weeks.orEmpty()
                .map { week -> week.mapKeys { DayOfWeek.valueOf(it.key) } },
            anchorMonday = LocalDate.parse(repeat.anchorMonday),
            anchorWeekNumber = repeat.anchorWeekNumber ?: 1,
        )
        // Файл из более новой версии приложения не дойдёт сюда: read() отклоняет formatVersion новее нашего
        else -> error("Unknown repeat type: ${repeat.type}")
    },
    startDate = LocalDate.parse(startDate),
    endDate = endDate?.let(LocalDate::parse),
    pauses = pauses.map { Pause(LocalDate.parse(it.from), it.until?.let(LocalDate::parse)) },
    time = time?.let(LocalTime::parse),
    partOfDay = partOfDay?.let { runCatching { PartOfDay.valueOf(it) }.getOrNull() },
    listId = listId,
)
