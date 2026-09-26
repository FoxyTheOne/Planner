package app.planner.core.data.backup

import androidx.room.withTransaction
import app.planner.core.data.db.PlannerDatabase
import app.planner.core.data.db.TaskEntity
import app.planner.core.data.db.TaskListEntity
import app.planner.core.domain.model.ListColor
import app.planner.core.domain.model.PartOfDay
import app.planner.core.domain.model.TaskStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Резервная копия в JSON. Формат не зависит от схемы Room:
 * [BackupSnapshot.formatVersion] позволяет читать копии, сделанные старыми версиями приложения.
 */
@Singleton
class BackupManager @Inject constructor(
    private val db: PlannerDatabase,
) {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(output: OutputStream) {
        val snapshot = db.withTransaction {
            BackupSnapshot(
                formatVersion = FORMAT_VERSION,
                exportedAt = Instant.now().toString(),
                lists = db.taskListDao().getAll().map { it.toDto() },
                tasks = db.taskDao().getAll().map { it.toDto() },
            )
        }
        output.bufferedWriter().use { it.write(json.encodeToString(BackupSnapshot.serializer(), snapshot)) }
    }

    /** Читает копию, не трогая базу: чтобы показать, что внутри, до восстановления. */
    fun read(input: InputStream): BackupSnapshot {
        val snapshot = json.decodeFromString(BackupSnapshot.serializer(), input.bufferedReader().use { it.readText() })
        require(snapshot.formatVersion <= FORMAT_VERSION) { "Backup is newer than the app" }
        return snapshot
    }

    /** Полностью заменяет данные содержимым копии. */
    suspend fun restore(snapshot: BackupSnapshot) = db.withTransaction {
        db.taskDao().deleteAll()
        db.taskListDao().deleteAll()
        db.taskListDao().insertAll(snapshot.lists.map { it.toEntity() })
        db.taskDao().insertAll(snapshot.tasks.map { it.toEntity() })
    }

    companion object {
        const val FORMAT_VERSION = 1
    }
}

@Serializable
data class BackupSnapshot(
    val formatVersion: Int,
    val exportedAt: String,
    val lists: List<TaskListDto>,
    val tasks: List<TaskDto>,
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
