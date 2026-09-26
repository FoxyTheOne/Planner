package app.planner.core.data.repository

import androidx.room.withTransaction
import app.planner.core.data.db.PlannerDatabase
import app.planner.core.data.db.toDomain
import app.planner.core.data.db.toEntity
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.repository.TaskListRepository
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
) : TaskListRepository {

    private val dao = db.taskListDao()

    override fun observeLists(): Flow<List<TaskList>> = dao.observeAll().map { it.map { e -> e.toDomain() } }

    override suspend fun upsert(list: TaskList): Long {
        val rowId = dao.upsert(list.toEntity())
        return if (list.id == 0L) rowId else list.id
    }

    override suspend fun delete(list: TaskList) {
        // «Входящие» — системный список, его не удаляем
        if (list.isInbox) return
        db.withTransaction {
            // Задачи удалённого списка не пропадают, а переезжают во «Входящие»
            db.taskDao().moveToList(list.id, inboxId())
            dao.delete(list.toEntity())
        }
    }

    /**
     * id «Входящих». Список создаётся при первом обращении, поэтому заранее заводить его не нужно.
     * Название в базе пустое: на экране оно берётся из ресурсов по флагу isInbox,
     * чтобы менялось вместе с языком телефона.
     */
    override suspend fun inboxId(): Long =
        dao.inboxId() ?: dao.upsert(TaskList(name = "", isInbox = true).toEntity())
}
