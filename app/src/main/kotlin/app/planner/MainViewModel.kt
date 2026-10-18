package app.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.planner.common.DailyMaintenance
import app.planner.common.MessageBus
import app.planner.common.TaskCommands
import app.planner.common.TodayClock
import app.planner.common.UiMessage
import app.planner.core.domain.model.PlannerSettings
import app.planner.core.domain.model.Reminder
import app.planner.core.domain.repository.ISettingsRepository
import app.planner.reminders.ReminderQuestion
import app.planner.reminders.ReminderQuestions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel главного окна ([MainActivity]) — всё, что касается приложения целиком,
 * а не одного экрана:
 *
 * 1. **Настройки оформления** ([settings]) — тема нужна раньше любого экрана.
 * 2. **Ежедневная уборка** ([DailyMaintenance]) — при запуске и в полночь.
 * 3. **Сообщения внизу экрана** ([messages]) — «Перенесено на завтра · Отменить»
 *    с любого экрана показываются в одном общем месте (см. `PlannerApp`).
 * 4. **Вопросы про напоминания** ([reminderQuestions]) — «задачу перенесли, что сделать с напоминанием?»;
 *    так же одно окно на всё приложение, ответ записывает [answerReminder].
 *
 * Эта ViewModel живёт столько же, сколько окно приложения, и переживает поворот экрана.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: ISettingsRepository,
    messageBus: MessageBus,
    reminderQuestions: ReminderQuestions,
    private val commands: TaskCommands,
    maintenance: DailyMaintenance,
    todayClock: TodayClock,
) : ViewModel() {

    /**
     * Текущие настройки; null — ещё не прочитаны с диска (пока null, Activity держит заставку).
     *
     * SharingStarted.Eagerly — начать читать сразу при создании ViewModel, не дожидаясь,
     * пока экран подпишется: так заставка провисит как можно меньше.
     */
    val settings: StateFlow<PlannerSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Поток сообщений для Snackbar. Экраны кладут их в [MessageBus], показывает их `PlannerApp`. */
    val messages: SharedFlow<UiMessage> = messageBus.messages

    /** Вопросы «что сделать с напоминанием» после переноса задачи; показывает их `PlannerApp`. */
    val reminderQuestions: SharedFlow<ReminderQuestion> = reminderQuestions.questions

    init {
        // init выполняется один раз, когда ViewModel создана (при открытии приложения).
        //
        // todayFlow присылает «сегодня» при подписке и потом ещё раз — когда наступит новый день.
        // collect { ... } — «на каждое присланное значение выполни это». Поэтому уборка
        // запускается при открытии приложения и ещё раз в полночь, если приложение открыто.
        //
        // viewModelScope.launch — запустить это в фоне (в корутине), привязанной к ViewModel:
        // collect никогда не заканчивается сам (дни идут бесконечно), и без launch
        // init так и не завершился бы. Когда ViewModel уничтожается, корутина отменяется сама.
        viewModelScope.launch {
            todayClock.todayFlow.collect { today -> maintenance.run(today) }
        }
    }

    /**
     * Выполнить «Отменить» из Snackbar.
     *
     * [undo] — это `suspend`-функция: она пишет в базу и должна выполняться в корутине.
     * Сам Snackbar живёт в интерфейсе и может исчезнуть в любой момент, поэтому корутину
     * запускаем здесь, в viewModelScope: отмена доработает до конца, даже если экран уже сменился.
     */
    fun runUndo(undo: suspend () -> Unit) {
        viewModelScope.launch { undo() }
    }

    /** Ответ на вопрос про напоминание: новое напоминание или null — «убрать напоминание». */
    fun answerReminder(taskId: Long, reminder: Reminder?) {
        viewModelScope.launch { commands.setReminder(taskId, reminder) }
    }
}
