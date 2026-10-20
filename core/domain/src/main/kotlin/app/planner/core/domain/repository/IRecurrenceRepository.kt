package app.planner.core.domain.repository

import app.planner.core.domain.model.RecurrenceRule
import kotlinx.coroutines.flow.Flow

/**
 * Что приложение умеет делать с правилами повторения.
 * Реализация — `RoomRecurrenceRepository` в модуле `data`.
 *
 * Здесь хранятся только сами правила. Какие дни на них выпадают, считает
 * движок `Recurrence` — в базу эти дни не записываются.
 */
interface IRecurrenceRepository {
    /** Все правила; список обновляется сам, когда правила меняются. */
    fun observeRules(): Flow<List<RecurrenceRule>>

    suspend fun getRule(id: Long): RecurrenceRule?

    /** Сохранить новое (id = 0) или изменённое правило. Возвращает его id. */
    suspend fun save(rule: RecurrenceRule): Long

    suspend fun delete(rule: RecurrenceRule)

    /**
     * Новый порядок правил: [ids] в том порядке, в каком их расставили (первое — наверху).
     * Правила, которых нет в [ids], не трогаем. Пишется весь список сразу, одной транзакцией.
     */
    suspend fun reorder(ids: List<Long>)
}
