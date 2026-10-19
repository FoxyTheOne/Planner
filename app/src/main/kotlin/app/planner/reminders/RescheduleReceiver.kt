package app.planner.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Переставляет будильники, когда Android их потерял или они «съехали»:
 * - BOOT_COMPLETED — телефон перезагрузили: после перезагрузки система забывает все будильники;
 * - TIME_SET, TIMEZONE_CHANGED — человек поменял время или прилетел в другой часовой пояс:
 *   будильник стоит на «момент по всемирному времени», а «10:00 по часам телефона» теперь другой момент;
 * - MY_PACKAGE_REPLACED — приложение обновили: на всякий случай ставим заново.
 * - SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED — разрешили «Будильники и напоминания» (Android 12+):
 *   будильники, поставленные «неточными», переставляем на точное время.
 *
 * Какие сигналы ловить, перечислено в AndroidManifest.xml (intent-filter у этого приёмника).
 */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {

    @Inject
    lateinit var sync: ReminderSync

    override fun onReceive(context: Context, intent: Intent) {
        // Отвечаем только на «свои» сигналы из манифеста. Снаружи приёмник и так недоступен
        // (exported="false"), но проверить, что пришло, — дёшево и надёжно
        if (intent.action !in RESCHEDULE_ACTIONS) return
        val pending = goAsync() // см. ReminderReceiver: работа с базой не мгновенная
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Как в ReminderSync: сбой при перестановке будильников не должен ронять приложение
                runCatching { sync.syncNow() }
                    .onFailure {
                        Log.e(
                            "RescheduleReceiver",
                            "Не удалось переставить будильники",
                            it
                        )
                    }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED, // это и есть android.intent.action.TIME_SET
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // Строкой, а не AlarmManager.ACTION_…: константа появилась только в Android 12
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
