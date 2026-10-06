package app.planner.common

import android.content.Context
import android.content.res.Configuration
import app.planner.BuildConfig
import app.planner.R
import app.planner.core.domain.list.IInboxNames
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Достаёт из strings.xml, как «Без списка» называется на каждом языке приложения.
 * Это реализация [IInboxNames] из domain: domain сам ресурсы читать не умеет.
 *
 * Обычно `getString` отдаёт текст на ТЕКУЩЕМ языке телефона. Чтобы получить перевод
 * на другом языке, делаем «контекст с другим языком» (createConfigurationContext)
 * и спрашиваем строку у него.
 *
 * Список языков — [BuildConfig.LOCALES], он берётся из app/build.gradle.kts (appLocales).
 * Поэтому новый язык подхватится сам: достаточно добавить его туда и перевести строку.
 */
@Singleton
class ResourceInboxNames @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : IInboxNames {

    // by lazy — посчитать один раз при первом обращении и запомнить
    override val all: Set<String> by lazy {
        BuildConfig.LOCALES.map { tag ->
            val config = Configuration(context.resources.configuration)
            config.setLocale(Locale.forLanguageTag(tag))
            context.createConfigurationContext(config).getString(R.string.list_inbox)
        }.toSet()
    }
}

/** Hilt: «когда просят IInboxNames — дай ResourceInboxNames». */
@Module
@InstallIn(SingletonComponent::class)
abstract class InboxNamesModule {
    @Binds
    abstract fun inboxNames(impl: ResourceInboxNames): IInboxNames
}
