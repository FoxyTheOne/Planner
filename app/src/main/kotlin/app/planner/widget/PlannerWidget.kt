package app.planner.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.planner.MainActivity
import app.planner.R
import app.planner.common.DateTexts
import app.planner.common.DayItem
import app.planner.common.asTask
import app.planner.common.foodRes
import app.planner.core.designsystem.theme.PaperColors
import app.planner.core.designsystem.theme.paperColors
import app.planner.core.domain.model.PlannerSettings
import app.planner.core.domain.model.ThemeMode
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Виджет «Сегодня» на рабочем столе: план дня с галочками, приёмы лекарств и привычки (по настройке),
 * а вечером (или всегда) — и завтрашний день. Галочки отмечают прямо на виджете (WidgetActions),
 * тап по строке открывает приложение.
 *
 * GLANCE. Виджет рисует не приложение, а сам рабочий стол — и только из простых «кирпичиков»
 * (RemoteViews). Jetpack Glance разрешает описывать их почти как обычный Compose: Column, Row, Text.
 * Но это другие классы (androidx.glance.*), и умеют они меньше: например, нет FlowRow и своих шрифтов.
 *
 * КАК ОБНОВЛЯЕТСЯ. Пока виджет «живой», он подписан на данные (см. [provideGlance]). А «будят» его
 * WidgetUpdater (когда в базе что-то поменялось), окно настройки и сама система
 * раз в полчаса (updatePeriodMillis в res/xml/planner_widget_info.xml) — так виджет сам перейдёт
 * на новый день и вечером покажет завтра.
 */
class PlannerWidget : GlanceAppWidget() {

    // Настройки каждого виджета (WidgetConfig) Glance хранит в маленьком файле DataStore Preferences
    override val stateDefinition = PreferencesGlanceStateDefinition

    /**
     * Glance зовёт это, когда виджет «просыпается» (его добавили, обновили, система решила перерисовать).
     * Потом виджет какое-то время «живой» (около минуты): обновления в это время не вызывают provideGlance
     * заново, а только перерисовывают уже работающий provideContent. Поэтому и данные, и настройки
     * читаем внутри него, подпиской:
     * - данные — collectAsState: поменялось в базе — виджет перерисуется сам;
     * - настройки виджета — currentState(): Glance подставляет свежие после updateAppWidgetState.
     *
     * Первые данные берём заранее (first()), чтобы виджет сразу появился с ними, а не пустым.
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val source = observeWidgetSource(entry, LocalDate.now())
        val first = source.first()
        provideContent {
            val data by source.collectAsState(initial = first)
            val config = WidgetConfig.read(currentState<Preferences>())
            WidgetBody(data.toContent(config, LocalDateTime.now()))
        }
    }
}

/** Приёмник виджета: через него система сообщает о виджете (добавили, пора обновить). Объявлен в манифесте. */
class PlannerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlannerWidget()
}

/**
 * Цвета виджета — та же «бумага», что в приложении (paperColors). ColorProvider(day, night) — цвет
 * «днём» и «ночью»: при теме «как в системе» рабочий стол сам выберет нужный, когда телефон
 * переключится на тёмную тему. Выбрана светлая или тёмная тема — оба цвета одинаковые.
 */
private class WidgetPalette(settings: PlannerSettings) {
    private val light = paperColors(dark = false, settings.darkVariant)
    private val dark = paperColors(dark = true, settings.darkVariant)
    private val day = if (settings.themeMode == ThemeMode.DARK) dark else light
    private val night = if (settings.themeMode == ThemeMode.LIGHT) light else dark

    private fun of(pick: (PaperColors) -> Color): ColorProvider = ColorProvider(pick(day), pick(night))

    val paper = of { it.paper }
    val ink = of { it.ink }
    val muted = of { it.inkMuted }
    val faint = of { it.inkFaint }
    val accent = of { it.timeAccent }
}

@Composable
private fun WidgetBody(content: WidgetContent) {
    val context = LocalContext.current
    val palette = WidgetPalette(content.settings)
    val openApp = actionStartActivity<MainActivity>()
    Column(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(palette.paper)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        LazyColumn(GlanceModifier.fillMaxSize()) {
            item {
                DayTitle(
                    context.getString(R.string.widget_day_title, context.getString(R.string.day_today),
                        DateTexts.shortDay(content.today.date)),
                    palette,
                    GlanceModifier.clickable(openApp),
                )
            }
            if (content.today.isEmpty) {
                item { Muted(context.getString(R.string.widget_empty), palette) }
            }
            dayRows(content.today, palette, context, openApp)
            content.tomorrow?.let { tomorrow ->
                item {
                    DayTitle(
                        context.getString(R.string.widget_day_title, context.getString(R.string.day_tomorrow),
                            DateTexts.shortDay(tomorrow.date)),
                        palette,
                        GlanceModifier.padding(top = 10.dp).clickable(openApp),
                    )
                }
                dayRows(tomorrow, palette, context, openApp)
            }
        }
    }
}

