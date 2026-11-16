package app.planner.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import app.planner.common.TodayClock
import app.planner.core.domain.repository.IMedicationRepository
import app.planner.core.domain.repository.IRecurrenceRepository
import app.planner.core.domain.repository.ISettingsRepository
import app.planner.core.domain.repository.ITaskRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Держит виджет «Сегодня» свежим: подписывается на всё, что он показывает (задачи сегодня и завтра,
 * повторения, лекарства, настройки), и при любом изменении перерисовывает виджеты.
 * Идея та же, что у ReminderSync: не «помнить, где обновить виджет», а подписаться на данные.
 * Отметили задачу на экране, в уведомлении или на самом виджете — база пришлёт новые данные, и виджет обновится.
 *
 * Работает, пока жив процесс приложения (запускается в PlannerApplication). Когда процесса нет,
 * виджет обновляет сама система — раз в полчаса (updatePeriodMillis), этого хватает, чтобы
 * перейти на новый день и вечером показать завтра.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tasks: ITaskRepository,
    private val rules: IRecurrenceRepository,
    private val medications: IMedicationRepository,
    private val settings: ISettingsRepository,
    private val todayClock: TodayClock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Подписаться на данные. Вызывается один раз, при запуске приложения.
     *
     * debounce(500 мс) — «подождать, пока изменения улягутся»: одно действие часто меняет несколько
     * таблиц подряд (отметили день повторения — новая задача, будильник, отметка), и перерисовывать
     * виджет пять раз за долю секунды незачем. flatMapLatest по дню — в полночь подписка переезжает
     * на новые «сегодня» и «завтра».
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start() {
        scope.launch {
            todayClock.todayFlow
                .flatMapLatest { today ->
                    val tomorrow = today.plusDays(1)
                    combine(
                        tasks.observeTasksBetween(today, tomorrow),
                        tasks.observeOccurrenceTasks(today, tomorrow),
                        rules.observeRules(),
                        medications.observeMedications(),
                        medications.observeTaken(today, tomorrow),
                    ) { _, _, _, _, _ -> today }
                }
                .combine(settings.settings) { today, _ -> today }
                .debounce(500)
                // Виджет — не главное: если с ним что-то не так, приложение не должно падать
                .catch { Log.e(TAG, "Не удалось обновить виджет", it) }
                .collect { updateAll() }
        }
    }

    /** Перерисовать все виджеты «Сегодня» — если они вообще стоят на рабочем столе. */
    suspend fun updateAll() {
        if (GlanceAppWidgetManager(context).getGlanceIds(PlannerWidget::class.java).isNotEmpty()) {
            PlannerWidget().updateAll(context)
        }
    }

    private companion object {
        const val TAG = "WidgetUpdater"
    }
}
