package app.planner.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.BuildConfig
import app.planner.R
import app.planner.core.designsystem.component.DashedRule
import app.planner.core.designsystem.component.PaperChip
import app.planner.core.designsystem.component.SectionLabel
import app.planner.core.designsystem.theme.PlannerTheme
import app.planner.core.domain.model.DarkVariant
import app.planner.core.domain.model.OverdueMode
import app.planner.core.domain.model.PlannerSettings
import app.planner.core.domain.model.ThemeMode
import java.time.LocalDate

/** Страница автора на Boosty — та же, что в RadioJourney. */
private const val DONATE_URL = "https://boosty.to/foxynest/donate"

/**
 * Экран настроек: планирование (что делать с несделанным), внешний вид (тема),
 * данные (экспорт и восстановление из файла) и — в сборке для RuStore — «Поддержать проект».
 *
 * Сами настройки хранятся в DataStore (см. `DataStoreSettingsRepository`), экран лишь
 * показывает текущие значения и отправляет изменения в [SettingsViewModel].
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()
    // ContentResolver — системный «посредник», через который читаются и пишутся файлы,
    // выбранные пользователем (в том числе на Google Диске или флешке)
    val resolver = LocalContext.current.contentResolver
    // Открывает ссылку в браузере (для кнопки «Поддержать»)
    val uriHandler = LocalUriHandler.current

    // Системные окна выбора файла (Storage Access Framework). Приложению не нужно разрешение
    // на доступ ко всей памяти: пользователь сам выбирает, куда сохранить и что открыть.
    // Результат (адрес файла — Uri) приходит в лямбду; null — если окно закрыли без выбора.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(resolver, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readForRestore(resolver, uri)
    }

    SettingsContent(
        settings = settings,
        showDonate = BuildConfig.SHOW_DONATE,
        contentPadding = contentPadding,
        onBack = onBack,
        onUpdate = viewModel::update,
        onExport = { exportLauncher.launch("planner-${LocalDate.now()}.json") },
        onImport = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
        onDonate = { uriHandler.openUri(DONATE_URL) },
    )

    pendingRestore?.let { snapshot ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(stringResource(R.string.import_confirm_text, snapshot.tasks.size, snapshot.lists.count { !it.isInbox })) },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text(stringResource(R.string.import_confirm_action)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/**
 * Рисующая часть настроек: только разметка. Файлы, ссылки и ViewModel остаются в [SettingsScreen],
 * поэтому экран можно показать в превью (см. `SettingsPreviews.kt`).
 *
 * @param onUpdate изменить настройки: получает текущие, возвращает новые.
 * @param showDonate показывать ли «Поддержать проект». В приложении это BuildConfig.SHOW_DONATE:
 *   true в сборке для RuStore, false для Google Play — правила Play запрещают вести
 *   пользователя на сторонние способы оплаты, кроме пожертвований на благотворительность.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(
    settings: PlannerSettings,
    showDonate: Boolean,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onUpdate: ((PlannerSettings) -> PlannerSettings) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDonate: () -> Unit,
) {
    val paper = PlannerTheme.paper
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.cd_back)) }
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            SectionLabel(stringResource(R.string.settings_planning))
            SettingTitle(stringResource(R.string.settings_overdue))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperChip(
                    stringResource(R.string.overdue_mode_banner),
                    settings.overdueMode == OverdueMode.BANNER,
                    { onUpdate { it.copy(overdueMode = OverdueMode.BANNER) } },
                )
                PaperChip(
                    stringResource(R.string.overdue_mode_auto),
                    settings.overdueMode == OverdueMode.AUTO_MOVE_TO_TODAY,
                    { onUpdate { it.copy(overdueMode = OverdueMode.AUTO_MOVE_TO_TODAY) } },
                )
            }
            SettingTitle(stringResource(R.string.settings_return_after))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 7, 14, 30).forEach { days ->
                    PaperChip(
                        stringResource(R.string.days_short, days),
                        settings.returnAfterDays == days,
                        { onUpdate { it.copy(returnAfterDays = days) } },
                    )
                }
            }

            SectionLabel(stringResource(R.string.settings_appearance), modifier = Modifier.padding(top = 16.dp))
            SettingTitle(stringResource(R.string.settings_theme))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.SYSTEM to R.string.theme_system,
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark,
                ).forEach { (mode, label) ->
                    PaperChip(stringResource(label), settings.themeMode == mode, { onUpdate { it.copy(themeMode = mode) } })
                }
            }
            SettingTitle(stringResource(R.string.settings_dark_variant))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    DarkVariant.GRAPHITE to R.string.dark_graphite,
                    DarkVariant.INK to R.string.dark_ink,
                    DarkVariant.BLACK to R.string.dark_black,
                ).forEach { (variant, label) ->
                    PaperChip(stringResource(label), settings.darkVariant == variant, { onUpdate { it.copy(darkVariant = variant) } })
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUpdate { it.copy(systemColors = !it.systemColors) } }
                    .padding(vertical = 12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_system_colors))
                    Text(stringResource(R.string.settings_system_colors_hint), color = paper.inkMuted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = settings.systemColors, onCheckedChange = { checked -> onUpdate { it.copy(systemColors = checked) } })
            }

            SectionLabel(stringResource(R.string.settings_data), modifier = Modifier.padding(top = 8.dp))
            ActionRow(Icons.Outlined.FileUpload, stringResource(R.string.export_file), onExport)
            DashedRule()
            ActionRow(Icons.Outlined.FileDownload, stringResource(R.string.import_file), onImport)
            Text(
                stringResource(R.string.backup_drive_soon),
                color = paper.inkMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (showDonate) {
                Text(
                    stringResource(R.string.support_text),
                    color = paper.inkMuted,
                    modifier = Modifier.padding(top = 32.dp),
                )
                ActionRow(Icons.Outlined.LocalCafe, stringResource(R.string.support_link), onDonate)
            }
        }
    }
}

/** Заголовок одной настройки над её вариантами. */
@Composable
private fun SettingTitle(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
}

/** Строка-кнопка с иконкой: «Экспорт в файл», «Поддержать проект». */
@Composable
private fun ActionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Icon(icon, null, tint = PlannerTheme.paper.inkMuted)
        Text(text, modifier = Modifier.padding(start = 16.dp))
    }
}
