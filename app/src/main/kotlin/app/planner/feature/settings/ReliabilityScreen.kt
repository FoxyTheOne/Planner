package app.planner.feature.settings

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.planner.R
import app.planner.common.DateTexts
import app.planner.core.designsystem.component.DashedRule
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.reminders.ReminderHealth
import java.time.LocalTime

/**
 * Что сейчас с настройками телефона, от которых зависят напоминания (см. ReminderHealth).
 *
 * @param exactAlarmsAsked есть ли на этом телефоне вообще разрешение «точное время» (Android 12+).
 *   На старых его нет — точные будильники можно всегда, и пункт не показываем.
 */
data class ReliabilityState(
    val notificationsOn: Boolean,
    val exactAlarmsOn: Boolean,
    val exactAlarmsAsked: Boolean,
    val unrestricted: Boolean,
) {
    companion object {
        fun read(context: Context) = ReliabilityState(
            notificationsOn = ReminderHealth.notificationsOn(context),
            exactAlarmsOn = ReminderHealth.exactAlarmsOn(context),
            exactAlarmsAsked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            unrestricted = ReminderHealth.unrestrictedInBackground(context),
        )
    }
}

/**
 * Страница «Надёжность напоминаний»: три проверки и пробное напоминание.
 *
 * У каждой проверки — что сейчас и кнопка, ведущая прямо в нужное место настроек Android.
 * Приложение само эти настройки поменять не может (и не должно): решает человек.
 * Когда он возвращается из настроек, страница перепроверяет всё заново (LifecycleResumeEffect).
 */
@Composable
fun ReliabilityScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: ReliabilityViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(ReliabilityState.read(context)) }
    LifecycleResumeEffect(Unit) {
        state = ReliabilityState.read(context)
        onPauseOrDispose { }
    }
    // Когда ждать пробное напоминание — чтобы написать «Ждите уведомление около 10:31»
    var testAt by rememberSaveable { mutableStateOf<LocalTime?>(null) }

    ReliabilityContent(
        state = state,
        testAt = testAt,
        contentPadding = contentPadding,
        onBack = onBack,
        onOpenNotifications = { context.startActivity(ReminderHealth.notificationSettings(context)) },
        onOpenExactAlarms = {
            // Кнопка есть только на Android 12+, но компилятор этого не знает — проверяем ещё раз
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.startActivity(ReminderHealth.exactAlarmSettings(context))
            }
        },
        onOpenAppSettings = { context.startActivity(ReminderHealth.appSettings(context)) },
        onSendTest = { testAt = viewModel.sendTest().toLocalTime() },
    )
}

/**
 * Рисующая часть страницы: только разметка, поэтому её можно показать в превью
 * (см. `SettingsPreviews.kt`).
 */
@Composable
fun ReliabilityContent(
    state: ReliabilityState,
    testAt: LocalTime?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenExactAlarms: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSendTest: () -> Unit,
) {
    val paper = PlannerTheme.paper
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.cd_back))
            }
            Text(
                stringResource(R.string.reliability_title),
                style = MaterialTheme.typography.titleLarge
            )
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                stringResource(R.string.reliability_intro),
                color = paper.inkMuted,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )

            CheckItem(
                title = stringResource(R.string.reliability_notifications),
                ok = state.notificationsOn,
                text = stringResource(
                    if (state.notificationsOn) R.string.reliability_notifications_on
                    else R.string.reliability_notifications_off,
                ),
                action = stringResource(R.string.reminder_notifications_enable),
                onAction = onOpenNotifications,
            )
            if (state.exactAlarmsAsked) {
                DashedRule()
                CheckItem(
                    title = stringResource(R.string.reliability_exact),
                    ok = state.exactAlarmsOn,
                    text = stringResource(
                        if (state.exactAlarmsOn) R.string.reliability_exact_on else R.string.reliability_exact_off,
                    ),
                    action = stringResource(R.string.reliability_allow),
                    onAction = onOpenExactAlarms,
                )
            }
            DashedRule()
            // Фоновая работа — «по желанию»: на большинстве телефонов она не нужна,
            // поэтому вместо «не в порядке» — спокойный значок «к сведению»
            CheckItem(
                title = stringResource(R.string.reliability_background),
                ok = state.unrestricted,
                optional = true,
                text = stringResource(
                    if (state.unrestricted) R.string.reliability_background_on else R.string.reliability_background_off,
                ),
                action = stringResource(R.string.reliability_open_settings),
                onAction = onOpenAppSettings,
            )
            DashedRule()

            Row(Modifier.padding(vertical = 14.dp)) {
                Icon(Icons.Outlined.NotificationsActive, null, tint = paper.inkMuted)
                Column(
                    Modifier
                        .padding(start = 16.dp)
                        .weight(1f)
                ) {
                    Text(
                        stringResource(R.string.reliability_test),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.reliability_test_text),
                        color = paper.inkMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onSendTest, modifier = Modifier.padding(top = 12.dp)) {
                        Text(stringResource(R.string.reliability_test_action))
                    }
                    testAt?.let {
                        Text(
                            stringResource(R.string.reliability_test_scheduled, DateTexts.time(it)),
                            color = paper.inkMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Одна проверка: значок, название, что сейчас и — если не в порядке — кнопка «исправить».
 *
 * Значки без красного (см. мягкий тон приложения): в порядке — галочка, не в порядке — «!»
 * цветом акцента, а для необязательного ([optional]) — нейтральное «i».
 */
@Composable
private fun CheckItem(
    title: String,
    ok: Boolean,
    text: String,
    action: String,
    onAction: () -> Unit,
    optional: Boolean = false,
) {
    val paper = PlannerTheme.paper
    val (icon: ImageVector, tint) = when {
        ok -> Icons.Outlined.CheckCircle to paper.inkMuted
        optional -> Icons.Outlined.Info to paper.inkMuted
        else -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.primary
    }
    Row(Modifier.padding(vertical = 14.dp)) {
        Icon(icon, null, tint = tint)
        Column(
            Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(text, color = paper.inkMuted, style = MaterialTheme.typography.bodyMedium)
            if (!ok) {
                // offset — сдвинуть кнопку влево на её собственный отступ, чтобы слово
                // стояло ровно под текстом, а не уезжало вправо
                TextButton(onClick = onAction, modifier = Modifier.offset(x = (-12).dp)) {
                    Text(
                        action
                    )
                }
            }
        }
    }
}
