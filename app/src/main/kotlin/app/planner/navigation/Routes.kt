package app.planner.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import app.planner.R
import java.time.LocalDate

/*
 * АДРЕСА ЭКРАНОВ (routes)
 *
 * В Compose Navigation у каждого экрана есть «адрес» — обычная строка, как путь на сайте.
 * Чтобы открыть экран, говорим навигации: navController.navigate("settings").
 * Навигация находит в NavHost (см. PlannerApp.kt) экран с таким адресом и показывает его,
 * а предыдущий запоминает — чтобы кнопка «Назад» вернула к нему.
 *
 * Если экрану нужны данные (какой день открыть, какой список), они передаются прямо в адресе:
 *     шаблон  "day/{epochDay}"   — {epochDay} это «дырка» для значения;
 *     адрес   "day/20720"        — открыть день номер 20720.
 * Навигация сама сопоставит адрес с шаблоном и положит 20720 в аргумент epochDay.
 *
 * Все адреса собраны здесь, в одном файле: шаблон и функция, которая строит адрес,
 * лежат рядом, поэтому их невозможно случайно написать по-разному.
 */

/**
 * Вкладки нижней панели: адрес экрана, подпись и иконка.
 * `enum class` — фиксированный набор вариантов; порядок здесь = порядок кнопок на панели.
 *
 * @param home первый экран вкладки, если внутри неё несколько экранов: повторное нажатие
 *   на вкладку возвращает к нему. null — у вкладки один экран.
 */
enum class Tab(val route: String, val label: Int, val icon: ImageVector, val home: String? = null) {
    TODAY("today", R.string.nav_today, Icons.Outlined.WbSunny),
    WEEK("week", R.string.nav_week, Icons.Outlined.CalendarViewWeek),

    // У «Списков» внутри два экрана (оглавление и один список), поэтому у вкладки
    // адрес не экрана, а «вложенного графа» — группы экранов (см. navigation(...) в PlannerApp.kt)
    LISTS("lists_tab", R.string.nav_lists, Icons.Outlined.Checklist, home = Routes.LISTS_OVERVIEW),

    // «Цели» — так же: все цели и экран одной цели
    GOALS("goals_tab", R.string.nav_goals, Icons.Outlined.Flag, home = Routes.GOALS_OVERVIEW),
    DIARY("diary", R.string.nav_diary, Icons.Outlined.EditNote),
}

/** Адреса экранов, которых нет на нижней панели. */
object Routes {
    const val SETTINGS = "settings"

    /** Экран «Привычки» — открывается кнопкой «Все привычки» на «Сегодня». */
    const val HABITS = "habits"

    /** Страница «Надёжность напоминаний» — открывается из настроек. */
    const val REMINDER_RELIABILITY = "settings/reminders"
    const val LISTS_OVERVIEW = "lists"

    // --- Экран одного дня ---

    /** Имя аргумента: какой день открыть. */
    const val ARG_EPOCH_DAY = "epochDay"

    /**
     * Шаблон адреса дня. День передаём числом «epoch day» — сколько дней прошло с 1 января 1970:
     * адрес — это строка, а число в строку и обратно переводится просто и без ошибок,
     * в отличие от «24.09.2026», где важен формат и язык.
     */
    const val DAY = "day/{$ARG_EPOCH_DAY}"

    /** Готовый адрес конкретного дня, например "day/20720". */
    fun day(date: LocalDate): String = "day/${date.toEpochDay()}"

    // --- Экран одного списка ---

    /** Имя аргумента: какой список открыть (его id в базе). */
    const val ARG_LIST_ID = "listId"

    /** Шаблон адреса списка. */
    const val LIST_DETAIL = "list/{$ARG_LIST_ID}"

    /** Готовый адрес конкретного списка, например "list/4". */
    fun list(id: Long): String = "list/$id"

    // --- Цели ---

    const val GOALS_OVERVIEW = "goals"

    /** Имя аргумента: какую цель открыть (её id в базе). */
    const val ARG_GOAL_ID = "goalId"

    /** Шаблон адреса цели. */
    const val GOAL_DETAIL = "goal/{$ARG_GOAL_ID}"

    /** Готовый адрес конкретной цели, например "goal/2". */
    fun goal(id: Long): String = "goal/$id"
}

//1. На «Неделе» нажали на день → navController.navigate(Routes.day(date)) → получается адрес "day/20720".
//2. Навигация сравнивает адрес с шаблонами и видит, что подходит "day/{epochDay}".
//3. В arguments = listOf(navArgument("epochDay") { type = NavType.LongType }) мы заранее сказали, что в этой «дырке» число. Навигация вынимает 20720 и кладёт в entry.arguments.
//4. Мы достаём число: LocalDate.ofEpochDay(20720) → 24 сентября 2026, и передаём его в DayScreen.
//
//С экраном списка "list/{listId}" всё так же, только id списка достаёт уже ViewModel через SavedStateHandle.