/**
 * Строки одного дня: сначала привычки и лекарства (если включены в настройках виджета) — маленькими
 * разделами, потом дела. Порядок тот же, что на «Сегодня»: привычки и лекарства над планом дня.
 */
private fun androidx.glance.appwidget.lazy.LazyListScope.dayRows(
    day: WidgetDay,
    palette: WidgetPalette,
    context: Context,
    openApp: androidx.glance.action.Action,
) {
    if (day.habits.isNotEmpty()) {
        item { Section(context.getString(R.string.widget_habits_section), palette) }
        day.habits.forEach { habit ->
            item {
                CheckRow(
                    habit.isDone, habit.rule.title, null, null, palette, openApp,
                    onCheck = toggleHabit(habit.rule.id, habit.occurrence.date),
                )
            }
        }
    }
    if (day.doses.isNotEmpty()) {
        item { Section(context.getString(R.string.medications_section), palette) }
        day.doses.forEach { dose ->
            val medication = dose.medication
            val subtitle = listOfNotNull(
                medication.dose.ifBlank { null },
                medication.food?.let { context.getString(foodRes(it)) },
            ).joinToString(" · ").ifEmpty { null }
            item {
                CheckRow(
                    dose.isTaken, medication.name, DateTexts.time(dose.time), subtitle, palette, openApp,
                    onCheck = toggleDose(medication.id, dose.date, dose.time),
                )
            }
        }
    }
    // Дела — своим разделом «план», если выше были привычки или лекарства (иначе подпись лишняя)
    if ((day.habits.isNotEmpty() || day.doses.isNotEmpty()) && day.items.isNotEmpty()) {
        item { Section(context.getString(R.string.widget_plan_section), palette) }
    }
    day.items.forEach { dayItem ->
        val task = dayItem.asTask()
        // Задача (и уже отмеченный день повторения) — переключить; день повторения по правилу — отметить
        val onCheck = when (dayItem) {
            is DayItem.TaskItem -> toggleTask(dayItem.task.id)
            is DayItem.RepeatItem -> completeRepeatDay(dayItem.occurrence.rule.id, dayItem.occurrence.date)
        }
        item {
            CheckRow(
                checked = dayItem.isDone,
                title = task.title + if (task.repeatRuleId != null) "  ↻" else "",
                time = task.time?.let(DateTexts::time),
                subtitle = null,
                palette = palette,
                onClick = openApp,
                onCheck = onCheck,
            )
        }
    }
}

/** «Сегодня · Сб 24» — заголовок дня. */
@Composable
private fun DayTitle(text: String, palette: WidgetPalette, modifier: GlanceModifier = GlanceModifier) {
    Text(
        text,
        style = TextStyle(color = palette.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium),
        modifier = modifier.padding(bottom = 4.dp),
    )
}

/** «лекарства», «привычки» — подпись раздела, как на «Сегодня». */
@Composable
private fun Section(text: String, palette: WidgetPalette) {
    Text(text, style = TextStyle(color = palette.muted, fontSize = 13.sp), modifier = GlanceModifier.padding(top = 6.dp))
}

@Composable
private fun Muted(text: String, palette: WidgetPalette) {
    Text(text, style = TextStyle(color = palette.muted, fontSize = 14.sp), modifier = GlanceModifier.padding(vertical = 4.dp))
}

/**
 * Строка с галочкой: «☐ 10:30 Врач». Сделанное — бледнее и зачёркнуто, как на «Сегодня».
 * Галочка ([onCheck]) отмечает прямо на виджете (см. WidgetActions), тап по строке ([onClick]) — открывает приложение.
 */
@Composable
private fun CheckRow(
    checked: Boolean,
    title: String,
    time: String?,
    subtitle: String?,
    palette: WidgetPalette,
    onClick: androidx.glance.action.Action,
    onCheck: androidx.glance.action.Action,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick),
    ) {
        CheckBox(
            checked = checked,
            onCheckedChange = onCheck,
            colors = CheckboxDefaults.colors(checkedColor = palette.accent, uncheckedColor = palette.muted),
        )
        time?.let {
            Text(
                it,
                style = TextStyle(color = if (checked) palette.faint else palette.accent, fontSize = 14.sp),
                modifier = GlanceModifier.padding(end = 6.dp),
            )
        }
        Column(GlanceModifier.defaultWeight()) {
            Text(
                title,
                maxLines = 2,
                style = TextStyle(
                    color = if (checked) palette.faint else palette.ink,
                    fontSize = 14.sp,
                    textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                ),
            )
            subtitle?.let {
                Text(it, maxLines = 1, style = TextStyle(color = palette.muted, fontSize = 12.sp))
            }
        }
    }
}
