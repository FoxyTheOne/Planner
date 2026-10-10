package app.planner.common

import app.planner.R
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import app.planner.core.domain.recurrence.Occurrence
import app.planner.core.domain.recurrence.Recurrence
import app.planner.core.domain.repository.IRecurrenceRepository
import app.planner.core.domain.repository.ITaskRepository
import app.planner.core.domain.usecase.CreateListUseCase
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
    private val rules: IRecurrenceRepository,
    private val createList: CreateListUseCase,
    private val messages: MessageBus,
    private val todayClock: TodayClock,
) {
    suspend fun toggleDone(task: Task) {
        if (task.isOccurrence && task.isDone) {
            // Сняли отметку с дня повторения — удаляем задачу, и на экране снова «виртуальная» строка
            // по правилу. Так, если правило потом поменяют (новое название, время), день подхватит изменения
            tasks.delete(task)
        } else {
            tasks.setDone(task.id, !task.isDone)
        }
    }

    /**
     * Отметить день повторения сделанным: он превращается в задачу (см. Recurrence.toTask),
     * и «виртуальная» строка по правилу на этот день больше не показывается.
     */
    suspend fun completeOccurrence(occurrence: Occurrence) {
        tasks.addOrUpdate(Recurrence.toTask(occurrence, TaskStatus.DONE))
    }

    /** Создать новую задачу (id = 0) без нового списка — например, из шторки недели. */
    suspend fun create(task: Task): Long = tasks.addOrUpdate(task)

    /**
     * Сохранить из редактора. Если в редакторе придумали новый список ([newListName]),
     * сначала создаём список (следующим цветом палитры), потом кладём в него задачу или повторение.
     * Возвращает id списка.
     *
     * Редактор один для задач и повторений, поэтому вариантов три:
     * - выбрано повторение ([repeat]) — сохраняем ПРАВИЛО (новое или правку старого), а не задачу;
     * - открыли повторение, а выбрали «Не повторять» — правило удаляем, остаётся одна обычная задача;
     * - обычная задача — как раньше.
     *
     * «Черновик правила» — задача с id = 0 и ruleId (см. Recurrence.draftFromRule): в базе такой
     * задачи нет, это правило, открытое в редакторе. Отмеченный день повторения — другое: у него
     * есть и id, и ruleId, и правится он как обычная задача.
     */
    suspend fun save(task: Task, newListName: String?, repeat: Repeat? = null): Long? {
        // Правило «какой цвет и где встаёт новый список» — в CreateListUseCase (domain)
        val listId = if (newListName.isNullOrBlank()) task.listId else createList(newListName)
        val saved = task.copy(listId = listId)
        val ruleDraftId = task.ruleId.takeIf { task.id == 0L }
        when {
            repeat != null -> {
                val existing = ruleDraftId?.let { rules.getRule(it) }
                rules.save(Recurrence.ruleFromDraft(saved, repeat, todayClock.today(), existing))
                // Обычная задача из базы стала повторяющейся — сама по себе она больше не нужна
                if (task.id != 0L && !task.isOccurrence) tasks.delete(task)
            }
            ruleDraftId != null -> {
                rules.getRule(ruleDraftId)?.let { rules.delete(it) }
                tasks.addOrUpdate(saved.copy(ruleId = null, date = saved.date ?: todayClock.today()))
            }
            else -> tasks.addOrUpdate(saved)
        }
        return listId
    }

    suspend fun schedule(task: Task, slot: Slot) {
        tasks.schedule(task.id, slot)
        messages.post(UiMessage(scheduledText(slot), undo = { tasks.addOrUpdate(task) }))
    }

    suspend fun moveToTomorrow(task: Task) {
        val tomorrow = todayClock.today().plusDays(1)
        tasks.schedule(task.id, Slot(tomorrow, task.partOfDay, task.time))
        messages.post(
            UiMessage(
                UiText.Res(R.string.msg_moved_tomorrow),
                undo = { tasks.addOrUpdate(task) })
        )
    }

    suspend fun returnToList(task: Task) {
        tasks.unschedule(task.id)
        messages.post(
            UiMessage(
                UiText.Res(R.string.msg_returned_to_list),
                undo = { tasks.addOrUpdate(task) })
        )
    }

    suspend fun delete(task: Task) {
        // В редакторе было открыто повторение (черновик правила) — удаляем правило
        // val ruleId — локальная копия: для свойства из другого модуля (domain) Kotlin не делает smart cast
        val ruleId = task.ruleId
        if (task.id == 0L && ruleId != null) {
            deleteRule(ruleId)
            return
        }
        tasks.delete(task)
        messages.post(
            UiMessage(
                UiText.Res(R.string.msg_deleted),
                undo = { tasks.addOrUpdate(task) })
        )
    }

    /**
     * Удалить правило повторения. Уже отмеченные дни остаются в истории обычными задачами
     * (внешний ключ SET_NULL). «Отменить» возвращает правило; связь отмеченных дней с ним
     * при этом не восстанавливается — это редкий случай, и данные не теряются.
     */
    private suspend fun deleteRule(ruleId: Long) {
        val rule = rules.getRule(ruleId) ?: return
        rules.delete(rule)
        messages.post(UiMessage(UiText.Res(R.string.msg_repeat_deleted), undo = { rules.save(rule) }))
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
