package app.planner.core.domain.reminder

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Напоминание отложили кнопкой «Отложить на 10 минут» в уведомлении.
 *
 * @param original когда напоминание должно было сработать по задаче (Reminders.fireAt) в момент,
 *   когда его отложили. По нему видно, что задачу с тех пор не меняли: поменяли время задачи
 *   или само напоминание — у задачи будет другой fireAt, и отложенное больше ни к чему.
 * @param until когда напомнить снова.
 * @param date только у повторения: какой его день отложили (у задачи день один — её собственный).
 */
data class Snooze(
    val original: LocalDateTime,
    val until: LocalDateTime,
    val date: LocalDate? = null
)
