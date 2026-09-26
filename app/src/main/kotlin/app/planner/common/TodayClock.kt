package app.planner.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** «Сегодня», которое само сменится в полночь, если приложение открыто. */
@Singleton
class TodayClock @Inject constructor() {
    var clock: Clock = Clock.systemDefaultZone()

    fun today(): LocalDate = LocalDate.now(clock)

    val todayFlow: Flow<LocalDate> = flow {
        while (true) {
            emit(today())
            delay(60_000)
        }
    }.distinctUntilChanged()
}
