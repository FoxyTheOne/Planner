package app.planner.core.domain.repository

import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
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
interface ITaskRepository {
    /** Задачи с датой в промежутке [from]..[to] включительно — для «Сегодня» и «Недели». */
    fun observeTasksBetween(from: LocalDate, to: LocalDate): Flow<List<Task>>

    /**
     * Задачи, в которые превратились дни повторений с [from] по [to] (по дню правила,
     * а не по дате в плане). По ним движок понимает, какие дни уже отмечены.
     */
    fun observeOccurrenceTasks(from: LocalDate, to: LocalDate): Flow<List<Task>>

    /** Все дни правила [ruleId], ставшие задачами: сделанные, перенесённые, пропущенные. */
    suspend fun getOccurrenceTasks(ruleId: Long): List<Task>

    /** Удалить все дни правила [ruleId], ставшие задачами (отметки привычки, когда её удаляют). */
    suspend fun deleteOccurrenceTasks(ruleId: Long)

    /** Шаги одной цели — все: сделанные, открытые, запланированные. Порядок — см. orderedSteps. */
    fun observeGoalSteps(goalId: Long): Flow<List<Task>>

    /** Шаги всех целей — для карточек на экране «Цели» (прогресс и следующий шаг). */
    fun observeAllGoalSteps(): Flow<List<Task>>

    /** Хвосты: открытые задачи с датой раньше [today]. Дни повторений хвостами не бывают. */
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
    suspend fun addOrUpdate(task: Task): Long
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
