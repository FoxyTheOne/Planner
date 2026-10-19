package app.planner.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.planner.R
import app.planner.common.UiMessage
import app.planner.common.resolve
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.Reminder
import app.planner.feature.goals.GoalScreen
import app.planner.feature.goals.GoalsScreen
import app.planner.feature.habits.HabitsScreen
import app.planner.feature.lists.ListDetailScreen
import app.planner.feature.lists.ListsOverviewScreen
import app.planner.feature.settings.ReliabilityScreen
import app.planner.feature.settings.SettingsScreen
import app.planner.feature.today.TodayScreen
import app.planner.feature.week.DayScreen
import app.planner.feature.week.WeekScreen
import app.planner.reminders.ExactAlarmOfferDialog
import app.planner.reminders.ReminderQuestion
import app.planner.reminders.ReminderQuestionDialog
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/*
 * КАРКАС ПРИЛОЖЕНИЯ И НАВИГАЦИЯ
 *
 * В приложении одна Activity, а экраны — composable-функции. «Перейти на другой экран» —
 * значит попросить NavHost нарисовать другую функцию на месте текущей.
 *
 * Главные действующие лица:
 * - NavController (navController) — «пульт» навигации. Хранит историю экранов (back stack)
 *   и умеет: navigate("адрес") — открыть экран поверх текущего; popBackStack() — вернуться назад.
 * - NavHost — «рамка», в которой рисуется текущий экран, и одновременно карта:
 *   какой адрес какой функции соответствует (см. PlannerNavHost ниже).
 * - Scaffold — рама Material 3 вокруг экранов: снизу панель вкладок, над ней место для Snackbar.
 *
 * Адреса экранов описаны в Routes.kt.
 */

/**
 * Каркас всего приложения: Scaffold с нижней панелью вкладок, общий Snackbar и NavHost с экранами.
 *
 * @param messages сообщения для Snackbar («Перенесено на завтра · Отменить») со всех экранов.
 * @param onUndo что сделать, когда в Snackbar нажали «Отменить».
 * @param reminderQuestions вопросы «задачу перенесли — что с напоминанием?»; [onReminderAnswer] — ответ на них.
 */
@Composable
fun PlannerApp(
    messages: SharedFlow<UiMessage>,
    onUndo: (suspend () -> Unit) -> Unit,
    reminderQuestions: SharedFlow<ReminderQuestion>,
    onReminderAnswer: (taskId: Long, reminder: Reminder?) -> Unit,
    showExactAlarmOffer: Boolean,
    onExactAlarmOfferClosed: () -> Unit,
) {
    // rememberNavController создаёт «пульт» навигации один раз и помнит его между перерисовками
    val navController = rememberNavController()

    // Состояние Snackbar: какое сообщение сейчас показано. SnackbarHost (ниже) рисует его,
    // а SnackbarMessages кладёт в него новые сообщения
    val snackbar = remember { SnackbarHostState() }
    SnackbarMessages(messages = messages, snackbar = snackbar, onUndo = onUndo)
    // Окно «задачу перенесли — что с напоминанием?» — тоже одно на всё приложение
    ReminderQuestionDialog(questions = reminderQuestions, onAnswer = onReminderAnswer)
    // Окно «Точное время» — один раз, после первого напоминания (см. MainViewModel.showExactAlarmOffer)
    if (showExactAlarmOffer) ExactAlarmOfferDialog(onClose = onExactAlarmOfferClosed)

    // Какой экран сейчас открыт. currentBackStackEntryAsState — «подписка»: при каждом переходе
    // эта переменная меняется, и Compose перерисовывает панель вкладок (подсветка, видимость)
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination

    // Показывать ли панель вкладок. Здесь перечислено, где панель ЕСТЬ, а не где её нет:
    // «открытый экран находится внутри какой-нибудь вкладки из Tab?»
    // - «Сегодня» (адрес "today") — да, это вкладка TODAY → панель видна;
    // - один список ("list/{listId}") — да, он внутри вкладки LISTS ("lists_tab") → видна;
    // - настройки ("settings") и день ("day/{epochDay}") — ни в одной вкладке их нет → скрыта.
    // Новый экран вне вкладок (например, будущий «Обзор недели») тоже будет без панели — сам.
    val showBar = Tab.entries.any { tab -> destination.isInside(tab) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) BottomTabs(navController = navController, destination = destination)
        },
    ) { padding ->
        // padding — отступы, которые Scaffold просит соблюдать: снизу высота панели вкладок,
        // сверху — строка состояния телефона. Каждый экран получает их как contentPadding
        PlannerNavHost(navController = navController, padding = padding)
    }
}

