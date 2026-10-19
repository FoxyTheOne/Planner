package app.planner.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.planner.MainActivity
import app.planner.R
import app.planner.common.DateTexts
import app.planner.common.notePreview
import app.planner.core.domain.model.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Уведомления-напоминания о задачах.
 *
 * КАНАЛ. С Android 8 каждое уведомление принадлежит «каналу» — группе, которую человек может
 * отдельно настроить в системных настройках (звук, вибрация, выключить совсем). У нас пока
 * один канал — «Напоминания». Создать канал можно сколько угодно раз: если он уже есть,
 * Android просто ничего не делает, поэтому [createChannel] вызывается при каждом запуске.
 *
 * РАЗРЕШЕНИЕ. С Android 13 показывать уведомления можно только с разрешения человека
 * (POST_NOTIFICATIONS). Спрашивать его будем, когда человек впервые поставит напоминание
 * (следующий шаг). Без разрешения [show] просто ничего не показывает.
 */
@Singleton
class ReminderNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private companion object {
        const val CHANNEL_REMINDERS = "reminders"
    }

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders),
            // HIGH — уведомление всплывает поверх экрана и звучит: напоминание должно быть замечено
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_reminders_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Есть ли разрешение показывать уведомления (до Android 13 оно есть всегда). */
    val canNotify: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Показать напоминание о [task]: заголовок — название задачи, ниже — её время и начало заметки
     * («10:30 · каб. 214»). Тап по уведомлению открывает приложение.
     */
    fun show(task: Task) {
        // Проверка разрешения — прямо здесь, рядом с notify, а не только через canNotify:
        // Android Studio (проверка Lint «MissingPermission») видит проверку, только если она
        // в той же функции, что и вызов, которому разрешение нужно. Через свойство она её «не видит»
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val text =
            listOfNotNull(task.time?.let(DateTexts::time), task.notePreview).joinToString(" · ")
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(task.title)
            .setContentText(text.ifEmpty { null })
            .setContentIntent(openApp(task.id))
            .setAutoCancel(true) // нажали — уведомление убирается
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // для Android 7 и старше, где каналов нет
            // Кнопки под текстом. Иконка 0 — без значка: в уведомлениях Android 7+ у кнопок
            // значки всё равно не рисуются, только подписи
            .addAction(
                0,
                context.getString(R.string.action_done),
                action(task.id, ReminderReceiver.ACTION_DONE)
            )
            .addAction(
                0,
                context.getString(R.string.notification_snooze),
                action(task.id, ReminderReceiver.ACTION_SNOOZE)
            )
            .build()
        // id уведомления = id задачи: повторное напоминание о той же задаче заменит старое, а не добавит второе
        NotificationManagerCompat.from(context).notify(task.id.toInt(), notification)
    }

    /** Убрать уведомление задачи [taskId] — после нажатия на его кнопку. */
    fun dismiss(taskId: Long) = NotificationManagerCompat.from(context).cancel(taskId.toInt())

    /**
     * Нажатие на кнопку уведомления — сигнал нашему [ReminderReceiver] с действием [action].
     * Номер (requestCode) у всех трёх «намерений» задачи один — id задачи, но Android различает
     * их по действию (action): «Готово» и «Отложить» не заменят друг друга и будильник.
     */
    private fun action(taskId: Long, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Тап по уведомлению — открыть приложение (на экране «Сегодня»). */
    private fun openApp(taskId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            taskId.toInt(),
            Intent(
                context,
                MainActivity::class.java
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
