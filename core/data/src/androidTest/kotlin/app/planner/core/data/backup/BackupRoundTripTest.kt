package app.planner.core.data.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.planner.core.data.db.GoalEntity
import app.planner.core.data.db.GoalStageEntity
import app.planner.core.data.db.PlannerDatabase
import app.planner.core.data.db.TaskEntity
import app.planner.core.data.db.TaskListEntity
import app.planner.core.data.db.toEntity
import app.planner.core.domain.model.ListColor
import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate

/**
 * Полный круг «экспорт → импорт» на настоящей базе: всё, что было в базе,
 * после восстановления из файла должно оказаться в другой базе ровно таким же.
 *
 * Базы создаются в памяти (inMemoryDatabaseBuilder): живут, пока идёт тест, и не оставляют файлов.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun newDb() = Room.inMemoryDatabaseBuilder(context, PlannerDatabase::class.java).build()

    @Test
    fun exportThenRestore_givesTheSameData() = runBlocking {
        val source = newDb()
        val target = newDb()
        try {
            // 1. Наполняем первую базу: список, задача, правило с циклом и паузой, цель с этапом и шагом
            source.taskListDao().insertAll(listOf(TaskListEntity(1, "Дом", ListColor.OCHRE, 0, false)))
            source.taskDao().insertAll(
                listOf(
                    TaskEntity(
                        id = 1, title = "Купить лампу", note = "в IKEA", date = LocalDate.of(2026, 10, 9),
                        time = null, partOfDay = null, listId = 1, status = TaskStatus.OPEN,
                        isSample = false, createdAt = Instant.ofEpochMilli(1_000), doneAt = null,
                        ruleId = null, occurrenceDate = null, goalId = null, stageId = null,
                    ),
                ),
            )
            val rule = RecurrenceRule(
                id = 1,
                title = "Полив",
                repeat = Repeat.Cycle(listOf(mapOf(WEDNESDAY to "фикус")), LocalDate.of(2026, 10, 5)),
                startDate = LocalDate.of(2026, 10, 5),
                pauses = listOf(Pause(LocalDate.of(2026, 10, 12))),
                listId = 1,
                goalId = 1, // привычка «помогает» цели — проверим, что связь не потеряется
            )
            // Цель раньше правила: правило на неё ссылается (goalId)
            source.goalDao().insertGoals(
                listOf(
                    GoalEntity(
                        id = 1, title = "Ремонт в ванной", why = "Чтобы не стыдно звать гостей", note = "",
                        deadline = LocalDate.of(2026, 12, 31), position = 0,
                        createdAt = Instant.ofEpochMilli(500), achievedOn = null,
                    ),
                ),
            )
            source.goalDao().insertStages(listOf(GoalStageEntity(id = 1, goalId = 1, title = "Демонтаж", position = 0)))
            source.recurrenceRuleDao().insertAll(listOf(rule.toEntity()))
            // И отмеченный день этого правила — задача со ссылкой на правило
            source.taskDao().insertAll(
                listOf(
                    TaskEntity(
                        id = 2, title = "Полив", note = "фикус", date = LocalDate.of(2026, 10, 7),
                        time = null, partOfDay = null, listId = 1, status = TaskStatus.DONE,
                        isSample = false, createdAt = Instant.ofEpochMilli(2_000), doneAt = Instant.ofEpochMilli(3_000),
                        ruleId = 1, occurrenceDate = LocalDate.of(2026, 10, 7), goalId = null, stageId = null,
                    ),
                ),
            )

            // Шаг цели: задача без списка, но с целью и этапом
            source.taskDao().insertAll(
                listOf(
                    TaskEntity(
                        id = 3, title = "Снять старую плитку", note = "", date = null,
                        time = null, partOfDay = null, listId = null, status = TaskStatus.OPEN,
                        isSample = false, createdAt = Instant.ofEpochMilli(4_000), doneAt = null,
                        ruleId = null, occurrenceDate = null, goalId = 1, stageId = 1, position = 2,
                    ),
                ),
            )

            // 2. Экспорт в «файл» (массив байтов в памяти) и восстановление во вторую базу
            val file = ByteArrayOutputStream()
            BackupManager(source).export(file)
            val manager = BackupManager(target)
            manager.restore(manager.read(ByteArrayInputStream(file.toByteArray())))

            // 3. Во второй базе всё то же самое
            assertEquals(source.taskListDao().getAll(), target.taskListDao().getAll())
            assertEquals(source.taskDao().getAll(), target.taskDao().getAll())
            assertEquals(source.recurrenceRuleDao().getAll(), target.recurrenceRuleDao().getAll())
            assertEquals(source.goalDao().getAllGoals(), target.goalDao().getAllGoals())
            assertEquals(source.goalDao().getAllStages(), target.goalDao().getAllStages())
        } finally {
            source.close()
            target.close()
        }
    }
}
