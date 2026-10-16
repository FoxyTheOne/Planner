package app.planner.core.data.repository

import androidx.room.withTransaction
import app.planner.core.data.db.PlannerDatabase
import app.planner.core.data.db.toDomain
import app.planner.core.data.db.toEntity
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import app.planner.core.domain.overdue.Overdue
import app.planner.core.domain.repository.ITaskListRepository
import app.planner.core.domain.repository.ITaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранилище задач поверх базы Room.
 *
 * Экраны и ViewModel не знают про Room: они работают с интерфейсом [ITaskRepository]
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
    private val lists: ITaskListRepository,
) : ITaskRepository {

    private val taskDao = db.taskDao()

    // Методы observe… возвращают Flow — «поток» значений. Room сам присылает новый список,
    // как только таблица изменилась, поэтому экраны обновляются без ручного перезапроса.

    override fun observeTasksBetween(from: LocalDate, to: LocalDate): Flow<List<Task>> =
        taskDao.observeBetween(from, to).map { it.map { e -> e.toDomain() } }

    override fun observeOccurrenceTasks(from: LocalDate, to: LocalDate): Flow<List<Task>> =
        taskDao.observeOccurrenceTasks(from, to).map { it.map { e -> e.toDomain() } }

    override suspend fun getOccurrenceTasks(ruleId: Long): List<Task> =
        taskDao.getOfRule(ruleId).map { it.toDomain() }

    override suspend fun deleteOccurrenceTasks(ruleId: Long) = taskDao.deleteOfRule(ruleId)

    override fun observeOverdue(today: LocalDate): Flow<List<Task>> =
        taskDao.observeOverdue(today).map { it.map { e -> e.toDomain() } }

    override fun observeListTasks(listId: Long): Flow<List<Task>> =
        taskDao.observeByList(listId).map { it.map { e -> e.toDomain() } }

    override fun observeUnscheduled(): Flow<List<Task>> =
        taskDao.observeUnscheduled().map { it.map { e -> e.toDomain() } }

    override fun observeOpen(): Flow<List<Task>> =
        taskDao.observeOpen().map { it.map { e -> e.toDomain() } }

    // Список пар (список, число) превращаем в словарь, чтобы экран брал число по id списка
    override fun observeOpenCountsByList(): Flow<Map<Long, Int>> =
        taskDao.observeOpenCounts().map { rows -> rows.associate { it.listId to it.count } }

    // suspend-функции выполняются в корутине: не блокируют экран, пока идёт работа с диском

    override suspend fun getTask(id: Long): Task? = taskDao.get(id)?.toDomain()

    override suspend fun addOrUpdate(task: Task): Long {
        // Каждая задача живёт в каком-нибудь списке: без списка — во «Входящих».
        // Тогда задача, созданная прямо в «Сегодня», не потеряется, если её вернуть без даты.
        val withList = if (task.listId == null) task.copy(listId = lists.inboxId()) else task
        // @Upsert вставляет новую строку или обновляет существующую (по id) и возвращает Long:
        // - если строка ВСТАВЛЕНА — её номер в таблице (rowId). У нас id — автоматический первичный
        //   ключ, поэтому rowId = это и есть новый id, который база только что выдала задаче;
        // - если строка ОБНОВЛЕНА — -1 (нового номера нет).
        val rowId = taskDao.addOrUpdate(withList.toEntity())
        // Новая задача (id = 0) — берём id, выданный базой; существующая — её id и так известен
        return if (task.id == 0L) rowId else task.id
    }

    override suspend fun delete(task: Task) = taskDao.delete(task.toEntity())

    override suspend fun setDone(id: Long, done: Boolean) {
        val task = taskDao.get(id) ?: return
        taskDao.addOrUpdate(
            task.copy(
                status = if (done) TaskStatus.DONE else TaskStatus.OPEN,
                doneAt = if (done) Instant.now() else null,
            ),
        )
    }

    override suspend fun schedule(id: Long, slot: Slot) {
        val task = taskDao.get(id) ?: return
        taskDao.addOrUpdate(
            task.copy(
                date = slot.date,
                partOfDay = slot.partOfDay,
                time = slot.time
            )
        )
    }

    override suspend fun unschedule(id: Long) {
        val task = taskDao.get(id) ?: return
        taskDao.addOrUpdate(Overdue.returnedToList(task.toDomain(), lists.inboxId()).toEntity())
    }

    /*
     * КОГДА НУЖЕН withTransaction
     *
     * Транзакция — «всё внутри выполнится целиком, или не выполнится ничего», и пока она идёт,
     * никто другой не может менять эти данные.
     *
     * Один вызов DAO (одна запись, upsertAll сразу многих задач, один UPDATE) Room уже сам
     * выполняет целиком — оборачивать не нужно.
     *
     * Оборачиваем, когда в методе НЕСКОЛЬКО обращений к базе, которые должны быть одним целым.
     * Чаще всего это «прочитать → изменить → записать»: между чтением и записью человек
     * мог успеть отметить задачу выполненной, и мы записали бы поверх его отметки старую копию.
     */

    override suspend fun returnStaleOverdue(today: LocalDate, returnAfterDays: Int): Int =
        db.withTransaction {
            val inbox = lists.inboxId()
            // Правило «что считать забытым» живёт в domain (Overdue) — здесь только применяем его
            val stale = taskDao.getOverdue(today).map { it.toDomain() }
                .filter { Overdue.shouldReturnToList(it, today, returnAfterDays) }
            taskDao.upsertAll(stale.map { Overdue.returnedToList(it, inbox).toEntity() })
            // Последняя строка блока — то, что вернёт withTransaction, а значит и вся функция
            stale.size
        }

    override suspend fun moveOverdueToDate(today: LocalDate, target: LocalDate) =
        db.withTransaction {
            taskDao.upsertAll(taskDao.getOverdue(today).map { it.copy(date = target) })
        }

    override suspend fun returnAllOverdueToLists(today: LocalDate) = db.withTransaction {
        val inbox = lists.inboxId()
        taskDao.upsertAll(taskDao.getOverdue(today).map {
            Overdue.returnedToList(it.toDomain(), inbox).toEntity()
        })
    }

    override suspend fun moveOrphansToInbox() {
        // Задачи, созданные до того, как появилось правило «всегда в каком-то списке»
        taskDao.moveOrphansToList(lists.inboxId())
    }
}
