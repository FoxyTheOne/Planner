package app.planner.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Источник «сегодня» для всего приложения.
 *
 * Зачем отдельный класс, а не просто `LocalDate.now()` в каждом месте:
 * - [todayFlow] сам сообщит, когда наступил новый день, — экраны «Сегодня» и «Неделя»
 *   перестроятся в полночь, даже если приложение не закрывали;
 * - [clock] можно подменить в тестах и проверить, например, что будет 31 декабря.
 */
@Singleton
class TodayClock @Inject constructor() {
    var clock: Clock = Clock.systemDefaultZone()

    fun today(): LocalDate = LocalDate.now(clock)

    /**
     * Поток «какое сегодня число». `flow { }` — поток, который мы описываем сами:
     * раз в минуту проверяем дату и отправляем её (emit).
     * distinctUntilChanged — пропускать дальше только *изменившиеся* значения:
     * подписчики получат дату при подписке и потом — только когда она сменится (в полночь),
     * а не каждую минуту.
     */
    val todayFlow: Flow<LocalDate> = flow {
        while (true) {
            emit(today())
            delay(60_000) // ждём минуту, не блокируя приложение
        }
    }.distinctUntilChanged()
}
