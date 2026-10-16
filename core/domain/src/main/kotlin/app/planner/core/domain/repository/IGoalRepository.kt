package app.planner.core.domain.repository

import app.planner.core.domain.model.Goal
import app.planner.core.domain.model.GoalStage
import kotlinx.coroutines.flow.Flow

/**
 * Что приложение умеет делать с целями и их этапами.
 * Реализация — `RoomGoalRepository` в модуле `data`.
 *
 * Шагов целей здесь нет: шаги — это задачи, они в [ITaskRepository]
 * (`observeGoalSteps`, `observeAllGoalSteps`).
 */
interface IGoalRepository {
    /** Все цели: сначала те, что в работе, потом достигнутые. Обновляется само. */
    fun observeGoals(): Flow<List<Goal>>

    /** Одна цель; null — её нет (например, удалили, пока экран был открыт). */
    fun observeGoal(id: Long): Flow<Goal?>

    suspend fun getGoal(id: Long): Goal?

    /** Сохранить новую (id = 0) или изменённую цель. Возвращает её id. */
    suspend fun save(goal: Goal): Long

    /** Удалить цель. Её этапы удаляются вместе с ней; что делать с шагами — решает вызывающий. */
    suspend fun delete(goal: Goal)

    /** Этапы одной цели по порядку. */
    fun observeStages(goalId: Long): Flow<List<GoalStage>>

    /** Этапы всех целей — для карточек: какой шаг «следующий», зависит от порядка этапов. */
    fun observeAllStages(): Flow<List<GoalStage>>

    /** Сохранить новый (id = 0) или изменённый этап. Возвращает его id. */
    suspend fun saveStage(stage: GoalStage): Long

    /** Удалить этап. Его шаги остаются в цели, просто без этапа. */
    suspend fun deleteStage(stage: GoalStage)
}
