package app.planner.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.planner.R
import app.planner.common.MessageBus
import app.planner.common.UiMessage
import app.planner.common.UiText
import app.planner.core.data.backup.BackupManager
import app.planner.core.data.backup.BackupSnapshot
import app.planner.core.domain.model.PlannerSettings
import app.planner.core.domain.repository.ISettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel настроек: меняет настройки, делает экспорт в файл и восстановление из файла.
 *
 * Восстановление идёт в два шага, чтобы не заменить данные по ошибке:
 * сначала файл читается и показывается, что в нём ([pendingRestore]),
 * и только после подтверждения данные заменяются.
 *
 * Работа с файлами идёт в `Dispatchers.IO` — отдельных потоках для диска и сети,
 * чтобы интерфейс не подвисал.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: ISettingsRepository,
    private val backup: BackupManager,
    private val messages: MessageBus,
) : ViewModel() {

    val settings: StateFlow<PlannerSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlannerSettings())

    /** Прочитанная копия ждёт подтверждения — сначала показываем, что в ней. */
    private val _pendingRestore = MutableStateFlow<BackupSnapshot?>(null)
    val pendingRestore: StateFlow<BackupSnapshot?> = _pendingRestore.asStateFlow()

    fun update(transform: (PlannerSettings) -> PlannerSettings) = viewModelScope.launch {
        settingsRepository.update(transform)
    }

    fun export(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        val ok = runCatching {
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri)?.use { backup.export(it) } ?: error("No stream")
            }
        }.isSuccess
        messages.post(UiMessage(UiText.Res(if (ok) R.string.export_done else R.string.export_error)))
    }

    fun readForRestore(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        val snapshot = runCatching {
            withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.use { backup.read(it) } ?: error("No stream")
            }
        }.getOrNull()
        if (snapshot == null) {
            messages.post(UiMessage(UiText.Res(R.string.import_error)))
        } else {
            _pendingRestore.value = snapshot
        }
    }

    fun confirmRestore() = viewModelScope.launch {
        val snapshot = _pendingRestore.value ?: return@launch
        _pendingRestore.value = null
        backup.restore(snapshot)
        messages.post(UiMessage(UiText.Res(R.string.import_done)))
    }

    fun cancelRestore() {
        _pendingRestore.value = null
    }
}
