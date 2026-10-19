package app.planner.common

import app.planner.R
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Reminder
import app.planner.core.domain.model.Slot
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import app.planner.core.domain.recurrence.Occurrence
import app.planner.core.domain.recurrence.Recurrence
import app.planner.core.domain.reminder.Reminders
import app.planner.core.domain.repository.IRecurrenceRepository
import app.planner.core.domain.repository.ITaskRepository
import app.planner.core.domain.usecase.CreateListUseCase
import app.planner.reminders.ReminderQuestion
import app.planner.reminders.ReminderQuestions
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
    private val reminderQuestions: ReminderQuestions,
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

    /** Создать новую задачу (id = 0) без нового списка — например, из шторки недели. */
    suspend fun create(task: Task): Long = tasks.addOrUpdate(task)

    /**
     * Сохранить из редактора. Если в редакторе придумали новый список (newListName),
     * сначала создаём список (следующим цветом палитры), потом кладём в него задачу или повторение.
     * Возвращает id списка.
     *
     * Редактор один для задач и повторений, поэтому вариантов три:
     * - выбрано повторение (repeat) — сохраняем ПРАВИЛО (новое или правку старого), а не задачу.
     *   Привычка это или дело и какой цели она помогает — тоже из редактора (isHabit, habitGoalId);
     * - открыли повторение, а выбрали «Не повторять» — правило удаляем, остаётся одна обычная задача;
     * - обычная задача — как раньше.
     *
     * «Черновик правила» — задача с id = 0 и ruleId (см. Recurrence.draftFromRule): в базе такой
     * задачи нет, это правило, открытое в редакторе. Отмеченный день повторения — другое: у него
     * есть и id, и ruleId, и правится он как обычная задача.
     */
    suspend fun save(result: EditorResult): Long? {
        // Разложить объект на переменные: дальше код читается так же, как с отдельными параметрами
        val (task, newListName, repeat, isHabit, habitGoalId) = result
        // Правило «какой цвет и где встаёт новый список» — в CreateListUseCase (domain)
        val listId = if (newListName.isNullOrBlank()) task.listId else createList(newListName)
        val saved = task.copy(listId = listId)
        val ruleDraftId = task.ruleId.takeIf { task.isRuleDraft }
        when {
            repeat != null -> {
                val existing = ruleDraftId?.let { rules.getRule(it) }
                val rule =
                    Recurrence.ruleFromDraft(
                        saved,
                        repeat,
                        todayClock.today(),
                        existing,
                        isHabit,
                        habitGoalId
                    )
                rules.save(rule)
                // Правило поменяли — его перенесённые, ещё не сделанные дни получают новое название и список
                // (дата и время остаются: это перенос). Сделанные дни не трогаем — это история
                if (existing != null) {
                    tasks.getOccurrenceTasks(rule.id)
                        .filter { it.status == TaskStatus.OPEN }
                        .forEach { tasks.addOrUpdate(Recurrence.syncInstance(it, rule)) }
                }
                // Обычная задача из базы стала повторяющейся — сама по себе она больше не нужна
                if (task.id != 0L && !task.isOccurrence) tasks.delete(task)
            }

            ruleDraftId != null -> {
                rules.getRule(ruleDraftId)?.let { removeRule(it) }
                tasks.addOrUpdate(
                    saved.copy(
                        ruleId = null,
                        date = saved.date ?: todayClock.today()
                    )
                )
            }

            else -> {
                // Какой задача была до правки — чтобы понять, не поменялось ли время дня (см. askAboutReminder)
                val before = task.id.takeIf { it != 0L }?.let { tasks.getTask(it) }
                tasks.addOrUpdate(saved)
                before?.let { askAboutReminder(it, saved) }
            }
        }
        return listId
    }

    /**
     * Поставить задачу в план.
     *
     * @param reminder напоминание после переноса. Из шторки «Запланировать» приходит то, что там
     *   выбрали (шторка уже подогнала его под новое время). Остальные пути — перетаскивание, шторка «+» —
     *   его не передают, и напоминание остаётся как было. «Отменить» вернёт и прежнее напоминание:
     *   в undo кладём задачу целиком.
     */
    suspend fun schedule(task: Task, slot: Slot, reminder: Reminder? = task.reminder) {
        if (task.isOccurrenceDraft) return moveOccurrence(task, slot, scheduledText(slot))
        tasks.schedule(task.id, slot)
        if (reminder != task.reminder) setReminder(task.id, reminder)
        messages.post(UiMessage(scheduledText(slot), undo = { tasks.addOrUpdate(task) }))
        // Напоминание поменяли в шторке — человек уже решил, askAfterMove ничего не спросит
        askAboutReminder(
            task,
            task.copy(
                date = slot.date,
                partOfDay = slot.partOfDay,
                time = slot.time,
                reminder = reminder
            )
        )
    }

    /** Ответ на вопрос про напоминание: новое напоминание или null — «убрать напоминание». */
    suspend fun setReminder(taskId: Long, reminder: Reminder?) {
        tasks.getTask(taskId)?.let { tasks.addOrUpdate(it.copy(reminder = reminder)) }
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

    suspend fun returnToList(task: Task) {
        tasks.unschedule(task.id)
        messages.post(
            UiMessage(
                // Шаг цели вернулся в свою цель, а не в список (см. Overdue.returnedToList)
                UiText.Res(if (task.isGoalStep) R.string.msg_returned_to_goal else R.string.msg_returned_to_list),
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

    /**
     * После переноса спросить про напоминание, если нужно (когда — решает домен: Reminders.askAfterMove).
     * Задача к этому моменту уже перенесена, а напоминание — пока «как было»: если окно просто закрыть,
     * оно так и останется. Сам вопрос показывает каркас приложения (см. ReminderQuestions).
     */
    private fun askAboutReminder(before: Task, after: Task) {
        if (!Reminders.askAfterMove(before, after)) return
        // askAfterMove уже проверил, что напоминание — «в такой-то час», поэтому приведение безопасно
        val at = (after.reminder as Reminder.AtTime).time
        reminderQuestions.ask(
            ReminderQuestion(
                after.id,
                after.title,
                reminderTime = at,
                taskTime = after.time
            )
        )
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

    /** Удалить правило повторения кнопкой «Удалить» — с «Отменить», которое вернёт всё как было. */
    private suspend fun deleteRule(ruleId: Long) {
        val rule = rules.getRule(ruleId) ?: return
        val days = removeRule(rule)
        messages.post(
            UiMessage(
                UiText.Res(R.string.msg_repeat_deleted),
                undo = {
                    rules.save(rule) // то же id, поэтому дни снова на него ссылаются
                    days.forEach { tasks.addOrUpdate(it) }
                },
            )
        )
    }

    /**
     * Убрать правило из базы. Что будет с его днями, ставшими задачами:
     * - у дела отмеченные дни остаются в истории обычными задачами (внешний ключ SET_NULL
     *   обнуляет им ruleId): «в среду полила цветы» — это правда, и её видно в «Неделе»;
     * - у привычки отметки удаляются. Сами по себе они ничего не значат, а без правила стали бы
     *   обычными сделанными задачами «Вода» — по одной в каждом прошедшем дне «Недели».
     *
     * Возвращает дни такими, какими они были ДО удаления (с ruleId), — чтобы «Отменить» мог
     * их вернуть. Записать их обратно (@Upsert по id) — и связь с правилом восстановится.
     */
    private suspend fun removeRule(rule: RecurrenceRule): List<Task> {
        val days = tasks.getOccurrenceTasks(rule.id)
        // Сначала отметки, потом правило: после удаления правила у них уже не будет ruleId
        if (rule.isHabit) tasks.deleteOccurrenceTasks(rule.id)
        rules.delete(rule)
        return days
    }

}
