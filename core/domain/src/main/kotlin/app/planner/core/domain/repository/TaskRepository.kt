package app.planner.core.domain.repository

import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Что приложение умеет делать с задачами — без подробностей, *где* они хранятся.
 *
 * Это интерфейс («договор»). Реализация — `RoomTaskRepository` в модуле `data`.
 * Экраны зависят только от интерфейса, поэтому хранилище можно заменить
 * (например, на тестовое в памяти), не трогая экраны.
 *
 * `Flow` — поток значений, который присылает новый список при каждом изменении данных.
 * `suspend` — функция, которая может «подождать» (работа с диском), не блокируя экран.
 */
interface TaskRepository {
    /** Задачи с датой в промежутке [from]..[to] включительно — для «Сегодня» и «Недели». */
    fun observeTasksBetween(from: LocalDate, to: LocalDate): Flow<List<Task>>

    /** Хвосты: открытые задачи с датой раньше [today]. */
    fun observeOverdue(today: LocalDate): Flow<List<Task>>

    /** Задачи списка, включая запланированные (они показываются серыми с датой) и ещё не сделанные. */
    fun observeListTasks(listId: Long): Flow<List<Task>>

    /** Все открытые задачи без даты — для шторки «Из списков». */
    fun observeUnscheduled(): Flow<List<Task>>

    /** Все открытые задачи — для поиска в оглавлении списков. */
    fun observeOpen(): Flow<List<Task>>

    /** Сколько открытых задач в каждом списке: id списка → число. Для счётчиков в оглавлении. */
    fun observeOpenCountsByList(): Flow<Map<Long, Int>>

    suspend fun getTask(id: Long): Task?

    /** Создать (id = 0) или сохранить задачу. Задача без списка попадает во «Входящие». Возвращает id. */
    suspend fun upsert(task: Task): Long
    suspend fun delete(task: Task)
    suspend fun setDone(id: Long, done: Boolean)

    /** Поставить задачу в план: день, часть дня или время. */
    suspend fun schedule(id: Long, slot: Slot)

    /** Убрать дату — задача возвращается в свой список. */
    suspend fun unschedule(id: Long)

    /** Возвращает задачи, провисевшие в хвостах дольше срока; результат — сколько вернулось. */
    suspend fun returnStaleOverdue(today: LocalDate, returnAfterDays: Int): Int

    /** «Всё на сегодня»: все хвосты переезжают на [target]. */
    suspend fun moveOverdueToDate(today: LocalDate, target: LocalDate)

    /** «Всё в списки»: хвосты теряют дату и возвращаются в свои списки. */
    suspend fun returnAllOverdueToLists(today: LocalDate)

    /** Задачи без списка переселяются во «Входящие», чтобы ничего не потерялось. */
    suspend fun moveOrphansToInbox()
}

/**
 * Что приложение умеет делать со списками «Когда-нибудь».
 * Реализация — `RoomTaskListRepository` в модуле `data`.
 */
interface TaskListRepository {
    /** Все списки; «Входящие» — первыми. */
    fun observeLists(): Flow<List<TaskList>>
    suspend fun upsert(list: TaskList): Long

    /** Удалить список; его задачи переедут во «Входящие». Сами «Входящие» не удаляются. */
    suspend fun delete(list: TaskList)

    /** id системного списка «Входящие» (создаётся при первом обращении). */
    suspend fun inboxId(): Long
}
