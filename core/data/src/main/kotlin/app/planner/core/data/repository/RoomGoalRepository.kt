package app.planner.core.data.repository

import app.planner.core.data.db.PlannerDatabase
import app.planner.core.data.db.toDomain
import app.planner.core.data.db.toEntity
import app.planner.core.domain.model.Goal
import app.planner.core.domain.model.GoalStage
import app.planner.core.domain.repository.IGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранилище целей и этапов поверх Room. Переводит доменные [Goal] и [GoalStage]
 * в строки таблиц `goals` и `goal_stages` и обратно (см. `GoalEntities.kt`).
 */
@Singleton
class RoomGoalRepository @Inject constructor(
    db: PlannerDatabase,
) : IGoalRepository {

    private val goalDao = db.goalDao()

    override fun observeGoals(): Flow<List<Goal>> =
        goalDao.observeGoals().map { rows -> rows.map { it.toDomain() } }

    // ?.toDomain(): строки может не быть (цель удалили) — тогда в поток уходит null
    override fun observeGoal(id: Long): Flow<Goal?> = goalDao.observeGoal(id).map { it?.toDomain() }

    override suspend fun getGoal(id: Long): Goal? = goalDao.getGoal(id)?.toDomain()

    override suspend fun save(goal: Goal): Long {
        // Как в остальных хранилищах: у новой цели берём id, выданный базой
        val rowId = goalDao.upsertGoal(goal.toEntity())
        return if (goal.id == 0L) rowId else goal.id
    }

    override suspend fun delete(goal: Goal) = goalDao.deleteGoal(goal.id)

    override fun observeStages(goalId: Long): Flow<List<GoalStage>> =
        goalDao.observeStages(goalId).map { rows -> rows.map { it.toDomain() } }

    override fun observeAllStages(): Flow<List<GoalStage>> =
        goalDao.observeAllStages().map { rows -> rows.map { it.toDomain() } }

    override suspend fun saveStage(stage: GoalStage): Long {
        val rowId = goalDao.upsertStage(stage.toEntity())
        return if (stage.id == 0L) rowId else stage.id
    }

    override suspend fun deleteStage(stage: GoalStage) = goalDao.deleteStage(stage.id)
}
