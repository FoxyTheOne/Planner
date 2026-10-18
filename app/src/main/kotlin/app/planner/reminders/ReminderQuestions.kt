package app.planner.reminders

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Вопрос «Задачу перенесли — что сделать с напоминанием?».
 *
 * @param taskId о какой задаче речь — ответ запишется в неё.
 * @param title её название — чтобы в окне было понятно, о чём спрашиваем.
 * @param reminderTime на сколько сейчас стоит напоминание («в 9:00»).
 * @param taskTime новое точное время задачи, если оно есть, — от него окно предложит «за 30 минут до».
 */
data class ReminderQuestion(
    val taskId: Long,
    val title: String,
    val reminderTime: LocalTime,
    val taskTime: LocalTime?,
)

/**
 * Общая «почта» для таких вопросов — как MessageBus для сообщений «Отменить».
 *
 * Задачу переносят из многих мест: редактор, «Запланировать», шторка «+», перетаскивание в неделе
 * и в дне. Все они идут через TaskCommands, а он про окна ничего не знает (и не должен:
 * команды не рисуют интерфейс). Поэтому он просто кладёт вопрос сюда, а каркас приложения
 * (PlannerApp) один на всех показывает окно. Так вопрос выглядит одинаково, откуда бы ни переносили.
 */
@Singleton
class ReminderQuestions @Inject constructor() {
    // Те же настройки очереди, что у MessageBus: событие, а не состояние; свежее важнее старого
    private val _questions = MutableSharedFlow<ReminderQuestion>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val questions: SharedFlow<ReminderQuestion> = _questions

    fun ask(question: ReminderQuestion) {
        _questions.tryEmit(question)
    }
}
