package app.planner.core.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.planner.core.data.db.PlannerDatabase
import app.planner.core.data.db.toEntity
import app.planner.core.domain.model.Goal
import app.planner.core.domain.model.GoalStage
import app.planner.core.domain.model.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Хранилище целей на настоящей базе (в памяти). Проверяем то, что живёт в SQL-запросах,
 * а не в Kotlin: порядок целей и какие задачи считаются шагами.
 */
@RunWith(AndroidJUnit4::class)
class RoomGoalRepositoryTest {

    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        PlannerDatabase::class.java,
    ).build()
    private val goals = RoomGoalRepository(db)

    @After
    fun close() = db.close()

    @Test
    fun goalsInProgressComeBeforeAchieved() = runBlocking {
        goals.save(Goal(title = "Испанский", position = 1))
        goals.save(Goal(title = "Бег 5 км", position = 0, achievedOn = LocalDate.of(2026, 9, 1)))
        goals.save(Goal(title = "Ремонт", position = 0))

        // Сначала в работе (по position), потом достигнутые — хотя у «Бега» position тоже 0
        assertEquals(listOf("Ремонт", "Испанский", "Бег 5 км"), goals.observeGoals().first().map { it.title })
    }

    @Test
    fun stepsAreTasksOfThisGoalOnly() = runBlocking {
        val repair = goals.save(Goal(title = "Ремонт"))
        val spanish = goals.save(Goal(title = "Испанский"))
        val stage = goals.saveStage(GoalStage(goalId = repair, title = "Демонтаж"))
        val tasks = db.taskDao()
        tasks.addOrUpdate(Task(title = "Снять плитку", goalId = repair, stageId = stage).toEntity())
        tasks.addOrUpdate(Task(title = "Учебник", goalId = spanish).toEntity())
        tasks.addOrUpdate(Task(title = "Купить лампу").toEntity()) // обычная задача, не шаг

        assertEquals(listOf("Снять плитку"), tasks.observeByGoal(repair).first().map { it.title })
        assertEquals(2, tasks.observeGoalSteps().first().size)
        assertEquals(listOf("Демонтаж"), goals.observeStages(repair).first().map { it.title })
    }
}
