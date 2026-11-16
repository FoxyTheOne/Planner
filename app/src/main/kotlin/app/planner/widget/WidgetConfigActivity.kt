package app.planner.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import app.planner.R
import app.planner.common.DateTexts
import app.planner.common.SwitchRow
import app.planner.core.designsystem.component.PaperChip
import app.planner.core.designsystem.component.SectionLabel
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.repository.ISettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/**
 * Окно настройки виджета «Сегодня». Система открывает его, когда виджет добавляют на рабочий стол,
 * а на Android 12+ — ещё и по долгому нажатию на виджет («Настроить»). Так задумано в
 * res/xml/planner_widget_info.xml (android:configure и widgetFeatures="reconfigurable").
 *
 * Как это устроено в Android: окно получает номер виджета (EXTRA_APPWIDGET_ID). Пока человек не нажал
 * «Готово», результат — RESULT_CANCELED: если он передумает и закроет окно, виджет не добавится.
 * «Готово» — записываем настройки в состояние этого виджета, перерисовываем его и отвечаем RESULT_OK.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: ISettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Сначала «отмена»: закрыли окно, не нажав «Готово», — виджет не появится
        setResult(RESULT_CANCELED, resultIntent(widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val manager = GlanceAppWidgetManager(this)

        lifecycleScope.launch {
            val glanceId = manager.getGlanceIdBy(widgetId)
            // Настраивают уже стоящий виджет — показываем то, что выбрано сейчас
            val current = WidgetConfig.read(getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId))
            val settings = settingsRepository.settings.first()
            setContent {
                PlannerTheme(settings.themeMode, settings.darkVariant, settings.systemColors) {
                    WidgetConfigScreen(
                        initial = current,
                        eveningTime = settings.eveningSummaryTime,
                        onDone = { config ->
                            lifecycleScope.launch {
                                // Glance отдаёт старые настройки только для чтения; возвращаем изменённую копию
                                updateAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId) {
                                    it.toMutablePreferences().apply { config.writeTo(this) }
                                }
                                PlannerWidget().update(this@WidgetConfigActivity, glanceId)
                                setResult(RESULT_OK, resultIntent(widgetId))
                                finish()
                            }
                        },
                    )
                }
            }
        }
    }

    /** Ответ системе: какой виджет настраивали. */
    private fun resultIntent(widgetId: Int) =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}

/**
 * Сами настройки: когда показывать завтра, лекарства и привычки. Вид — как у редакторов приложения:
 * кнопки-выбор (PaperChip) и строки с переключателем (SwitchRow).
 *
 * @param eveningTime час вечерней сводки — с него «вечером» виджет показывает и завтра.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetConfigScreen(initial: WidgetConfig, eveningTime: LocalTime, onDone: (WidgetConfig) -> Unit) {
    // rememberSaveable — выбранное переживёт поворот экрана (окно при этом создаётся заново)
    var config by rememberSaveable(stateSaver = WidgetConfigSaver) { mutableStateOf(initial) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.titleLarge)

            SectionLabel(stringResource(R.string.widget_tomorrow), modifier = Modifier.padding(top = 16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            ) {
                listOf(
                    TomorrowMode.EVENING to stringResource(R.string.widget_tomorrow_evening, DateTexts.time(eveningTime)),
                    TomorrowMode.ALWAYS to stringResource(R.string.widget_tomorrow_always),
                    TomorrowMode.NEVER to stringResource(R.string.widget_tomorrow_never),
                ).forEach { (mode, label) ->
                    PaperChip(label, config.tomorrow == mode, { config = config.copy(tomorrow = mode) })
                }
            }

            SwitchRow(
                icon = Icons.Outlined.Medication,
                title = stringResource(R.string.widget_show_medications),
                hint = stringResource(R.string.widget_show_medications_hint),
                checked = config.medications,
                onCheckedChange = { config = config.copy(medications = it) },
            )
            SwitchRow(
                icon = Icons.Outlined.SelfImprovement,
                title = stringResource(R.string.widget_show_habits),
                hint = stringResource(R.string.widget_show_habits_hint),
                checked = config.habits,
                onCheckedChange = { config = config.copy(habits = it) },
            )

            Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.End) {
                Button(onClick = { onDone(config) }) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
}

/**
 * Как сохранить [WidgetConfig] на время поворота экрана: разложить в список простых значений
 * (название варианта «завтра» и два переключателя) и собрать обратно.
 */
private val WidgetConfigSaver = listSaver<WidgetConfig, Any>(
    save = { listOf(it.tomorrow.name, it.medications, it.habits) },
    restore = { WidgetConfig(TomorrowMode.valueOf(it[0] as String), it[1] as Boolean, it[2] as Boolean) },
)
