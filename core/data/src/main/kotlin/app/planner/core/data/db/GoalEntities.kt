package app.planner.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.planner.core.domain.model.Goal
import app.planner.core.domain.model.GoalStage
import java.time.Instant
import java.time.LocalDate

/**
 * Строка таблицы `goals` — цель (см. [Goal]). Шагов здесь нет: шаги — это строки таблицы
 * `tasks` с колонкой goalId (см. [TaskEntity]).
 */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val why: String,
    val note: String,
    val deadline: LocalDate?,
    val position: Int,
    val createdAt: Instant,
    val achievedOn: LocalDate?,
)

/**
 * Строка таблицы `goal_stages` — этап цели (см. [GoalStage]).
 *
 * `onDelete = CASCADE` — удалили цель, и её этапы удаляются вместе с ней: без цели
 * этап «Демонтаж» ничего не значит. (У шагов-задач по-другому, см. [TaskEntity].)
 */
@Entity(
    tableName = "goal_stages",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("goalId")],
)
data class GoalStageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val title: String,
    val position: Int,
)

fun GoalEntity.toDomain() = Goal(
    id = id, title = title, why = why, note = note, deadline = deadline,
    position = position, createdAt = createdAt, achievedOn = achievedOn,
)

fun Goal.toEntity() = GoalEntity(
    id = id, title = title, why = why, note = note, deadline = deadline,
    position = position, createdAt = createdAt, achievedOn = achievedOn,
)

fun GoalStageEntity.toDomain() = GoalStage(id = id, goalId = goalId, title = title, position = position)

fun GoalStage.toEntity() = GoalStageEntity(id = id, goalId = goalId, title = title, position = position)
