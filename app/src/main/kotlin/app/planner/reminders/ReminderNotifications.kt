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
import app.planner.common.foodRes
import app.planner.common.notePreview
import app.planner.core.domain.medication.Dose
import app.planner.core.domain.medication.DoseKey
import app.planner.core.domain.model.PlannerSettings
import app.planner.core.domain.model.Task
import app.planner.core.domain.recurrence.Occurrence
import app.planner.core.domain.repository.ISettingsRepository
import app.planner.reminders.ReminderNotifications.Companion.CHANNEL_HABITS
import app.planner.reminders.ReminderNotifications.Companion.CHANNEL_TASKS
import app.planner.reminders.ReminderNotifications.Companion.TAG_RULE
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Уведомления-напоминания о задачах ([show]), о днях повторений и привычек ([showRuleDay])
 * и о приёмах лекарств ([showDose]).
 *
 * КАНАЛЫ. С Android 8 каждое уведомление принадлежит «каналу» — группе, которую человек может
 * отдельно настроить в системных настройках (звук, вибрация, всплывать ли, выключить совсем).
 * У нас их три:
 * - [CHANNEL_TASKS] «Напоминания о делах» — задачи и повторения. Звучат и всплывают поверх экрана;
 * - [CHANNEL_HABITS] «Привычки» — звучат, но не всплывают: привычка — мягкий повод, а не срочное дело.
 * - [CHANNEL_MEDICATIONS] «Лекарства» — звучат и всплывают: приём важно не пропустить.
 *
 * ЗВУК. У канала звук задаётся один раз, при создании, — дальше его меняет только человек
 * в настройках Android. А там на каждом телефоне всё по-своему: на Xiaomi, например, выключить
 * звук канала почти невозможно найти. Поэтому «со звуком / без звука» выбирают в настройках
 * приложения ([PlannerSettings.tasksSound], [PlannerSettings.habitsSound], [PlannerSettings.medicationsSound]),
 * а мы выполняем выбор
 * у каждого уведомления: «без звука» — setSilent(true). Такое уведомление не звучит, не вибрирует
 * и не всплывает, что бы ни было выбрано у канала, — просто тихо появляется в шторке.
 * Каналы же создаём «звучащими», чтобы «со звуком» тоже работало.
 *
 * Создать канал можно сколько угодно раз: если он уже есть, Android обновит только название
 * и описание, а звук и всё, что настроил человек, не тронет. Поэтому [createChannels]
 * вызывается при каждом запуске.
 *
 * РАЗРЕШЕНИЕ. С Android 13 показывать уведомления можно только с разрешения человека
 * (POST_NOTIFICATIONS). Спрашиваем его, когда человек впервые выбирает напоминание (ReminderRow),
 * а включены ли уведомления, проверяет ReminderHealth. Без разрешения уведомление просто не показывается
 * (проверка одна на всех — в [post]).
 */
