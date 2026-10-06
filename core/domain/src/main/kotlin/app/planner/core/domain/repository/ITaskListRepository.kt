package app.planner.core.domain.repository

import app.planner.core.domain.model.TaskList
import kotlinx.coroutines.flow.Flow

/**
 * Что приложение умеет делать со списками «Когда-нибудь».
 * Реализация — `RoomTaskListRepository` в модуле `data`.
 */
interface ITaskListRepository {
    /** Все списки; «Входящие» — первыми. */
    fun observeLists(): Flow<List<TaskList>>
    suspend fun upsert(list: TaskList): Long

    /** Удалить список; его задачи переедут во «Входящие». Сами «Входящие» не удаляются. */
    suspend fun delete(list: TaskList)

    /** id системного списка «Входящие» (создаётся при первом обращении). */
    suspend fun inboxId(): Long
}