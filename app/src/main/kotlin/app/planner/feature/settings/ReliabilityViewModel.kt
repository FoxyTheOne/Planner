package app.planner.feature.settings

import androidx.lifecycle.ViewModel
import app.planner.reminders.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * ViewModel страницы «Надёжность напоминаний». Сами проверки (уведомления, точное время, фон)
 * делает экран через ReminderHealth — им нужен только Context. А вот поставить пробный
 * будильник может лишь [ReminderScheduler], его и даёт ViewModel.
 */
@HiltViewModel
class ReliabilityViewModel @Inject constructor(
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    /** Поставить пробное напоминание через минуту. Возвращает, когда его ждать. */
    fun sendTest(): LocalDateTime {
        val at = LocalDateTime.now().plusMinutes(1)
        scheduler.scheduleTest(at)
        return at
    }
}
