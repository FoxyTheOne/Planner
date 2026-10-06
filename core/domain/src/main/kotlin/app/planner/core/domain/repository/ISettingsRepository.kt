package app.planner.core.domain.repository

import app.planner.core.domain.model.PlannerSettings
import kotlinx.coroutines.flow.Flow

/**
 * Доступ к настройкам. Реализация — `DataStoreSettingsRepository` в модуле `data`.
 */
interface ISettingsRepository {
    /** Текущие настройки; присылает новое значение при каждом изменении. */
    val settings: Flow<PlannerSettings>

    /**
     * Изменить настройки: [transform] получает текущие и возвращает новые, например
     * `update { it.copy(themeMode = ThemeMode.DARK) }`.
     */
    suspend fun update(transform: (PlannerSettings) -> PlannerSettings)
}
