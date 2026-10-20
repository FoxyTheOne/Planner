package app.planner.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.planner.MainActivity
import app.planner.R
import app.planner.common.DateTexts
import app.planner.common.notePreview
import app.planner.core.domain.model.Task
import app.planner.core.domain.recurrence.Occurrence
import app.planner.reminders.ReminderNotifications.Companion.TAG_RULE
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Уведомления-напоминания о задачах ([show]) и о днях повторений и привычек ([showRuleDay]).
 *
 * КАНАЛ. С Android 8 каждое уведомление принадлежит «каналу» — группе, которую человек может
 * отдельно настроить в системных настройках (звук, вибрация, выключить совсем). У нас пока
 * один канал — «Напоминания». Создать канал можно сколько угодно раз: если он уже есть,
 * Android просто ничего не делает, поэтому [createChannel] вызывается при каждом запуске.
 *
 * РАЗРЕШЕНИЕ. С Android 13 показывать уведомления можно только с разрешения человека
 * (POST_NOTIFICATIONS). Спрашиваем его, когда человек впервые выбирает напоминание (ReminderRow),
 * а включены ли уведомления, проверяет ReminderHealth. Без разрешения уведомление просто не показывается
 * (проверка одна на всех — в [post]).
 */
@Singleton
class ReminderNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_REMINDERS = "reminders"

        /** Номер пробного уведомления. У задач номера с 1, поэтому 0 ни с одной не совпадёт. */
        const val TEST_NOTIFICATION_ID = 0

        /**
         * Метка (tag) уведомлений о днях повторений. Уведомление в Android узнаётся по паре
         * «метка + номер»: у задачи 5 и повторения 5 номер один, а метки разные — они не заменят друг друга.
         * У уведомлений задач метки нет (null).
         */
        private const val TAG_RULE = "rule"

        /** Какой день повторения в уведомлении — в его «дополнительных данных» (extras). */
        private const val EXTRA_DAY = "planner.day"
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

    /**
     * Показать напоминание о [task]: заголовок — название задачи, ниже — её время и начало заметки
     * («10:30 · каб. 214»). Тап по уведомлению открывает приложение.
     */
    fun show(task: Task) {
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
        post(tag = null, id = task.id.toInt(), notification)
    }

    /**
     * Пробное напоминание со страницы «Надёжность напоминаний»: «Если вы это видите — напоминания доходят».
     * Без кнопок и без задачи; тап открывает приложение.
     */
    fun showTest() {
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(context.getString(R.string.test_notification_title))
            .setContentText(context.getString(R.string.test_notification_text))
            .setContentIntent(openApp(TEST_NOTIFICATION_ID.toLong()))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        post(tag = null, id = TEST_NOTIFICATION_ID, notification)
    }

    /**
     * Напоминание о дне повторения или привычки: «Полить цветы» и ниже «18:00 · фикус»
     * (время и дела этого дня у цикла). Кнопки те же, что у задачи: «Готово» отмечает день,
     * «Отложить» — напомнит через 10 минут.
     */
    fun showRuleDay(occurrence: Occurrence) {
        val rule = occurrence.rule
        val text =
            listOfNotNull(rule.time?.let(DateTexts::time), occurrence.details).joinToString(" · ")
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(rule.title)
            .setContentText(text.ifEmpty { null })
            .setContentIntent(openApp(rule.id))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Запоминаем день: по нему ReminderSync поймёт, что день уже отметили, и уберёт уведомление
            .addExtras(Bundle().apply { putLong(EXTRA_DAY, occurrence.date.toEpochDay()) })
            .addAction(
                0,
                context.getString(R.string.action_done),
                ruleAction(rule.id, occurrence.date, ReminderReceiver.ACTION_RULE_DONE),
            )
            .addAction(
                0,
                context.getString(R.string.notification_snooze),
                ruleAction(rule.id, occurrence.date, ReminderReceiver.ACTION_RULE_SNOOZE),
            )
            .build()
        // Метка + id правила: следующее напоминание того же повторения заменит это, а не добавит второе
        post(tag = TAG_RULE, id = rule.id.toInt(), notification)
    }

    /**
     * Показать готовое уведомление. Единственное место, где мы зовём notify, — поэтому и проверка
     * разрешения (Android 13+) здесь одна на всех. Без разрешения просто ничего не показываем.
     *
     * Проверка — прямо здесь, рядом с notify, а не в отдельной функции: Android Studio
     * (проверка Lint «MissingPermission») видит её, только если она в той же функции, что и вызов,
     * которому разрешение нужно.
     *
     * @param tag метка: у задач null, у дней повторений [TAG_RULE] — по паре «метка + номер»
     *   Android узнаёт уведомление, чтобы заменить или убрать его.
     */
    private fun post(tag: String?, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(tag, id, notification)
    }

    /** Убрать уведомление задачи [taskId] — после нажатия на его кнопку. */
    fun dismiss(taskId: Long) = NotificationManagerCompat.from(context).cancel(taskId.toInt())

    /** Убрать уведомление повторения [ruleId] — после нажатия на его кнопку. */
    fun dismissRuleDay(ruleId: Long) =
        NotificationManagerCompat.from(context).cancel(TAG_RULE, ruleId.toInt())

    /**
     * Убрать уведомления, о которых напоминать больше не нужно: о задачах — всех, кроме [keepTaskIds];
     * о днях повторений — тех, для которых [keepRuleDay] ответит «нет».
     *
     * Зачем: пришло «Позвонить бабушке», а отметили её в приложении (или удалили, вернули в список,
     * убрали напоминание). Уведомление про уже сделанное дело в шторке ни к чему. Зовёт ReminderSync
     * при каждом изменении — он как раз знает, у каких задач напоминание ещё в силе.
     *
     * С днями повторений так же: [keepRuleDay] отвечает, ещё ли актуален день (его не отметили,
     * не перенесли, напоминание у повторения не убрали).
     *
     * activeNotifications — уведомления нашего приложения, которые сейчас висят в шторке.
     * Берём только из канала напоминаний: если позже появятся другие (вечерняя сводка), их не трогаем.
     */
    fun dismissAllExcept(
        keepTaskIds: Set<Long>,
        keepRuleDay: (ruleId: Long, date: LocalDate) -> Boolean
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications
            // Пробное уведомление не трогаем: задачи у него нет, но оно должно повисеть, пока его не увидят
            .filter { it.notification.channelId == CHANNEL_REMINDERS && it.id != TEST_NOTIFICATION_ID }
            .forEach { shown ->
                val keep = if (shown.tag == TAG_RULE) {
                    val day = LocalDate.ofEpochDay(shown.notification.extras.getLong(EXTRA_DAY))
                    keepRuleDay(shown.id.toLong(), day)
                } else {
                    shown.id.toLong() in keepTaskIds
                }
                if (!keep) manager.cancel(shown.tag, shown.id)
            }
    }

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

    /** Кнопка уведомления о дне повторения — тот же приёмник, но с правилом и днём вместо задачи. */
    private fun ruleAction(ruleId: Long, date: LocalDate, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ruleId.toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .putExtra(ReminderReceiver.EXTRA_RULE_ID, ruleId)
                .putExtra(ReminderReceiver.EXTRA_DATE, date.toEpochDay()),
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
