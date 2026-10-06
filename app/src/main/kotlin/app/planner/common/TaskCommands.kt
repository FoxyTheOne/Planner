package app.planner.common

import app.planner.R
import app.planner.core.domain.model.ListColor
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskList
import app.planner.core.domain.repository.ITaskListRepository
import app.planner.core.domain.repository.ITaskRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Действия с задачей, общие для всех экранов: сохранить, отметить, перенести, удалить.
 *
 * Все экраны (Сегодня, Неделя, День, Списки) вызывают одни и те же функции,
 * поэтому поведение и сообщения везде одинаковые.
 *
 * Всё, что меняет план, можно отменить: перед изменением запоминаем задачу целиком,
 * а в «Отменить» (кнопка в Snackbar) просто кладём её обратно.
 *
 * По сути это набор UseCase'ов в одном классе: каждая функция — один сценарий
 * («перенести на завтра» = записать дату + показать сообщение + дать отменить).
 * Живёт в модуле app, а не в domain, потому что работает с текстами (R.string) и Snackbar.
 *
 * Интерфейса нет намеренно: реализация одна, и в тестах подменять удобнее репозитории
 * (у них интерфейсы есть), а TaskCommands брать настоящий. Чистые правила (что считать
 * просроченным, когда следующий полив) кладём в domain, а сюда — только их вызов.
 */
@Singleton
class TaskCommands @Inject constructor(
    private val tasks: ITaskRepository,
    private val lists: ITaskListRepository,
    private val messages: MessageBus,
    private val todayClock: TodayClock,
) {
    suspend fun toggleDone(task: Task) {
        tasks.setDone(task.id, !task.isDone)
    }

    /** Создать новую задачу (id = 0) без нового списка — например, из шторки недели. */
    suspend fun create(task: Task): Long = tasks.addOrUpdate(task)

    /**
     * Сохранить задачу из редактора. Если в редакторе придумали новый список ([newListName]),
     * сначала создаём список (следующим цветом палитры), потом кладём задачу в него.
     * Возвращает id списка, в котором оказалась задача.
     */
    suspend fun save(task: Task, newListName: String?): Long? {
        val listId = if (newListName.isNullOrBlank()) {
            task.listId
        } else {
            val existing = lists.observeLists().first()
            val color = ListColor.entries[existing.count { !it.isInbox } % ListColor.entries.size]
            lists.upsert(TaskList(name = newListName, color = color, position = existing.size))
        }
        tasks.addOrUpdate(task.copy(listId = listId))
        return listId
    }

    suspend fun schedule(task: Task, slot: Slot) {
        tasks.schedule(task.id, slot)
        messages.post(UiMessage(scheduledText(slot), undo = { tasks.addOrUpdate(task) }))
    }

    suspend fun moveToTomorrow(task: Task) {
        val tomorrow = todayClock.today().plusDays(1)
        tasks.schedule(task.id, Slot(tomorrow, task.partOfDay, task.time))
        messages.post(UiMessage(UiText.Res(R.string.msg_moved_tomorrow), undo = { tasks.addOrUpdate(task) }))
    }

    suspend fun returnToList(task: Task) {
        tasks.unschedule(task.id)
        messages.post(UiMessage(UiText.Res(R.string.msg_returned_to_list), undo = { tasks.addOrUpdate(task) }))
    }

    suspend fun delete(task: Task) {
        tasks.delete(task)
        messages.post(UiMessage(UiText.Res(R.string.msg_deleted), undo = { tasks.addOrUpdate(task) }))
    }

    /** «Запланировано: завтра» или «Запланировано: Пт 25, 10:30». */
    fun scheduledText(slot: Slot): UiText {
        val today = todayClock.today()
        val day = when (slot.date) {
            today -> UiText.Res(R.string.day_today_lower)
            today.plusDays(1) -> UiText.Res(R.string.day_tomorrow_lower)
            else -> UiText.Raw(DateTexts.shortDay(slot.date))
        }
        return if (slot.time == null) {
            UiText.Res(R.string.msg_scheduled, listOf(day))
        } else {
            UiText.Res(R.string.msg_scheduled_at, listOf(day, DateTexts.time(slot.time!!)))
        }
    }
}
