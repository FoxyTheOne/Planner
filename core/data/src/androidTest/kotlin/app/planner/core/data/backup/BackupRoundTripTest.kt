package app.planner.core.data.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
            // 1. Наполняем первую базу: список, задача, правило с циклом и паузой
            source.taskListDao().insertAll(listOf(TaskListEntity(1, "Дом", ListColor.OCHRE, 0, false)))
            source.taskDao().insertAll(
                listOf(
                    TaskEntity(
                        id = 1, title = "Купить лампу", note = "в IKEA", date = LocalDate.of(2026, 10, 9),
                        time = null, partOfDay = null, listId = 1, status = TaskStatus.OPEN,
                        isSample = false, createdAt = Instant.ofEpochMilli(1_000), doneAt = null,
                        ruleId = null, occurrenceDate = null,
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
            )
            source.recurrenceRuleDao().insertAll(listOf(rule.toEntity()))
            // И отмеченный день этого правила — задача со ссылкой на правило
            source.taskDao().insertAll(
                listOf(
                    TaskEntity(
                        id = 2, title = "Полив", note = "фикус", date = LocalDate.of(2026, 10, 7),
                        time = null, partOfDay = null, listId = 1, status = TaskStatus.DONE,
                        isSample = false, createdAt = Instant.ofEpochMilli(2_000), doneAt = Instant.ofEpochMilli(3_000),
                        ruleId = 1, occurrenceDate = LocalDate.of(2026, 10, 7),
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
        } finally {
            source.close()
            target.close()
        }
    }
}
