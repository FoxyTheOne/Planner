package app.planner.feature.lists

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import app.planner.core.designsystem.preview.PlannerScreenPreviews
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.preview.SampleData

/*
 * Превью вкладки «Списки»: оглавление, поиск и экран одного списка — на выдуманных данных.
 * Откройте файл в Android Studio и включите вид «Split».
 */

/** Оглавление: списки со счётчиками, «Без списка» внизу. */
@PlannerScreenPreviews
@Composable
private fun ListsOverviewPreview() {
    val state = with(SampleData) {
        ListsOverviewUiState(
            today = today,
            rows = listOf(
                ListRow(home, 7),
                ListRow(english, 3),
                ListRow(flowers, 2),
                ListRow(books, 12),
                ListRow(noList, 1),
            ),
            allLists = lists,
        )
    }
    PlannerTheme { OverviewForPreview(state) }
}

/** Поиск «кни»: нашёлся список «Книги» и задачи с этим словом. */
@PlannerScreenPreviews
@Composable
private fun ListsSearchPreview() {
    val state = with(SampleData) {
        ListsOverviewUiState(
            today = today,
            allLists = lists,
            query = "кни",
            foundLists = listOf(ListRow(books, 12)),
            foundTasks = listOf(returnBook),
        )
    }
    PlannerTheme { OverviewForPreview(state) }
}

/** Внутри списка «Книги»: обычные задачи и запланированная — серая, с датой. */
@PlannerScreenPreviews
@Composable
private fun ListDetailPreview() {
    // Подставляем данные из SampleData, чтобы посмотреть, как будет выглядеть
    val state = with(SampleData) {
        // ListDetailUiState - data class, который собирает ListDetailViewModel, чтобы передать через val state (Flow)
        ListDetailUiState(
            today = today,
            list = books,
            tasks = listOf(readDune, buyBook, returnBook),
            allLists = lists,
            loaded = true,
        )
    }
    PlannerTheme {
        // ListDetailContent - разметка из файла ListDetailScreen.kt
        ListDetailContent(
            state = state,
            contentPadding = PaddingValues(),
            onBack = {},
            onEditList = {},
            onToggle = {},
            onEdit = {},
            onEditRule = {},
            onSchedule = {},
            onAddTask = {},
        )
    }
}

@Composable
private fun OverviewForPreview(state: ListsOverviewUiState) = ListsOverviewContent(
    state = state,
    contentPadding = PaddingValues(),
    onQueryChange = {},
    onOpenList = {},
    onEditList = {},
    onNewList = {},
    onAddTask = {},
)
