package app.planner.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.planner.core.domain.repository.ISettingsRepository
import app.planner.reminders.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * ViewModel страницы «Надёжность напоминаний». Сами проверки (уведомления, точное время, фон)
 * делает экран через ReminderHealth — им нужен только Context. А вот поставить пробный
 * будильник может лишь [ReminderScheduler], и выбор звука хранится в настройках — их и даёт ViewModel.
 */
@HiltViewModel
class ReliabilityViewModel @Inject constructor(
    private val scheduler: ReminderScheduler,
    settingsRepository: ISettingsRepository,
) : ViewModel() {

    /**
     * Выбрано ли в настройках «напоминания о делах — со звуком». Пока настройки не прочитались —
     * true, как по умолчанию.
     */
    val tasksSound: StateFlow<Boolean> = settingsRepository.settings
        .map { it.tasksSound }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Поставить пробное напоминание через минуту. Возвращает, когда его ждать. */
    fun sendTest(): LocalDateTime {
        val at = LocalDateTime.now().plusMinutes(1)
        scheduler.scheduleTest(at)
        return at
    }
}
