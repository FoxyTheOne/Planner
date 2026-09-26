package app.planner.core.data.db

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
 * Класс абстрактный: реализацию Room генерирует сам при сборке.
 * Создаётся в `DatabaseModule` (см. `di/DataModule.kt`).
 */
@Database(
    entities = [TaskEntity::class, TaskListEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskListDao(): TaskListDao
}
