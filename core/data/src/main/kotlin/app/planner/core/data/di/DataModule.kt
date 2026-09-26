package app.planner.core.data.di

import android.content.Context
import androidx.room.Room
import app.planner.core.data.db.PlannerDatabase
import app.planner.core.data.repository.RoomTaskListRepository
import app.planner.core.data.repository.RoomTaskRepository
import app.planner.core.data.settings.DataStoreSettingsRepository
import app.planner.core.domain.repository.SettingsRepository
import app.planner.core.domain.repository.TaskListRepository
import app.planner.core.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/*
 * Модули Hilt — «инструкции по сборке» зависимостей.
 *
 * Hilt (внедрение зависимостей, DI) сам создаёт объекты и передаёт их туда, где они нужны:
 * ViewModel просит в конструкторе `TaskRepository` — Hilt находит здесь, чем его заменить,
 * и создаёт. Так классы не создают друг друга вручную и их легко подменять в тестах.
 *
 * `@InstallIn(SingletonComponent::class)` — объекты живут всё время работы приложения.
 */

/**
 * Как создать базу данных. `@Provides` — для объектов, которые нельзя просто создать
 * через `@Inject constructor` (Room-базу строит специальный builder).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PlannerDatabase =
        Room.databaseBuilder(context, PlannerDatabase::class.java, "planner.db").build()
}

/**
 * Какая реализация стоит за каждым интерфейсом. `@Binds` говорит Hilt:
 * «когда просят TaskRepository — дай RoomTaskRepository».
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun taskRepository(impl: RoomTaskRepository): TaskRepository
    @Binds abstract fun taskListRepository(impl: RoomTaskListRepository): TaskListRepository
    @Binds abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