@Singleton
class ReminderNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: ISettingsRepository,
) {
    companion object {
        /**
         * Канал задач и повторений. Идентификатор старый, «reminders»: так назывался канал,
         * когда был единственным, — если человек его уже настроил, настройки сохранятся.
         */
        const val CHANNEL_TASKS = "reminders"

        /**
         * Канал привычек. Новый идентификатор: первый канал привычек («habits») был тихим,
         * а поднять громкость канала приложение не может — только удалить его и создать новый.
         */
        const val CHANNEL_HABITS = "habit_reminders"

        /** Прежний, тихий канал привычек — удаляем при запуске (см. [createChannels]). */
        private const val OLD_CHANNEL_HABITS = "habits"

        /** Канал лекарств. */
        const val CHANNEL_MEDICATIONS = "medication_reminders"

        /** Номер пробного уведомления. У задач номера с 1, поэтому 0 ни с одной не совпадёт. */
        const val TEST_NOTIFICATION_ID = 0

        /**
         * Метка (tag) уведомлений о днях повторений. Уведомление в Android узнаётся по паре
         * «метка + номер»: у задачи 5 и повторения 5 номер один, а метки разные — они не заменят друг друга.
         * У уведомлений задач метки нет (null).
         */
        private const val TAG_RULE = "rule"

        /** Метка уведомлений о приёмах лекарств: номер у них — id лекарства (см. [TAG_RULE]). */
        private const val TAG_MEDICATION = "medication"

        /** Какой день повторения в уведомлении — в его «дополнительных данных» (extras). */
        private const val EXTRA_DAY = "planner.day"

        /** Какое время приёма лекарства в уведомлении — секундами от полуночи. */
        private const val EXTRA_TIME = "planner.time"

        /** Каналы напоминаний: их уведомления [dismissAllExcept] убирает, когда те больше не нужны. */
        private val REMINDER_CHANNELS = setOf(CHANNEL_TASKS, CHANNEL_HABITS, CHANNEL_MEDICATIONS)
    }

    /** Создать все каналы (или обновить их названия). Зовёт PlannerApplication при каждом запуске. */
    fun createChannels() {
        val tasks = NotificationChannel(
            CHANNEL_TASKS,
            context.getString(R.string.channel_tasks),
            // HIGH — уведомление звучит и всплывает поверх экрана
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_tasks_desc) }
        val habits = NotificationChannel(
            CHANNEL_HABITS,
            context.getString(R.string.channel_habits),
            // DEFAULT — звучит, но не всплывает поверх экрана
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_habits_desc) }
        val medications = NotificationChannel(
            CHANNEL_MEDICATIONS,
            context.getString(R.string.channel_medications),
            // HIGH — как у дел: звучит и всплывает поверх экрана
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_medications_desc) }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(tasks, habits, medications))
        // Нет такого канала — Android просто ничего не сделает, поэтому удалять можно при каждом запуске
        manager.deleteNotificationChannel(OLD_CHANNEL_HABITS)
    }

    /**
     * Общая основа всех напоминаний: значок, текст, «нажали — убралось», тап открывает приложение.
     * [sound] — выбор человека в настройках приложения: false — тихо, только в шторке (см. описание класса).
     */
    private fun builder(
        channel: String,
        title: String,
        text: String?,
        tapId: Long,
        sound: Boolean,
    ): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(tapId))
            .setAutoCancel(true) // нажали — уведомление убирается
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(!sound)

    /**
     * Показать напоминание о [task]: заголовок — название задачи, ниже — её время и начало заметки
     * («10:30 · каб. 214»). Тап по уведомлению открывает приложение.
     *
     * `suspend` — потому что сначала читаем настройки (со звуком или без), а это не мгновенно.
     */
    suspend fun show(task: Task) {
        val text =
            listOfNotNull(task.time?.let(DateTexts::time), task.notePreview).joinToString(" · ")
        val notification = builder(
            CHANNEL_TASKS, task.title, text.ifEmpty { null }, task.id, sound = settings().tasksSound,
        )
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
    suspend fun showTest() {
        // Как напоминание о деле: тот же канал и тот же выбор звука — проверка должна быть честной
        val notification = builder(
            CHANNEL_TASKS,
            context.getString(R.string.test_notification_title),
            context.getString(R.string.test_notification_text),
            TEST_NOTIFICATION_ID.toLong(),
            sound = settings().tasksSound,
        ).build()
        post(tag = null, id = TEST_NOTIFICATION_ID, notification)
    }

    /**
     * Напоминание о дне повторения или привычки: «Полить цветы» и ниже «18:00 · фикус»
     * (время и дела этого дня у цикла). Кнопки те же, что у задачи: «Готово» отмечает день,
     * «Отложить» — напомнит через 10 минут.
     *
     * Привычки идут в канал [CHANNEL_HABITS] и звучат, только если так выбрано ([PlannerSettings.habitsSound]);
     * остальные повторения — как задачи: канал дел [CHANNEL_TASKS] и [PlannerSettings.tasksSound].
     */
    suspend fun showRuleDay(occurrence: Occurrence) {
        val rule = occurrence.rule
        val text =
            listOfNotNull(rule.time?.let(DateTexts::time), occurrence.details).joinToString(" · ")
        val settings = settings()
        val notification = builder(
            channel = if (rule.isHabit) CHANNEL_HABITS else CHANNEL_TASKS,
            title = rule.title,
            text = text.ifEmpty { null },
            tapId = rule.id,
            sound = if (rule.isHabit) settings.habitsSound else settings.tasksSound,
        )
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
     * Напоминание о приёме лекарства: «Витамин D» и ниже «21:00 · 1 капсула · после еды».
     * Кнопки: «Принято» — отметить приём, «Отложить» — напомнить через 10 минут.
     *
     * Если включён повтор, то же уведомление показывается ещё раз через 10, 20 и 30 минут (см.
     * MedicationReminders). Это то же самое уведомление (метка + id лекарства), поэтому в шторке
     * оно не множится, а снова звучит. Смахнули — сработает deleteIntent, и повторы прекратятся.
     */
    suspend fun showDose(dose: Dose) {
        val medication = dose.medication
        val text = listOfNotNull(
            DateTexts.time(dose.time),
            medication.dose.ifBlank { null },
            medication.food?.let { context.getString(foodRes(it)) },
        ).joinToString(" · ")
        val notification = builder(
            CHANNEL_MEDICATIONS, medication.name, text, medication.id, sound = settings().medicationsSound,
        )
            // Какой это приём — по нему ReminderSync уберёт уведомление, когда приём отметят на экране
            .addExtras(Bundle().apply {
                putLong(EXTRA_DAY, dose.date.toEpochDay())
                putInt(EXTRA_TIME, dose.time.toSecondOfDay())
            })
            // Смахнули уведомление — человек его видел: больше не повторяем
            .setDeleteIntent(doseAction(dose, ReminderReceiver.ACTION_DOSE_DISMISSED))
            .addAction(0, context.getString(R.string.action_taken), doseAction(dose, ReminderReceiver.ACTION_DOSE_TAKEN))
            .addAction(
                0,
                context.getString(R.string.notification_snooze),
                doseAction(dose, ReminderReceiver.ACTION_DOSE_SNOOZE),
            )
            .build()
        // Метка + id лекарства: следующее напоминание того же лекарства заменит это
        post(tag = TAG_MEDICATION, id = medication.id.toInt(), notification)
    }

    /** Настройки как сейчас — один раз, без подписки (first — «первое значение потока»). */
    private suspend fun settings(): PlannerSettings = settingsRepository.settings.first()

    /**
     * Показать готовое уведомление. Единственное место, где мы зовём notify, — поэтому и проверка
     * разрешения (Android 13+) здесь одна на всех. Без разрешения просто ничего не показываем.
     *
     * Проверка — прямо здесь, рядом с notify, а не в отдельной функции: Android Studio
     * (проверка Lint «MissingPermission») видит её, только если она в той же функции, что и вызов,
     * которому разрешение нужно.
     *
     * @param tag метка: у задач null, у дней повторений [TAG_RULE], у лекарств [TAG_MEDICATION] —
     *   по паре «метка + номер» Android узнаёт уведомление, чтобы заменить или убрать его.
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

    /** Убрать уведомление лекарства [medicationId] — после «Принято» или «Отложить». */
    fun dismissDose(medicationId: Long) =
        NotificationManagerCompat.from(context).cancel(TAG_MEDICATION, medicationId.toInt())

    /**
     * Убрать уведомления, о которых напоминать больше не нужно: о задачах — всех, кроме [keepTaskIds];
     * о днях повторений — тех, для которых [keepRuleDay] ответит «нет».
     *
     * Зачем: пришло «Позвонить бабушке», а отметили её в приложении (или удалили, вернули в список,
     * убрали напоминание). Уведомление про уже сделанное дело в шторке ни к чему. Зовёт ReminderSync
     * при каждом изменении — он как раз знает, у каких задач напоминание ещё в силе.
     *
     * С днями повторений так же: [keepRuleDay] отвечает, ещё ли актуален день (его не отметили,
     * не перенесли, напоминание у повторения не убрали). С лекарствами — [keepDose]: приём не отметили
     * и не смахнули, лекарство не удалили.
     *
     * activeNotifications — уведомления нашего приложения, которые сейчас висят в шторке.
     * Берём только из каналов напоминаний: если позже появятся другие (вечерняя сводка), их не трогаем.
     */
    fun dismissAllExcept(
        keepTaskIds: Set<Long>,
        keepRuleDay: (ruleId: Long, date: LocalDate) -> Boolean,
        keepDose: (DoseKey) -> Boolean,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications
            // Пробное уведомление не трогаем: задачи у него нет, но оно должно повисеть, пока его не увидят
            .filter { it.notification.channelId in REMINDER_CHANNELS && it.id != TEST_NOTIFICATION_ID }
            .forEach { shown ->
                val extras = shown.notification.extras
                val keep = when (shown.tag) {
                    TAG_RULE -> keepRuleDay(shown.id.toLong(), LocalDate.ofEpochDay(extras.getLong(EXTRA_DAY)))
                    TAG_MEDICATION -> keepDose(
                        DoseKey(
                            shown.id.toLong(),
                            LocalDate.ofEpochDay(extras.getLong(EXTRA_DAY)),
                            LocalTime.ofSecondOfDay(extras.getInt(EXTRA_TIME).toLong()),
                        ),
                    )

                    else -> shown.id.toLong() in keepTaskIds
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

    /**
     * Кнопка уведомления о приёме (и «смахнули» — deleteIntent): тот же приёмник, с лекарством,
     * днём и временем приёма. Номер — id лекарства, а различает «намерения» действие (action).
     */
    private fun doseAction(dose: Dose, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            dose.medication.id.toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .putExtra(ReminderReceiver.EXTRA_MEDICATION_ID, dose.medication.id)
                .putExtra(ReminderReceiver.EXTRA_DATE, dose.date.toEpochDay())
                .putExtra(ReminderReceiver.EXTRA_TIME, dose.time.toSecondOfDay()),
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
