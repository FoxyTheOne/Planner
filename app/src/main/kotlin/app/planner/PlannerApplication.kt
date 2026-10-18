package app.planner

import android.app.Application
import app.planner.reminders.ReminderNotifications
import app.planner.reminders.ReminderSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Класс приложения — создаётся Android первым, раньше любого экрана, и живёт, пока жив процесс.
 * Указан в AndroidManifest.xml (`android:name=".PlannerApplication"`).
 *
 * `@HiltAndroidApp` запускает Hilt: отсюда начинается «дерево» зависимостей,
 * из которого потом получают базу, репозитории и ViewModel все экраны.
 *
 * При запуске здесь же включаются напоминания: создаётся канал уведомлений и запускается
 * [ReminderSync] — он следит за задачами с напоминаниями и держит будильники в порядке.
 * Процесс приложения запускается и без экранов — например, когда сработал будильник или
 * телефон перезагрузили, — поэтому это место надёжнее, чем MainActivity.
 */
@HiltAndroidApp
class PlannerApplication : Application() {

    // Поля с @Inject Hilt заполняет сам — к моменту onCreate они уже готовы
    @Inject
    lateinit var reminderNotifications: ReminderNotifications
    @Inject
    lateinit var reminderSync: ReminderSync

    override fun onCreate() {
        super.onCreate()
        reminderNotifications.createChannel()
        reminderSync.start()
    }
}