/**
 * Показывает сообщения из [messages] в Snackbar — по одному, новое вместо старого.
 *
 * Здесь ничего не рисуется: это «эффект», то есть код, который просто работает в фоне,
 * пока каркас приложения на экране.
 */
@Composable
private fun SnackbarMessages(
    messages: SharedFlow<UiMessage>,
    snackbar: SnackbarHostState,
    onUndo: (suspend () -> Unit) -> Unit,
) {
    // Строки берём заранее: внутри LaunchedEffect функции вроде stringResource вызывать нельзя
    // LocalResources, а не LocalContext.current.resources: если сменится язык или тема,
    // Compose узнает об этом и возьмёт строки заново
    val resources = LocalResources.current
    val undoLabel = stringResource(R.string.action_undo)

    // LaunchedEffect запускает корутину, когда эта функция впервые появляется на экране,
    // и отменяет её, когда функция исчезает. Unit — «запустить один раз и не перезапускать».
    LaunchedEffect(Unit) {
        // collect — «жди сообщения и на каждое выполняй блок». Этот цикл не заканчивается:
        // он ждёт новые сообщения всё время, пока приложение открыто.
        messages.collect { message ->
            // 1. Если какое-то сообщение ещё на экране — убираем его.
            //    Иначе новое встало бы в очередь и появилось только через несколько секунд,
            //    а «Отменить» должно относиться к последнему действию человека.
            snackbar.currentSnackbarData?.dismiss()

            // 2. Показываем новое. showSnackbar «ждёт», пока сообщение не исчезнет
            //    (само через несколько секунд, по кнопке или после dismiss), и только потом
            //    возвращает результат. Поэтому запускаем его в отдельной корутине (launch):
            //    иначе collect не смог бы принять следующее сообщение, пока висит текущее.
            launch {
                val result = snackbar.showSnackbar(
                    message = message.text.resolve(resources),
                    // Кнопка «Отменить» — только если у сообщения есть что отменять
                    actionLabel = if (message.undo != null) undoLabel else null,
                    duration = SnackbarDuration.Short,
                )
                // 3. ActionPerformed — нажали «Отменить». Тогда выполняем отмену.
                //    message.undo?.let(onUndo) — «если undo не null, передай его в onUndo».
                if (result == SnackbarResult.ActionPerformed) message.undo?.let(onUndo)
            }
        }
    }
}

/** Нижняя панель с пятью вкладками. */
@Composable
private fun BottomTabs(navController: NavHostController, destination: NavDestination?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            val selected = destination.isInside(tab)
            NavigationBarItem(
                selected = selected,
                onClick = {
                    val home = tab.home
                    if (selected && home != null) {
                        // Повторное нажатие на открытую вкладку «Списки» или «Цели» — назад к её первому экрану.
                        // popBackStack(адрес, inclusive = false) убирает из истории всё,
                        // что открыто поверх этого адреса, а сам адрес оставляет
                        navController.popBackStack(home, inclusive = false)
                    } else {
                        navController.navigate(tab.route) {
                            // Стандартный рецепт для вкладок:
                            // - popUpTo(начальный экран) — не копить историю: если пройти
                            //   Сегодня → Неделя → Списки, «Назад» вернёт на «Сегодня», а не по всем вкладкам;
                            // - saveState / restoreState — запомнить, где человек был внутри вкладки
                            //   (например, в списке «Книги»), и вернуть туда же при возврате на вкладку;
                            // - launchSingleTop — не открывать второй экземпляр той же вкладки.
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(tab.icon, null) },
                label = { Text(stringResource(tab.label)) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    unselectedIconColor = PlannerTheme.paper.inkMuted,
                    unselectedTextColor = PlannerTheme.paper.inkMuted,
                ),
            )
        }
    }
}

