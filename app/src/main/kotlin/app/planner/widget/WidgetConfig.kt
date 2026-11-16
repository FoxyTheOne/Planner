package app.planner.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Когда виджет показывает завтрашний день под сегодняшним. */
enum class TomorrowMode {
    /** Всегда: «Сегодня + завтра». */
    ALWAYS,

    /** Вечером — с того же часа, что и вечерняя сводка (по умолчанию 20:00). */
    EVENING,

    /** Никогда: только сегодня. */
    NEVER,
}

/**
 * Настройки одного виджета «Сегодня» — их выбирают в окне настройки (WidgetConfigActivity),
 * когда добавляют виджет на рабочий стол (а на Android 12+ — и позже, долгим нажатием на виджет).
 *
 * У каждого виджета свои: можно поставить два — один минималистичный, другой с лекарствами и привычками.
 * Хранятся в «состоянии» самого виджета: Glance держит для каждого виджета маленький файл настроек
 * (тот же DataStore Preferences, что и у настроек приложения), см. [read] и [writeTo].
 *
 * @param medications показывать приёмы лекарств (по умолчанию да).
 * @param habits показывать привычки (по умолчанию нет: на маленьком виджете они занимают место).
 */
data class WidgetConfig(
    val tomorrow: TomorrowMode = TomorrowMode.EVENING,
    val medications: Boolean = true,
    val habits: Boolean = false,
) {
    /** Записать в состояние виджета (см. updateAppWidgetState в WidgetConfigActivity). */
    fun writeTo(prefs: MutablePreferences) {
        prefs[KEY_TOMORROW] = tomorrow.name
        prefs[KEY_MEDICATIONS] = medications
        prefs[KEY_HABITS] = habits
    }

    companion object {
        private val KEY_TOMORROW = stringPreferencesKey("tomorrow")
        private val KEY_MEDICATIONS = booleanPreferencesKey("medications")
        private val KEY_HABITS = booleanPreferencesKey("habits")

        /** Прочитать из состояния виджета. Нет записи (виджет только что добавили) — значения по умолчанию. */
        fun read(prefs: Preferences): WidgetConfig {
            val defaults = WidgetConfig()
            return WidgetConfig(
                tomorrow = prefs[KEY_TOMORROW]?.let { runCatching { TomorrowMode.valueOf(it) }.getOrNull() }
                    ?: defaults.tomorrow,
                medications = prefs[KEY_MEDICATIONS] ?: defaults.medications,
                habits = prefs[KEY_HABITS] ?: defaults.habits,
            )
        }
    }
}
