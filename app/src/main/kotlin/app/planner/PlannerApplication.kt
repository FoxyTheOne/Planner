package app.planner

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Класс приложения — создаётся Android первым, раньше любого экрана, и живёт, пока жив процесс.
 * Указан в AndroidManifest.xml (`android:name=".PlannerApplication"`).
 *
 * `@HiltAndroidApp` запускает Hilt: отсюда начинается «дерево» зависимостей,
 * из которого потом получают базу, репозитории и ViewModel все экраны.
 */
@HiltAndroidApp
class PlannerApplication : Application()
