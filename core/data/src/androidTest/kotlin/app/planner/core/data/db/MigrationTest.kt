package app.planner.core.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.planner.core.data.repository.RoomRecurrenceRepository
import app.planner.core.domain.model.Goal
import app.planner.core.domain.model.GoalStage
import app.planner.core.domain.model.Pause
import app.planner.core.domain.model.Reminder
import app.planner.core.domain.model.RecurrenceRule
import app.planner.core.domain.model.Repeat
import app.planner.core.domain.model.Task
import app.planner.core.domain.model.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate

/**
 * Тест миграций базы. Запускается на эмуляторе или телефоне (папка androidTest),
 * потому что нужен настоящий SQLite.
 *
 * Сценарий — как у человека, который обновил приложение:
 * 1. создаём базу ВЕРСИИ 1 по старой схеме (файл schemas/…/1.json) и кладём туда задачу и списки;
 * 2. прогоняем все миграции до последней версии; helper заодно сверяет таблицы со схемой последней версии;
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
    fun migrate1ToLatest_keepsTasksAndLists() = runBlocking {
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

        // 2. Миграции 1 → 2 → … → 8 (AutoMigration из PlannerDatabase) + проверка схемы.
        // Новая версия базы — меняем здесь число на неё, и тест проверит всю цепочку
        helper.runMigrationsAndValidate(dbName, 8, true).close()

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
            assertEquals(null, task.ruleId) // версия 3: у старых задач новые колонки пустые — обычная задача

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

    /**
     * Миграция 3 → 4 с переименованием колонки: дата опорной недели цикла должна переехать
     * из cycleAnchorMonday в cycleAnchorWeekStart, а не потеряться. У старого цикла начало недели —
     * понедельник (новая колонка пустая).
     */
    @Test
    fun migrate3To4_keepsCycleAnchor() = runBlocking {
        val oct5 = LocalDate.of(2026, 10, 5).toEpochDay()
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO recurrence_rules (id, title, startDate, repeatType, cycleWeeks, cycleAnchorMonday, " +
                    "cycleAnchorWeekNumber, pauses) VALUES (1, 'Полив', $oct5, 'CYCLE', " +
                    "'[{\"WEDNESDAY\":\"фикус\"},{}]', $oct5, 2, '[]')",
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 4, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PlannerDatabase::class.java, dbName).build()
        try {
            val cycle = db.recurrenceRuleDao().get(1)!!.toDomain().repeat as Repeat.Cycle
            assertEquals(LocalDate.of(2026, 10, 5), cycle.anchorWeekStart) // дата не потерялась
            assertEquals(2, cycle.anchorWeekNumber)
            assertEquals(DayOfWeek.MONDAY, cycle.weekStart) // старый цикл — с понедельника, как и был
        } finally {
            db.close()
        }
    }

    /** Миграция 4 → 5: правила, созданные до привычек, остаются обычными делами (isHabit = 0). */
    @Test
    fun migrate4To5_oldRulesAreNotHabits() = runBlocking {
        helper.createDatabase(dbName, 4).apply {
            execSQL(
                "INSERT INTO recurrence_rules (id, title, startDate, repeatType, pauses) " +
                    "VALUES (1, 'Бассейн', ${LocalDate.of(2026, 10, 5).toEpochDay()}, 'DAILY', '[]')",
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 5, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PlannerDatabase::class.java, dbName).build()
        try {
            val rule = db.recurrenceRuleDao().get(1)!!.toDomain()
            assertEquals("Бассейн", rule.title)
            assertEquals(false, rule.isHabit)
        } finally {
            db.close()
        }
    }

    /**
     * Миграция 5 → 6: появились цели. Старые задачи и правила ни к какой цели не привязаны,
     * а в новые таблицы можно положить цель, этап и шаг — и прочитать их обратно.
     */
    @Test
    fun migrate5To6_addsGoals() = runBlocking {
        val oct5 = LocalDate.of(2026, 10, 5).toEpochDay()
        helper.createDatabase(dbName, 5).apply {
            execSQL("INSERT INTO task_lists (id, name, color, position, isInbox) VALUES (1, '', 'SAGE', 0, 1)")
            execSQL(
                "INSERT INTO tasks (id, title, note, listId, status, isSample, createdAt) " +
                    "VALUES (10, 'Купить лампу', '', 1, 'OPEN', 0, 0)",
            )
            execSQL(
                "INSERT INTO recurrence_rules (id, title, startDate, repeatType, pauses, isHabit) " +
                    "VALUES (1, 'Вода', $oct5, 'DAILY', '[]', 1)",
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 6, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PlannerDatabase::class.java, dbName).build()
        try {
            assertEquals(null, db.taskDao().get(10)!!.goalId) // старая задача — не шаг цели
            assertEquals(null, db.recurrenceRuleDao().get(1)!!.toDomain().goalId)

            val goal = Goal(title = "Ремонт", why = "Уют", createdAt = Instant.ofEpochMilli(0))
            val goalId = db.goalDao().upsertGoal(goal.toEntity())
            val stageId = db.goalDao().upsertStage(GoalStage(goalId = goalId, title = "Плитка").toEntity())
            assertEquals(goal.copy(id = goalId), db.goalDao().getGoal(goalId)!!.toDomain())

            // Удалили этап — шаг остаётся в цели без этапа; удалили цель — этапы уходят с ней
            val step = Task(title = "Купить плитку", goalId = goalId, stageId = stageId, createdAt = Instant.ofEpochMilli(0))
            val stepId = db.taskDao().addOrUpdate(step.toEntity())
            db.goalDao().deleteStage(stageId)
            assertEquals(null, db.taskDao().get(stepId)!!.stageId)
            assertEquals(goalId, db.taskDao().get(stepId)!!.goalId)
        } finally {
            db.close()
        }
    }

    /** Миграция 6 → 7: у задач появилось место (position). У старых шагов оно 0 — порядок добавления. */
    @Test
    fun migrate6To7_oldStepsKeepOrderOfAdding() = runBlocking {
        helper.createDatabase(dbName, 6).apply {
            execSQL("INSERT INTO goals (id, title, why, note, position, createdAt) VALUES (1, 'Ремонт', '', '', 0, 0)")
            execSQL(
                "INSERT INTO tasks (id, title, note, status, isSample, createdAt, goalId) " +
                    "VALUES (10, 'Снять плитку', '', 'OPEN', 0, 0, 1)",
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 7, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PlannerDatabase::class.java, dbName).build()
        try {
            val step = db.taskDao().get(10)!!
            assertEquals(0, step.position)
            assertEquals(1L, step.goalId)
        } finally {
            db.close()
        }
    }

    /** Миграция 7 → 8: у задач появилось напоминание. У старых задач его нет. */
    @Test
    fun migrate7To8_oldTasksHaveNoReminder() = runBlocking {
        helper.createDatabase(dbName, 7).apply {
            execSQL(
                "INSERT INTO tasks (id, title, note, status, isSample, createdAt, position) " +
                    "VALUES (10, 'Врач', '', 'OPEN', 0, 0, 0)",
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 8, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PlannerDatabase::class.java, dbName).build()
        try {
            assertEquals(null, db.taskDao().get(10)!!.toDomain().reminder)
            // А новое напоминание записывается и читается обратно
            val withReminder = db.taskDao().get(10)!!.toDomain().copy(reminder = Reminder.BeforeTime(15))
            db.taskDao().addOrUpdate(withReminder.toEntity())
            assertEquals(Reminder.BeforeTime(15), db.taskDao().get(10)!!.toDomain().reminder)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate8To9_oldRulesKeepCreationOrder() = runBlocking {
        val oct5 = LocalDate.of(2026, 10, 5).toEpochDay()
        helper.createDatabase(dbName, 8).apply {
            execSQL(
                "INSERT INTO recurrence_rules (id, title, startDate, repeatType, pauses, isHabit) " +
                    "VALUES (1, 'Вода', $oct5, 'DAILY', '[]', 1), (2, 'Зарядка', $oct5, 'DAILY', '[]', 1)",
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 9, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, PlannerDatabase::class.java, dbName).build()
        try {
            val repo = RoomRecurrenceRepository(db)
            // У старых правил места ещё нет (0) — идут по порядку создания
            assertEquals(listOf("Вода", "Зарядка"), repo.observeRules().first().map { it.title })
            // Переставили — порядок записался
            repo.reorder(listOf(2, 1))
            assertEquals(listOf("Зарядка", "Вода"), repo.observeRules().first().map { it.title })
        } finally {
            db.close()
        }
    }
}
