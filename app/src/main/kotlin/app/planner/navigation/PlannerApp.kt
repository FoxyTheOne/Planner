package app.planner.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.WbSunny
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.planner.R
import app.planner.common.DailyMaintenance
import app.planner.common.MessageBus
import app.planner.common.TodayClock
import app.planner.common.resolve
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.feature.lists.ListDetailScreen
import app.planner.feature.lists.ListDetailViewModel
import app.planner.feature.lists.ListsOverviewScreen
import app.planner.feature.settings.SettingsScreen
import app.planner.feature.today.TodayScreen
import app.planner.feature.week.DayScreen
import app.planner.feature.week.WeekScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Вкладки нижней панели: адрес экрана, подпись и иконка. */
private enum class Tab(val route: String, val label: Int, val icon: ImageVector) {
    TODAY("today", R.string.nav_today, Icons.Outlined.WbSunny),
    WEEK("week", R.string.nav_week, Icons.Outlined.CalendarViewWeek),
    // У «Списков» внутри два экрана (оглавление и список), поэтому адрес вкладки — адрес вложенного графа
    LISTS("lists_tab", R.string.nav_lists, Icons.Outlined.Checklist),
    GOALS("goals", R.string.nav_goals, Icons.Outlined.Flag),
    DIARY("diary", R.string.nav_diary, Icons.Outlined.EditNote),
}

private const val ROUTE_DAY = "day/{epochDay}"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_LISTS_OVERVIEW = "lists"
private const val ROUTE_LIST_DETAIL = "list/{${ListDetailViewModel.ARG_LIST_ID}}"

/**
 * ViewModel всего приложения (а не одного экрана): запускает ежедневную уборку хвостов
 * и отдаёт общую шину сообщений, из которой [PlannerApp] показывает Snackbar с «Отменить».
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    val messages: MessageBus,
    maintenance: DailyMaintenance,
    todayClock: TodayClock,
) : ViewModel() {
    init {
        // Каждый новый день — разбор хвостов: забытое возвращается в списки
        viewModelScope.launch { todayClock.todayFlow.collect { maintenance.run(it) } }
    }

    fun runUndo(undo: suspend () -> Unit) = viewModelScope.launch { undo() }
}

/**
 * Каркас приложения: нижняя панель с пятью вкладками, общий Snackbar и навигация между экранами.
 *
 * - Scaffold — стандартная «рама» экрана Material 3: места для панели навигации и Snackbar,
 *   а в `padding` — отступы, чтобы содержимое не залезало под них.
 * - NavHost — «переключатель экранов»: по адресу (route) показывает нужный экран
 *   и помнит историю для кнопки «Назад».
 * - Нижняя панель видна на главных вкладках (включая экран одного списка);
 *   на экране дня и в настройках скрыта.
 */
@Composable
fun PlannerApp(viewModel: AppViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(Unit) {
        viewModel.messages.messages.collect { message ->
            // Новое сообщение заменяет старое: «Отменить» относится к последнему действию
            snackbar.currentSnackbarData?.dismiss()
            launch {
                val result = snackbar.showSnackbar(
                    message = message.text.resolve(resources),
                    actionLabel = if (message.undo != null) undoLabel else null,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) message.undo?.let(viewModel::runUndo)
            }
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = Tab.entries.any { tab -> destination?.hierarchy?.any { it.route == tab.route } == true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    Tab.entries.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (selected && tab == Tab.LISTS) {
                                    // Повторное нажатие на открытую вкладку «Списки» — назад к оглавлению
                                    navController.popBackStack(ROUTE_LISTS_OVERVIEW, inclusive = false)
                                } else {
                                    // Переход на вкладку: saveState/restoreState запоминают, где человек
                                    // был на каждой вкладке, и возвращают туда же
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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
        },
    ) { padding ->
        NavHost(navController, startDestination = Tab.TODAY.route, modifier = Modifier.fillMaxSize()) {
            composable(Tab.TODAY.route) {
                TodayScreen(onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }, contentPadding = padding)
            }
            composable(Tab.WEEK.route) {
                WeekScreen(onOpenDay = { navController.navigate("day/${it.toEpochDay()}") }, contentPadding = padding)
            }
            // Вложенный граф: оглавление списков → конкретный список. Нижняя панель видна на обоих
            navigation(route = Tab.LISTS.route, startDestination = ROUTE_LISTS_OVERVIEW) {
                composable(ROUTE_LISTS_OVERVIEW) {
                    ListsOverviewScreen(
                        onOpenList = { id -> navController.navigate("list/$id") },
                        contentPadding = padding,
                    )
                }
                composable(
                    ROUTE_LIST_DETAIL,
                    arguments = listOf(navArgument(ListDetailViewModel.ARG_LIST_ID) { type = NavType.LongType }),
                ) {
                    ListDetailScreen(onBack = navController::popBackStack, contentPadding = padding)
                }
            }
            composable(Tab.GOALS.route) { ComingSoon(R.string.nav_goals, R.string.coming_soon_goals, padding) }
            composable(Tab.DIARY.route) { ComingSoon(R.string.nav_diary, R.string.coming_soon_diary, padding) }
            composable(ROUTE_DAY, arguments = listOf(navArgument("epochDay") { type = NavType.LongType })) { entry ->
                DayScreen(
                    date = LocalDate.ofEpochDay(entry.arguments!!.getLong("epochDay")),
                    onBack = navController::popBackStack,
                    contentPadding = padding,
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(onBack = navController::popBackStack, contentPadding = padding)
            }
        }
    }
}

/** Разделы следующих этапов: цели и дневник. */
@Composable
private fun ComingSoon(title: Int, text: Int, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
        Text(stringResource(text), color = PlannerTheme.paper.inkMuted, modifier = Modifier.padding(top = 12.dp))
    }
}
