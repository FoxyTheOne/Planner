package app.planner.core.data.repository

import androidx.room.withTransaction
import app.planner.core.data.db.PlannerDatabase
import app.planner.core.data.db.toDomain
import app.planner.core.data.db.toEntity
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import app.planner.core.domain.overdue.Overdue
import app.planner.core.domain.repository.TaskListRepository
import app.planner.core.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранилище задач поверх базы Room.
 *
 * Экраны и ViewModel не знают про Room: они работают с интерфейсом [TaskRepository]
 * из модуля `domain`. Этот класс — его реализация: переводит «хочу задачи на неделю»
 * в SQL-запросы к [PlannerDatabase] и превращает строки таблицы ([app.planner.core.data.db.TaskEntity])
 * обратно в доменные [Task].
 *
 * Про аннотации:
 * - `@Inject constructor` — Hilt сам создаст этот класс и передаст ему базу и репозиторий списков;
 * - `@Singleton` — на всё приложение будет один экземпляр.
 */
@Singleton
class RoomTaskRepository @Inject constructor(
    private val db: PlannerDatabase,
    private val lists: TaskListRepository,
) : TaskRepository {

    private val dao = db.taskDao()

    // Методы observe… возвращают Flow — «поток» значений. Room сам присылает новый список,
    // как только таблица изменилась, поэтому экраны обновляются без ручного перезапроса.

    override fun observeTasksBetween(from: LocalDate, to: LocalDate): Flow<List<Task>> =
        dao.observeBetween(from, to).map { it.map { e -> e.toDomain() } }

    override fun observeOverdue(today: LocalDate): Flow<List<Task>> =
        dao.observeOverdue(today).map { it.map { e -> e.toDomain() } }

    override fun observeListTasks(listId: Long): Flow<List<Task>> =
        dao.observeByList(listId).map { it.map { e -> e.toDomain() } }

    override fun observeUnscheduled(): Flow<List<Task>> =
        dao.observeUnscheduled().map { it.map { e -> e.toDomain() } }

    override fun observeOpen(): Flow<List<Task>> =
        dao.observeOpen().map { it.map { e -> e.toDomain() } }

    // Список пар (список, число) превращаем в словарь, чтобы экран брал число по id списка
    override fun observeOpenCountsByList(): Flow<Map<Long, Int>> =
        dao.observeOpenCounts().map { rows -> rows.associate { it.listId to it.count } }

    // suspend-функции выполняются в корутине: не блокируют экран, пока идёт работа с диском

    override suspend fun getTask(id: Long): Task? = dao.get(id)?.toDomain()

    override suspend fun upsert(task: Task): Long {
        // Каждая задача живёт в каком-нибудь списке: без списка — во «Входящих».
        // Тогда задача, созданная прямо в «Сегодня», не потеряется, если её вернуть без даты.
        val withList = if (task.listId == null) task.copy(listId = lists.inboxId()) else task
        // @Upsert вставляет новую строку или обновляет существующую (по id).
        // Для обновления он возвращает -1, поэтому id берём из самой задачи.
        val rowId = dao.upsert(withList.toEntity())
        return if (task.id == 0L) rowId else task.id
    }

    override suspend fun delete(task: Task) = dao.delete(task.toEntity())

    override suspend fun setDone(id: Long, done: Boolean) {
        val task = dao.get(id) ?: return
        dao.upsert(
            task.copy(
                status = if (done) TaskStatus.DONE else TaskStatus.OPEN,
                doneAt = if (done) Instant.now() else null,
            ),
        )
    }

    override suspend fun schedule(id: Long, slot: Slot) {
        val task = dao.get(id) ?: return
        dao.upsert(task.copy(date = slot.date, partOfDay = slot.partOfDay, time = slot.time))
    }

    override suspend fun unschedule(id: Long) {
        val task = dao.get(id) ?: return
        dao.upsert(Overdue.returnedToList(task.toDomain(), lists.inboxId()).toEntity())
    }

    override suspend fun returnStaleOverdue(today: LocalDate, returnAfterDays: Int): Int {
        val inbox = lists.inboxId()
        // Правило «что считать забытым» живёт в domain (Overdue) — здесь только применяем его
        val stale = dao.getOverdue(today).map { it.toDomain() }
            .filter { Overdue.shouldReturnToList(it, today, returnAfterDays) }
        dao.upsertAll(stale.map { Overdue.returnedToList(it, inbox).toEntity() })
        return stale.size
    }

    // withTransaction — всё внутри выполнится целиком или не выполнится вовсе:
    // не получится, что половина задач переехала, а половина нет.

    override suspend fun moveOverdueToDate(today: LocalDate, target: LocalDate) = db.withTransaction {
        dao.upsertAll(dao.getOverdue(today).map { it.copy(date = target) })
    }

    override suspend fun returnAllOverdueToLists(today: LocalDate) = db.withTransaction {
        val inbox = lists.inboxId()
        dao.upsertAll(dao.getOverdue(today).map { Overdue.returnedToList(it.toDomain(), inbox).toEntity() })
    }

    override suspend fun moveOrphansToInbox() {
        // Задачи, созданные до того, как появилось правило «всегда в каком-то списке»
        dao.moveOrphansToList(lists.inboxId())
    }
}
