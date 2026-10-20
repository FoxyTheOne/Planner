package app.planner.core.domain.model

import java.time.LocalDate
import java.time.LocalTime

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Какая тёмная тема выбрана: тёплый графит, чернильная или чёрная (AMOLED). */
enum class DarkVariant { GRAPHITE, INK, BLACK }

/** Что делать с несделанным: утренний баннер с выбором или автоперенос на сегодня. */
enum class OverdueMode { BANNER, AUTO_MOVE_TO_TODAY }

/**
 * Все настройки приложения одним объектом. Значения по умолчанию — те, с которыми
 * приложение открывается впервые. Хранятся в DataStore (см. `DataStoreSettingsRepository`).
 */
data class PlannerSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val darkVariant: DarkVariant = DarkVariant.GRAPHITE,
    val systemColors: Boolean = false,
    val overdueMode: OverdueMode = OverdueMode.BANNER,
    val returnAfterDays: Int = 7,
    val dayStart: LocalTime = LocalTime.of(12, 0),
    val eveningStart: LocalTime = LocalTime.of(18, 0),
    /** «Не сейчас» в баннере незавершённого прячет его до конца этого дня. */
    val overdueBannerHiddenOn: LocalDate? = null,
    /** Последний открытый список — при следующем запуске приложение вернётся в него. */
    val lastListId: Long? = null,
    /**
     * Уже предлагали включить «точное время» (окно после первого напоминания). Предлагаем один раз:
     * ответили «Разрешить» или «Не сейчас» — больше не спрашиваем, дальше — страница «Надёжность напоминаний».
     */
    val exactAlarmOfferShown: Boolean = false,
    /**
     * Звучат ли напоминания о делах (задачи и повторения). Без звука они тихо появляются в шторке.
     * Звук выбирают здесь, в приложении, а не в настройках Android: там на каждом телефоне
     * всё устроено по-своему, и выключить звук бывает непросто (см. ReminderNotifications).
     */
    val tasksSound: Boolean = true,
    /** Звучат ли напоминания о привычках. По умолчанию нет: привычка — мягкий повод, а не срочное дело. */
    val habitsSound: Boolean = false,
) {
    /** В какую часть дня попадает задача с точным временем. */
    fun partOfDayFor(time: LocalTime): PartOfDay = when {
        time < dayStart -> PartOfDay.MORNING
        time < eveningStart -> PartOfDay.DAY
        else -> PartOfDay.EVENING
    }
}
