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
import kotlinx.coroutines.flow.first
import java.time.LocalDate
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
        // Сняли отметку с дня повторения, который не переносили, — удаляем задачу, и на экране снова
        // «виртуальная» строка по правилу. Так, если правило потом поменяют (новое название, время), день
        // подхватит изменения. Перенесённый день (дата ≠ дню правила) не удаляем: иначе перенос потерялся бы
        if (task.isOccurrence && task.isDone && task.date == task.occurrenceDate) {
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
        val draft = Recurrence.toTask(occurrence, TaskStatus.DONE)
        tasks.addOrUpdate(withExistingId(draft))
    }

    /**
     * Если для этого дня правила задача уже есть — берём её id, чтобы обновить её, а не создать вторую.
     * Так бывает при быстром двойном нажатии: строка по правилу ещё не успела исчезнуть с экрана.
     */
    private suspend fun withExistingId(draft: Task): Task {
        val day = draft.occurrenceDate ?: return draft
        val existing =
            tasks.observeOccurrenceTasks(day, day).first().firstOrNull { it.ruleId == draft.ruleId }
        return if (existing != null) draft.copy(id = existing.id) else draft
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
        val ruleDraftId = task.ruleId.takeIf { task.isRuleDraft }
        when {
            repeat != null -> {
                val existing = ruleDraftId?.let { rules.getRule(it) }
                val rule = Recurrence.ruleFromDraft(saved, repeat, todayClock.today(), existing)
                rules.save(rule)
                // Правило поменяли — его перенесённые, ещё не сделанные дни получают новое название и список
                // (дата и время остаются: это перенос). Сделанные дни не трогаем — это история
                if (existing != null) {
                    tasks.getOpenOccurrenceTasks(rule.id)
                        .forEach { tasks.addOrUpdate(Recurrence.syncInstance(it, rule)) }
                }
                // Обычная задача из базы стала повторяющейся — сама по себе она больше не нужна
                if (task.id != 0L && !task.isOccurrence) tasks.delete(task)
            }

            ruleDraftId != null -> {
                rules.getRule(ruleDraftId)?.let { rules.delete(it) }
                tasks.addOrUpdate(
                    saved.copy(
                        ruleId = null,
                        date = saved.date ?: todayClock.today()
                    )
                )
            }

            else -> tasks.addOrUpdate(saved)
        }
        return listId
    }

    suspend fun schedule(task: Task, slot: Slot) {
        if (task.isOccurrenceDraft) return moveOccurrence(task, slot, scheduledText(slot))
        tasks.schedule(task.id, slot)
        messages.post(UiMessage(scheduledText(slot), undo = { tasks.addOrUpdate(task) }))
    }

    suspend fun moveToTomorrow(task: Task) {
        val tomorrow = Slot(todayClock.today().plusDays(1), task.partOfDay, task.time)
        if (task.isOccurrenceDraft) return moveOccurrence(
            task,
            tomorrow,
            UiText.Res(R.string.msg_moved_tomorrow)
        )
        tasks.schedule(task.id, tomorrow)
        messages.post(
            UiMessage(
                UiText.Res(R.string.msg_moved_tomorrow),
                undo = { tasks.addOrUpdate(task) })
        )
    }

    /**
     * «Пропустить этот раз»: день повторения помечается пропущенным (SKIPPED) и исчезает из плана.
     * Ничего не переносится; строка по правилу на этот день больше не появится. Пригодится и
     * статистике привычек: «пропущено» — это решение человека, а не «забыл».
     * Если день уже был задачей (его перенесли), пропущенным становится она.
     */
    suspend fun skipOccurrence(task: Task) {
        val skipped = withExistingId(task).copy(status = TaskStatus.SKIPPED)
        val id = tasks.addOrUpdate(skipped)
        // «Отменить»: перенесённый день возвращаем как был, а черновик просто удаляем — строка по правилу вернётся
        val undo: suspend () -> Unit =
            if (task.id != 0L) ({ tasks.addOrUpdate(task) }) else ({
                tasks.getTask(id)?.let { tasks.delete(it) }
            })
        messages.post(UiMessage(UiText.Res(R.string.msg_skipped), undo = undo))
    }

    /**
     * Перенести один день повторения: он становится задачей на новой дате, а на свой день
     * строка по правилу больше не выпадает — задача помнит его в occurrenceDate. Само правило не меняется.
     * «Отменить» удаляет эту задачу, и день повторения возвращается на место.
     */
    private suspend fun moveOccurrence(draft: Task, slot: Slot, message: UiText) {
        val id = tasks.addOrUpdate(
            withExistingId(draft).copy(
                date = slot.date,
                partOfDay = slot.partOfDay,
                time = slot.time
            )
        )
        messages.post(UiMessage(message, undo = { tasks.getTask(id)?.let { tasks.delete(it) } }))
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
        if (task.isRuleDraft && ruleId != null) {
            deleteRule(ruleId)
            return
        }
        // День повторения не удаляем, а пропускаем: иначе на его день снова появилась бы строка по правилу
        // (человек удалил «полив в четверг», а он вернулся в среду). Для него «удалить» = «этот раз не нужен»
        if (task.isOccurrenceDay) return skipOccurrence(task)
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
        messages.post(
            UiMessage(
                UiText.Res(R.string.msg_repeat_deleted),
                undo = { rules.save(rule) })
        )
    }

    /** Поставить правило на паузу с сегодняшнего дня по [until] (null — пока не нажмут «Продолжить»). */
    suspend fun pauseRule(ruleId: Long, until: LocalDate?) {
        val rule = rules.getRule(ruleId) ?: return
        rules.save(Recurrence.pause(rule, todayClock.today(), until))
        messages.post(UiMessage(UiText.Res(R.string.msg_paused), undo = { rules.save(rule) }))
    }

    /** «Продолжить»: снять паузу, которая идёт сейчас. */
    suspend fun resumeRule(ruleId: Long) {
        val rule = rules.getRule(ruleId) ?: return
        rules.save(Recurrence.resume(rule, todayClock.today()))
        messages.post(UiMessage(UiText.Res(R.string.msg_resumed), undo = { rules.save(rule) }))
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
