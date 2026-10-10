package app.planner.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
 * - Для сложных (переименовали колонку, разделили таблицу) пишется ручная `Migration`
 *   со своим SQL — или AutoMigration с подсказкой (spec). Дойдём до этого, когда понадобится.
 *
 * История версий:
 * - 1 — задачи и списки;
 * - 2 — правила повторения (таблица recurrence_rules);
 * - 3 — у задач колонки ruleId и occurrenceDate: отмеченный день повторения хранится задачей.
 *
 * Класс абстрактный: реализацию Room генерирует сам при сборке.
 * Создаётся в `StorageModule` (см. `di/DataModule.kt`).
 */
@Database(
    entities = [TaskEntity::class, TaskListEntity::class, RecurrenceRuleEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ],
)
@TypeConverters(Converters::class)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskListDao(): TaskListDao
    abstract fun recurrenceRuleDao(): RecurrenceRuleDao
}
