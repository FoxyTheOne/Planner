package app.planner.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec

/**
 * База данных приложения (SQLite через библиотеку Room). Файл на телефоне — `planner.db`.
 *
 * - `entities` — какие таблицы в ней есть;
 * - `version` — номер схемы. Если меняем таблицы (добавляем колонку и т.п.), номер растёт
 *   и пишется миграция, иначе у пользователей, обновивших приложение, данные не прочитаются;
 * - `exportSchema = true` — Room сохраняет описание каждой версии в `core/data/schemas`,
 *   по этим файлам пишутся и проверяются миграции;
 * - `@TypeConverters` — как хранить типы, которых SQLite не знает (даты, время).
 *
 * МИГРАЦИИ — как база на телефоне человека переходит со старой версии на новую.
 * Человек обновил приложение: файл базы у него старый (версия 1), а код ждёт версию 2.
 * Room видит разницу и выполняет миграцию — меняет таблицы, не трогая данные.
 * Без миграции Room не откроет базу и приложение упадёт (удалять данные мы ему не разрешаем).
 *
 * - `AutoMigration` — Room сам сравнивает схемы двух версий (файлы 1.json и 2.json)
 *   и пишет нужный SQL. Подходит для простых изменений: новая таблица, новая колонка.
 * - Сама Room не может понять, что колонку ПЕРЕИМЕНОВАЛИ: по двум схемам это выглядит как
 *   «одну колонку удалили, другую добавили» — и данные старой колонки пропали бы.
 *   Поэтому к AutoMigration прикладывают подсказку (spec) — например, @RenameColumn, см. [Migration3To4].
 * - Для совсем сложных изменений (разделить таблицу, пересчитать значения) пишется ручная
 *   `Migration` со своим SQL. Дойдём до этого, когда понадобится.
 *
 * История версий:
 * - 1 — задачи и списки;
 * - 2 — правила повторения (таблица recurrence_rules);
 * - 3 — у задач колонки ruleId и occurrenceDate: отмеченный день повторения хранится задачей;
 * - 4 — у цикла своё начало недели: cycleAnchorMonday переименована в cycleAnchorWeekStart,
 *   добавлена cycleWeekStart (у старых циклов пустая — значит, с понедельника, как и было);
 * - 5 — у правил колонка isHabit (у старых правил 0: обычное дело, как и было);
 * - 6 — цели: таблицы goals и goal_stages; у задач колонки goalId и stageId (шаг цели и его этап),
 *   у правил goalId (привычка помогает цели). У старых строк они пустые — к целям ничего не привязано;
 * - 7 — у задач колонка position: порядок шагов цели (у старых строк 0 — порядок добавления, как и было);
 * - 8 — у задач напоминание: колонки reminderMinutesBefore и reminderAt (у старых строк пустые — без напоминания);
 * - 9 — у правил колонка position: место в ряду привычек, которое задают перетаскиванием на экране
 *   «Привычки». У старых правил 0 — и они идут, как раньше, по порядку создания (ORDER BY position, id);
 * - 10 — у правил напоминание, как у задач в версии 8: колонки reminderMinutesBefore и reminderAt
 *   (у старых правил пустые — повторения без напоминаний, как и было).
 *
 * Класс абстрактный: реализацию Room генерирует сам при сборке.
 * Создаётся в `StorageModule` (см. `di/DataModule.kt`).
 */
@Database(
    entities = [
        TaskEntity::class, TaskListEntity::class, RecurrenceRuleEntity::class,
        GoalEntity::class, GoalStageEntity::class,
    ],
    version = 10,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = PlannerDatabase.Migration3To4::class),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
    ],
)
@TypeConverters(Converters::class)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskListDao(): TaskListDao
    abstract fun recurrenceRuleDao(): RecurrenceRuleDao
    abstract fun goalDao(): GoalDao

    /**
     * Подсказка для миграции 3 → 4: колонку не удалили и создали заново, а переименовали —
     * значит, её значения нужно перенести. Остальное (новую колонку cycleWeekStart) Room сделает сам.
     */
    @RenameColumn(
        tableName = "recurrence_rules",
        fromColumnName = "cycleAnchorMonday",
        toColumnName = "cycleAnchorWeekStart",
    )
    class Migration3To4 : AutoMigrationSpec
}
