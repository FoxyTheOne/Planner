package app.planner.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.planner.core.data.db.PlannerDatabase
import app.planner.core.data.repository.RoomTaskListRepository
import app.planner.core.data.repository.RoomTaskRepository
import app.planner.core.data.settings.DataStoreSettingsRepository
import app.planner.core.domain.repository.ITaskListRepository
import app.planner.core.domain.repository.ITaskRepository
import app.planner.core.domain.repository.ISettingsRepository
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
 * Как создать хранилища: базу данных и файл настроек. Все хранилища приложения создаются здесь,
 * в одном месте.
 *
 * `@Provides` — для объектов, которые нельзя просто создать через `@Inject constructor`
 * (их строят специальные «фабрики» библиотек). `@Singleton` — Hilt создаст объект один раз
 * и всем будет отдавать его же. Для DataStore это обязательно: два DataStore на один файл
 * роняют приложение.
 */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PlannerDatabase =
        Room.databaseBuilder(context, PlannerDatabase::class.java, "planner.db").build()

    /**
     * Файл настроек: files/datastore/settings.preferences_pb.
     * Имя "settings" то же, что было раньше, поэтому уже сохранённые настройки не потеряются.
     *
     * corruptionHandler — что делать, если файл повреждён (например, телефон выключился
     * посреди записи): не падать, а начать с пустых настроек, то есть со значений по умолчанию.
     *
     * Если понадобится ещё один файл настроек (например, для виджетов), рядом появится второй
     * такой метод, а различать их будем аннотациями-«ярлыками» (@Qualifier).
     */
    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { context.preferencesDataStoreFile("settings") },
        )
}

/**
 * Какая реализация стоит за каждым интерфейсом. `@Binds` говорит Hilt:
 * «когда просят TaskRepository — дай RoomTaskRepository».
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun taskRepository(impl: RoomTaskRepository): ITaskRepository
    @Binds
    abstract fun taskListRepository(impl: RoomTaskListRepository): ITaskListRepository
    @Binds
    abstract fun settingsRepository(impl: DataStoreSettingsRepository): ISettingsRepository
}
