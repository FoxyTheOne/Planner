package app.planner.core.data.repository

import app.planner.core.data.db.PlannerDatabase
import app.planner.core.data.db.toDomain
import app.planner.core.data.db.toEntity
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.repository.IRecurrenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранилище правил повторения поверх Room. Переводит доменные [RecurrenceRule]
 * в строки таблицы `recurrence_rules` и обратно (см. `RecurrenceEntities.kt`).
 */
@Singleton
class RoomRecurrenceRepository @Inject constructor(
    db: PlannerDatabase,
) : IRecurrenceRepository {

    private val recurrenceRuleDao = db.recurrenceRuleDao()

    override fun observeRules(): Flow<List<RecurrenceRule>> =
        recurrenceRuleDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getRule(id: Long): RecurrenceRule? = recurrenceRuleDao.get(id)?.toDomain()

    override suspend fun save(rule: RecurrenceRule): Long {
        // Как в RoomTaskRepository: у нового правила берём id, выданный базой
        val rowId = recurrenceRuleDao.upsert(rule.toEntity())
        return if (rule.id == 0L) rowId else rule.id
    }

    override suspend fun delete(rule: RecurrenceRule) = recurrenceRuleDao.delete(rule.id)
}
