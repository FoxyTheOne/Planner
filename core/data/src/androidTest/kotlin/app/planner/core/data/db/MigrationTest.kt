package app.planner.core.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

/**
 * Тест миграций базы. Запускается на эмуляторе или телефоне (папка androidTest),
 * потому что нужен настоящий SQLite.
 *
 * Сценарий — как у человека, который обновил приложение:
 * 1. создаём базу ВЕРСИИ 1 по старой схеме (файл schemas/…/1.json) и кладём туда задачу и списки;
 * 2. прогоняем миграцию до версии 2; helper заодно сверяет получившиеся таблицы со схемой 2.json;
 * 3. открываем базу уже нынешним кодом и проверяем, что старые данные на месте, а новая таблица работает.
 *
 * Запуск: зелёный треугольник рядом с классом в Android Studio
 * или `./gradlew :core:data:connectedDebugAndroidTest` при запущенном эмуляторе.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    // @get:Rule — JUnit сам создаст helper перед тестом и уберёт за ним
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PlannerDatabase::class.java,
    )

    @Test
    fun migrate1To2_keepsTasksAndLists() = runBlocking {
        // 1. База версии 1. Пишем «сырым» SQL: классы нынешнего кода описывают уже версию 2.
        //    Даты — числа дней от 1970 года, как их хранит Converters
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO task_lists (id, name, color, position, isInbox) VALUES (1, '', 'SAGE', 0, 1)")
            execSQL("INSERT INTO task_lists (id, name, color, position, isInbox) VALUES (2, 'Дом', 'OCHRE', 1, 0)")
            execSQL(
                "INSERT INTO tasks (id, title, note, date, time, partOfDay, listId, status, isSample, createdAt, doneAt) " +
                    "VALUES (10, 'Купить лампу', 'в IKEA', ${LocalDate.of(2026, 10, 7).toEpochDay()}, NULL, 'EVENING', 2, 'OPEN', 0, 0, NULL)",
            )
            close()
        }

        // 2. Миграция 1 → 2 (AutoMigration из PlannerDatabase) + проверка схемы
        helper.runMigrationsAndValidate(dbName, 2, true).close()

        // 3. Открываем обычным кодом приложения
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PlannerDatabase::class.java, dbName).build()
        try {
            val task = db.taskDao().getAll().single()
            assertEquals("Купить лампу", task.title)
            assertEquals("в IKEA", task.note)
            assertEquals(LocalDate.of(2026, 10, 7), task.date)
            assertEquals(2L, task.listId)
            assertEquals(TaskStatus.OPEN, task.status)

            assertEquals(listOf("", "Дом"), db.taskListDao().getAll().sortedBy { it.id }.map { it.name })

            // Новая таблица работает: правило сохраняется и читается таким же
            val rule = RecurrenceRule(
                title = "Полив",
                repeat = Repeat.Cycle(listOf(mapOf(WEDNESDAY to "фикус")), LocalDate.of(2026, 10, 5)),
                startDate = LocalDate.of(2026, 10, 5),
                pauses = listOf(Pause(LocalDate.of(2026, 10, 12))),
                listId = 2,
            )
            val id = db.recurrenceRuleDao().upsert(rule.toEntity())
            assertEquals(rule.copy(id = id), db.recurrenceRuleDao().get(id)!!.toDomain())
        } finally {
            db.close()
        }
    }
}
