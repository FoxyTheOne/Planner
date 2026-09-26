package app.planner.common

import app.planner.R
import app.planner.core.domain.model.OverdueMode
import app.planner.core.domain.repository.SettingsRepository
import app.planner.core.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * «Утренняя уборка» — выполняется один раз в день, при запуске приложения или в полночь.
 *
 * 1. Задачи без списка переселяются во «Входящие», чтобы ничего не потерялось.
 * 2. Хвосты, забытые дольше срока из настроек (по умолчанию 7 дней), тихо возвращаются
 *    в свои списки без даты — об этом появится одно короткое сообщение.
 * 3. Если в настройках включён автоперенос — оставшееся несделанное переезжает на сегодня.
 *
 * Запускает её [app.planner.navigation.AppViewModel].
 */
@Singleton
class DailyMaintenance @Inject constructor(
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val messages: MessageBus,
) {
    // День последнего запуска — чтобы не делать уборку дважды за день
    private var lastRun: LocalDate? = null

    suspend fun run(today: LocalDate) {
        if (lastRun == today) return
        lastRun = today

        tasks.moveOrphansToInbox()

        // settings — это Flow; first() берёт текущее значение и сразу отписывается
        val current = settings.settings.first()
        val returned = tasks.returnStaleOverdue(today, current.returnAfterDays)
        if (returned > 0) {
            messages.post(UiMessage(UiText.Plural(R.plurals.msg_returned_to_lists, returned)))
        }
        if (current.overdueMode == OverdueMode.AUTO_MOVE_TO_TODAY) {
            tasks.moveOverdueToDate(today, today)
        }
    }
}
