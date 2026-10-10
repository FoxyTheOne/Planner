package app.planner.core.data.repository

import androidx.room.withTransaction
import app.planner.core.data.db.PlannerDatabase
import app.planner.core.data.db.toDomain
import app.planner.core.data.db.toEntity
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.repository.ITaskListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранилище списков «Когда-нибудь» (Дом, Книги, Покупки…) поверх Room.
 *
 * Особый список — «Входящие»: он системный, создаётся сам и не удаляется.
 * Туда попадают задачи, которым не выбрали список, и задачи из удалённых списков.
 */
@Singleton
class RoomTaskListRepository @Inject constructor(
    private val db: PlannerDatabase,
) : ITaskListRepository {

    private val taskListDao = db.taskListDao()

    override fun observeLists(): Flow<List<TaskList>> =
        taskListDao.observeAll().map { it.map { e -> e.toDomain() } }

    override suspend fun upsert(list: TaskList): Long {
        val rowId = taskListDao.upsert(list.toEntity())
        return if (list.id == 0L) rowId else list.id
    }

    override suspend fun delete(list: TaskList) {
        // «Входящие» — системный список, его не удаляем
        if (list.isInbox) return
        db.withTransaction {
            // Задачи удалённого списка не пропадают, а переезжают во «Входящие»
            db.taskDao().moveToList(list.id, inboxId())
            taskListDao.delete(list.toEntity())
        }
    }

    /**
     * id служебного списка «Без списка» (в коде — inbox). Это СПИСОК, а не задача.
     *
     * Список создаётся при первом обращении, поэтому заранее заводить его не нужно:
     * - dao.inboxId() ищет его в базе и возвращает id или null, если его ещё нет;
     * - `?:` («элвис») — «если слева null, сделай то, что справа»: создаём список
     *   и возвращаем его новый id (upsert новой строки возвращает её id).
     *
     * Название в базе пустое: на экране оно берётся из ресурсов по флагу isInbox
     * («Без списка» / «Без спіса» / «No list»), чтобы менялось вместе с языком телефона.
     *
     * withTransaction: «найти, а если нет — создать» — это две операции. Если два места
     * спросят inboxId одновременно (при первом запуске так и бывает), без транзакции оба
     * могли бы не найти список и создать два. Транзакция выполняет их по очереди.
     */
    override suspend fun inboxId(): Long = db.withTransaction {
        taskListDao.inboxId() ?: taskListDao.upsert(TaskList(name = "", isInbox = true).toEntity())
    }
}