/**
 * Карта экранов: какой адрес какой функции соответствует.
 *
 * `composable("адрес") { ... }` — «когда открыт этот адрес, нарисуй это».
 * Каждому экрану передаём, что делать при переходах (например, onBack = popBackStack) —
 * сами экраны про навигацию ничего не знают, так их проще переиспользовать и показывать в превью.
 */
@Composable
private fun PlannerNavHost(navController: NavHostController, padding: PaddingValues) {
    NavHost(navController, startDestination = Tab.TODAY.route, modifier = Modifier.fillMaxSize()) {
        composable(Tab.TODAY.route) {
            TodayScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenHabits = { navController.navigate(Routes.HABITS) },
                contentPadding = padding
            )
        }

        composable(Tab.WEEK.route) {
            WeekScreen(
                onOpenDay = { date -> navController.navigate(Routes.day(date)) },
                contentPadding = padding
            )
        }

        // Экран дня: адрес "day/{epochDay}". В arguments объясняем навигации, что в {epochDay}
        // лежит число (LongType) — тогда она сама вынет его из адреса "day/20720" и положит в entry.arguments
        composable(
            Routes.DAY,
            arguments = listOf(navArgument(Routes.ARG_EPOCH_DAY) { type = NavType.LongType }),
        ) { entry ->
            val epochDay = entry.arguments!!.getLong(Routes.ARG_EPOCH_DAY)
            DayScreen(
                date = LocalDate.ofEpochDay(epochDay),
                onBack = navController::popBackStack,
                contentPadding = padding,
            )
        }

        // Вкладка «Списки» — вложенный граф: группа из двух экранов под одним адресом вкладки.
        // Нижняя панель считает вкладку открытой на обоих (см. isInside)
        navigation(route = Tab.LISTS.route, startDestination = Routes.LISTS_OVERVIEW) {
            composable(Routes.LISTS_OVERVIEW) {
                ListsOverviewScreen(
                    onOpenList = { id -> navController.navigate(Routes.list(id)) },
                    contentPadding = padding,
                )
            }
            // id списка из адреса "list/4" навигация положит в аргумент listId,
            // а ListDetailViewModel достанет его через SavedStateHandle
            composable(
                Routes.LIST_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_LIST_ID) { type = NavType.LongType }),
            ) {
                ListDetailScreen(onBack = navController::popBackStack, contentPadding = padding)
            }
        }

        // Вкладка «Цели» — тоже вложенный граф: все цели и экран одной цели
        navigation(route = Tab.GOALS.route, startDestination = Routes.GOALS_OVERVIEW) {
            composable(Routes.GOALS_OVERVIEW) {
                GoalsScreen(
                    onOpenGoal = { id -> navController.navigate(Routes.goal(id)) },
                    contentPadding = padding,
                )
            }
            composable(
                Routes.GOAL_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_GOAL_ID) { type = NavType.LongType }),
            ) {
                GoalScreen(onBack = navController::popBackStack, contentPadding = padding)
            }
        }
        composable(Tab.DIARY.route) {
            ComingSoon(
                R.string.nav_diary,
                R.string.coming_soon_diary,
                padding
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = navController::popBackStack,
                onOpenReliability = { navController.navigate(Routes.REMINDER_RELIABILITY) },
                contentPadding = padding,
            )
        }
        composable(Routes.HABITS) {
            HabitsScreen(onBack = navController::popBackStack, contentPadding = padding)
        }
        composable(Routes.REMINDER_RELIABILITY) {
            ReliabilityScreen(onBack = navController::popBackStack, contentPadding = padding)
        }
    }
}

/**
 * Находится ли открытый экран внутри вкладки [tab].
 * hierarchy — экран и все группы, в которые он вложен: для экрана списка это
 * "list/{listId}" и "lists_tab", поэтому вкладка «Списки» подсвечена и там.
 */
private fun NavDestination?.isInside(tab: Tab): Boolean =
    this?.hierarchy?.any { it.route == tab.route } == true

/** Заглушка для разделов следующих этапов (сейчас — дневник). */
@Composable
private fun ComingSoon(title: Int, text: Int, padding: PaddingValues) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(20.dp)
    ) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            stringResource(text),
            color = PlannerTheme.paper.inkMuted,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
