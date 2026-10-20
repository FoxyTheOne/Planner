package app.planner.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri

/**
 * «Осмотр» телефона: что может помешать напоминаниям прийти вовремя — и куда вести человека,
 * чтобы это исправить. Используют страница «Надёжность напоминаний», строка «Напомнить»
 * в редакторе и [ReminderScheduler].
 *
 * Все проверки — «как сейчас»: человек может поменять настройку в любой момент,
 * поэтому экраны перепроверяют, когда снова оказываются на виду.
 *
 * `object` — один на всё приложение, без состояния: просто набор функций под общим именем.
 */
object ReminderHealth {

    /**
     * Включены ли напоминания о делах. Учитывает всё сразу: разрешение Android 13+,
     * общий выключатель уведомлений приложения и отдельный выключатель канала дел —
     * в настройках Android его можно выключить, оставив остальные уведомления.
     *
     * Канал привычек не считаем: выключить тихие напоминания о привычках — обычный выбор, а не поломка.
     */
    fun notificationsOn(context: Context): Boolean =
        channelState(context, ReminderNotifications.CHANNEL_TASKS) != ChannelState.OFF

    /**
     * Разрешены ли уведомления приложению вообще — неважно, какого канала. Нет, если человек
     * выключил их в настройках Android или ещё не дал разрешения: с Android 13 приложение
     * спрашивает его, только когда человек впервые ставит напоминание.
     */
    fun appNotificationsOn(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Со звуком ли приходят напоминания о делах. Без звука их легко не заметить,
     * а некоторые телефоны (например, Xiaomi) сами выключают звук у новых приложений.
     */
    fun soundOn(context: Context): Boolean =
        channelState(context, ReminderNotifications.CHANNEL_TASKS) == ChannelState.SOUND

    /**
     * Как сейчас приходят уведомления канала [channelId] — так, как его настроил человек.
     *
     * «Важность» (importance) канала определяет, как он себя ведёт: NONE — выключен,
     * LOW и ниже — тихо, только в шторке, DEFAULT и выше — со звуком. Но звук можно выключить
     * и отдельно, оставив важность, — тогда у канала нет мелодии (sound == null).
     */
    fun channelState(context: Context, channelId: String): ChannelState {
        val channel = NotificationManagerCompat.from(context).getNotificationChannelCompat(channelId)
        return when {
            !appNotificationsOn(context) -> ChannelState.OFF
            // Канала ещё нет (его создают при запуске) — значит, будет таким, каким его создадут
            channel == null -> ChannelState.SOUND
            channel.importance == NotificationManager.IMPORTANCE_NONE -> ChannelState.OFF
            channel.importance >= NotificationManager.IMPORTANCE_DEFAULT && channel.sound != null ->
                ChannelState.SOUND

            else -> ChannelState.SILENT
        }
    }

    /**
     * Телефон Xiaomi (в том числе Redmi и POCO — у них производитель тоже «Xiaomi»).
     * Их прошивка MIUI / HyperOS строже других закрывает приложения в фоне,
     * и у неё свои пункты «Автозапуск» и «Контроль активности», о которых стоит подсказать.
     */
    val isXiaomi: Boolean
        get() = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    /**
     * Можно ли ставить будильники на точное время («Будильники и напоминания» в настройках).
     * С Android 12 это отдельное разрешение, а с Android 14 у новых установок оно выключено.
     * До Android 12 разрешения нет — точные будильники можно всегда.
     */
    fun exactAlarmsOn(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /**
     * Работает ли приложение в фоне без ограничений батареи. Для будильников на большинстве
     * телефонов это не нужно: они срабатывают и в режиме экономии. Но некоторые производители
     * закрывают «лишние» фоновые приложения, и тогда это помогает.
     */
    fun unrestrictedInBackground(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)

    /**
     * Всё ли в порядке с главным: уведомления и точное время. Фоновую работу не считаем:
     * на большинстве телефонов она ни при чём, и «не в порядке» из-за неё только пугало бы.
     */
    fun allGood(context: Context): Boolean = notificationsOn(context) && exactAlarmsOn(context)

    /** Системный экран «Уведомления» этого приложения — там их включают. */
    fun notificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** Системный экран одного канала [channelId]: там включают и выключают звук, вибрацию, всплывание. */
    fun channelSettings(context: Context, channelId: String): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)

    /**
     * Системный экран «Будильники и напоминания» этого приложения (Android 12+).
     * «package:…» в адресе — чтобы открылся переключатель именно нашего приложения, а не список всех.
     */
    @RequiresApi(Build.VERSION_CODES.S) // экрана нет до Android 12 — там и разрешения такого нет
    fun exactAlarmSettings(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            "package:${context.packageName}".toUri()
        )

    /**
     * «О приложении» в настройках Android. Оттуда путь к «Батарея → Без ограничений».
     * Прямой экран «не экономить батарею для этого приложения» Google Play разрешает
     * только особым приложениям (например, мессенджерам), поэтому ведём сюда.
     */
    fun appSettings(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri()
        )
}

/** Как приходят уведомления канала: со звуком, тихо (только в шторке) или никак. См. [ReminderHealth.channelState]. */
enum class ChannelState { SOUND, SILENT, OFF }
