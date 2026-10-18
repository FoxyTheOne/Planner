package app.planner.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * «Будильник» Android для напоминаний: поставить и снять будильник одной задачи.
 *
 * Как это устроено в Android. Приложение не может «ждать» до 10:00 — его процесс в любой момент
 * могут выгрузить из памяти. Поэтому мы просим систему (AlarmManager): «в 10:00 разбуди вот этот
 * BroadcastReceiver». Просьба — это PendingIntent: «намерение на потом», которое система выполнит
 * от имени приложения, даже если приложение к тому времени закрыто. Будильник разбудит
 * [ReminderReceiver], а тот покажет уведомление.
 *
 * ТОЧНОЕ ВРЕМЯ. С Android 12 точные будильники требуют разрешения, а с Android 14 у новых установок
 * оно по умолчанию выключено. Без него ставим «неточный» будильник: система может прислать его
 * на несколько минут позже, зато он работает всегда. Как включить точное — объяснит страница
 * «Надёжность напоминаний» (следующий блок).
 *
 * У каждой задачи — свой будильник: номер (requestCode) PendingIntent = id задачи. Поэтому
 * повторная постановка того же id заменяет старый будильник, а не добавляет второй.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)

    /** Можно ли ставить будильники на точное время. До Android 12 — всегда можно. */
    val canScheduleExact: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /** Поставить (или переставить) будильник задачи [taskId] на [at] по часам телефона. */
    fun schedule(taskId: Long, at: LocalDateTime) {
        // AlarmManager считает в миллисекундах от 1970 года по всемирному времени, поэтому
        // «19 октября, 10:00» переводим с учётом часового пояса телефона
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = pendingIntent(taskId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        // ...AndAllowWhileIdle — сработает и в режиме сна (Doze), когда телефон долго лежит без дела
        if (canScheduleExact) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
                return
            } catch (_: SecurityException) {
                // Разрешение могли отозвать между проверкой и вызовом — тогда неточный, ниже
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
    }

    /** Снять будильник задачи [taskId], если он стоит. */
    fun cancel(taskId: Long) {
        // FLAG_NO_CREATE — «найди уже существующий, новый не создавай»: нет будильника — null, и снимать нечего
        pendingIntent(taskId, PendingIntent.FLAG_NO_CREATE)?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(taskId: Long, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_REMIND)
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId),
            // IMMUTABLE — система не может подменить содержимое намерения (требование Android 12+)
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
}
