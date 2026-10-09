package app.planner.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * DAO (Data Access Object) — «пульт» таблицы задач.
 *
 * Мы только описываем интерфейс и пишем SQL в аннотациях `@Query`,
 * а Room при сборке сам генерирует код, который эти запросы выполняет.
 * Ошибка в SQL (например, опечатка в имени колонки) поймается ещё при компиляции.
 *
 * - Функции, возвращающие `Flow`, — «подписки»: Room пришлёт новый результат при каждом изменении таблицы.
 * - `suspend`-функции — разовые операции, их вызывают из корутин.
 * - `:today`, `:listId` в запросе — это параметры функции с тем же именем.
 * - Даты хранятся числами (см. [Converters]), поэтому `date < :today` сравнивает их правильно.
 */
@Dao
interface TaskDao {

    // Внутри дня: сначала задачи со временем по порядку, потом остальные по времени создания.
    // «time IS NULL» даёт 0 для задач со временем и 1 для остальных — так они уходят вниз.
    @Query(
        """
        SELECT * FROM tasks WHERE date BETWEEN :from AND :to
        ORDER BY date, time IS NULL, time, createdAt
        """,
    )
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<TaskEntity>>

    // Хвосты: открытые задачи с датой в прошлом. События с точным временем сюда не попадают
    @Query("SELECT * FROM tasks WHERE status = 'OPEN' AND date < :today AND time IS NULL ORDER BY date DESC")
    fun observeOverdue(today: LocalDate): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 'OPEN' AND date < :today AND time IS NULL")
    suspend fun getOverdue(today: LocalDate): List<TaskEntity>

    // Задачи одного списка: сначала то, что ещё ждёт своего часа, запланированные — внизу, по дате
    @Query("SELECT * FROM tasks WHERE listId = :listId AND status = 'OPEN' ORDER BY date IS NOT NULL, date, createdAt")
    fun observeByList(listId: Long): Flow<List<TaskEntity>>

    // Все открытые задачи — для поиска в оглавлении списков (фильтр по тексту делаем в Kotlin:
    // SQLite умеет сравнивать без учёта регистра только латиницу, а у нас кириллица)
    @Query("SELECT * FROM tasks WHERE status = 'OPEN' ORDER BY createdAt DESC")
    fun observeOpen(): Flow<List<TaskEntity>>

    // Сколько открытых задач в каждом списке — счётчики в оглавлении
    @Query("SELECT listId, COUNT(*) AS count FROM tasks WHERE status = 'OPEN' AND listId IS NOT NULL GROUP BY listId")
    fun observeOpenCounts(): Flow<List<ListCount>>

    // Всё открытое без даты — кандидаты для шторки «Из списков» и лотка планирования
    @Query("SELECT * FROM tasks WHERE date IS NULL AND status = 'OPEN' ORDER BY createdAt DESC")
    fun observeUnscheduled(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    // @Upsert = insert или update: если строки с таким id нет — вставит, есть — обновит
    @Upsert
    suspend fun addOrUpdate(task: TaskEntity): Long

    @Upsert
    suspend fun upsertAll(tasks: List<TaskEntity>)

    @Delete
    suspend fun delete(task: TaskEntity)

    // --- Для резервной копии ---

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    // --- Переезды между списками ---

    @Query("UPDATE tasks SET listId = :toListId WHERE listId = :fromListId")
    suspend fun moveToList(fromListId: Long, toListId: Long)

    @Query("UPDATE tasks SET listId = :toListId WHERE listId IS NULL")
    suspend fun moveOrphansToList(toListId: Long)
}

/** Результат подсчёта: в списке [listId] столько-то открытых задач. */
data class ListCount(val listId: Long, val count: Int)

/**
 * DAO таблицы списков. Списки идут в порядке, в котором их создали,
 * а служебный «Без списка» (isInbox) — последним.
 */
@Dao
interface TaskListDao {
    @Query("SELECT * FROM task_lists ORDER BY isInbox, position, id")
    fun observeAll(): Flow<List<TaskListEntity>>

    @Query("SELECT id FROM task_lists WHERE isInbox = 1 LIMIT 1")
    suspend fun inboxId(): Long?

    @Upsert
    suspend fun upsert(list: TaskListEntity): Long

    @Delete
    suspend fun delete(list: TaskListEntity)

    // --- Для резервной копии ---

    @Query("SELECT * FROM task_lists")
    suspend fun getAll(): List<TaskListEntity>

    @Insert
    suspend fun insertAll(lists: List<TaskListEntity>)

    @Query("DELETE FROM task_lists")
    suspend fun deleteAll()
}

/** DAO таблицы правил повторения. */
@Dao
interface RecurrenceRuleDao {
    @Query("SELECT * FROM recurrence_rules ORDER BY id")
    fun observeAll(): Flow<List<RecurrenceRuleEntity>>

    @Query("SELECT * FROM recurrence_rules WHERE id = :id")
    suspend fun get(id: Long): RecurrenceRuleEntity?

    @Upsert
    suspend fun upsert(rule: RecurrenceRuleEntity): Long

    @Query("DELETE FROM recurrence_rules WHERE id = :id")
    suspend fun delete(id: Long)

    // --- Для резервной копии ---

    @Query("SELECT * FROM recurrence_rules")
    suspend fun getAll(): List<RecurrenceRuleEntity>

    @Insert
    suspend fun insertAll(rules: List<RecurrenceRuleEntity>)

    @Query("DELETE FROM recurrence_rules")
    suspend fun deleteAll()
}