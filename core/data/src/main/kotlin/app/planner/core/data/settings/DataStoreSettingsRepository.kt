package app.planner.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.planner.core.domain.model.DarkVariant
import app.planner.core.domain.model.OverdueMode
import app.planner.core.domain.model.PlannerSettings
import app.planner.core.domain.model.ThemeMode
import app.planner.core.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

// Файл настроек на телефоне (files/datastore/settings.preferences_pb).
// `by preferencesDataStore` гарантирует, что на всё приложение будет один такой объект.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Хранит [PlannerSettings] в DataStore — современной замене SharedPreferences.
 *
 * DataStore хранит пары «ключ → значение» (строки, числа, флаги). Поэтому здесь
 * два перевода: объект настроек раскладывается по ключам при записи и собирается обратно при чтении.
 * Перечисления (enum) хранятся по имени, время — в минутах от полуночи, дата — днём от 1970 года.
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val darkVariant = stringPreferencesKey("dark_variant")
        val systemColors = booleanPreferencesKey("system_colors")
        val overdueMode = stringPreferencesKey("overdue_mode")
        val returnAfterDays = intPreferencesKey("return_after_days")
        val dayStart = intPreferencesKey("day_start_minutes")
        val eveningStart = intPreferencesKey("evening_start_minutes")
        val bannerHiddenOn = longPreferencesKey("overdue_banner_hidden_on")
        val lastListId = longPreferencesKey("last_list_id")
    }

    override val settings: Flow<PlannerSettings> = context.dataStore.data.map { it.toSettings() }

    override suspend fun update(transform: (PlannerSettings) -> PlannerSettings) {
        context.dataStore.edit { prefs ->
            val new = transform(prefs.toSettings())
            prefs[Keys.themeMode] = new.themeMode.name
            prefs[Keys.darkVariant] = new.darkVariant.name
            prefs[Keys.systemColors] = new.systemColors
            prefs[Keys.overdueMode] = new.overdueMode.name
            prefs[Keys.returnAfterDays] = new.returnAfterDays
            prefs[Keys.dayStart] = new.dayStart.toMinutes()
            prefs[Keys.eveningStart] = new.eveningStart.toMinutes()
            val hidden = new.overdueBannerHiddenOn
            if (hidden != null) prefs[Keys.bannerHiddenOn] = hidden.toEpochDay() else prefs.remove(Keys.bannerHiddenOn)
            val lastList = new.lastListId
            if (lastList != null) prefs[Keys.lastListId] = lastList else prefs.remove(Keys.lastListId)
        }
    }

    private fun Preferences.toSettings(): PlannerSettings {
        val defaults = PlannerSettings()
        return PlannerSettings(
            themeMode = enumOr(this[Keys.themeMode], defaults.themeMode),
            darkVariant = enumOr(this[Keys.darkVariant], defaults.darkVariant),
            systemColors = this[Keys.systemColors] ?: defaults.systemColors,
            overdueMode = enumOr(this[Keys.overdueMode], defaults.overdueMode),
            returnAfterDays = this[Keys.returnAfterDays] ?: defaults.returnAfterDays,
            dayStart = this[Keys.dayStart]?.let(::minutesToTime) ?: defaults.dayStart,
            eveningStart = this[Keys.eveningStart]?.let(::minutesToTime) ?: defaults.eveningStart,
            overdueBannerHiddenOn = this[Keys.bannerHiddenOn]?.let(LocalDate::ofEpochDay),
            lastListId = this[Keys.lastListId],
        )
    }

    // Неизвестное значение (например, после отката версии) не должно ронять приложение
    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private fun LocalTime.toMinutes() = hour * 60 + minute
    private fun minutesToTime(minutes: Int) = LocalTime.of(minutes / 60, minutes % 60)
}
