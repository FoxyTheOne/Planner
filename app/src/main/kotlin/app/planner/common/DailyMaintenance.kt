package app.planner.common

import app.planner.R
import app.planner.core.domain.model.OverdueMode
import app.planner.core.domain.repository.ISettingsRepository
import app.planner.core.domain.repository.ITaskRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * «Утренняя уборка» — выполняется один раз в день, при запуске приложения или в полночь.
 *
 * 1. Задачи без списка попадают в «Без списка», чтобы ничего не потерялось.
 * 2. Хвосты, забытые дольше срока из настроек (по умолчанию 7 дней), тихо возвращаются
 *    в свои списки без даты — об этом появится одно короткое сообщение.
 * 3. Если в настройках включён автоперенос — оставшееся несделанное переезжает на сегодня.
 * 4. Ко дням «раз в год» с подготовкой появляются задачи-подготовки (см. [PrepTasks]).
 *
 * Запускает её [app.planner.MainViewModel].
 */
@Singleton
class DailyMaintenance @Inject constructor(
    private val tasks: ITaskRepository,
    private val settings: ISettingsRepository,
    private val messages: MessageBus,
    private val prepTasks: PrepTasks,
) {
    /*
     * День последнего запуска — чтобы не делать уборку дважды за один день, пока приложение живо.
     *
     * Это поле живёт в памяти, а не на диске. DailyMaintenance — @Singleton, то есть один объект
     * на всё время, пока жив процесс приложения. Когда процесс закрывается (человек смахнул
     * приложение, система освободила память, перезагрузка телефона), объект пропадает,
     * и при следующем запуске lastRun снова null — уборка выполнится ещё раз.
     *
     * Это нормально и задумано так: уборку безопасно повторить. Она только возвращает
     * «забытые» задачи в списки, и повторный запуск в тот же день ничего лишнего не сделает —
     * нужные задачи уже вернулись. lastRun лишь экономит лишнюю работу, пока приложение открыто.
     */
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

        // Дни «раз в год», к которым пора готовиться, — задачи-подготовки появляются в плане
        prepTasks.create(today)
    }
}