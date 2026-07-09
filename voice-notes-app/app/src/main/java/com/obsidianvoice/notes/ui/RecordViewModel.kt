package com.obsidianvoice.notes.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obsidianvoice.notes.data.SettingsStore
import com.obsidianvoice.notes.recording.RecordingBus
import com.obsidianvoice.notes.recording.RecordingService
import com.obsidianvoice.notes.stt.ModelManager
import com.obsidianvoice.notes.storage.VaultWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date

sealed interface ModelState {
    data object Ready : ModelState
    data object NotReady : ModelState
    data class Downloading(val progress: Float?) : ModelState // null = indeterminate (unpacking)
    data class Error(val message: String) : ModelState
}

sealed interface SaveResult {
    data class Success(val fileName: String) : SaveResult
    data class Error(val message: String) : SaveResult
}

class RecordViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)

    val recordingState = RecordingBus.state

    private val _modelState = MutableStateFlow<ModelState>(
        if (ModelManager.isReady(app)) ModelState.Ready else ModelState.NotReady
    )
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()

    val vaultUri: StateFlow<String?> =
        settings.vaultTreeUri.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setVaultUri(uri: String) {
        viewModelScope.launch { settings.setVaultTreeUri(uri) }
    }

    /** Downloads the offline model if needed; updates [modelState] as it goes. */
    fun ensureModel() {
        if (_modelState.value == ModelState.Ready) return
        if (_modelState.value is ModelState.Downloading) return
        _modelState.value = ModelState.Downloading(0f)
        viewModelScope.launch {
            val result = ModelManager.ensureModel(getApplication()) { progress ->
                _modelState.value = ModelState.Downloading(if (progress < 0f) null else progress)
            }
            _modelState.value = result.fold(
                onSuccess = { ModelState.Ready },
                onFailure = { ModelState.Error(it.message ?: "모델 다운로드 실패") },
            )
        }
    }

    fun startRecording() {
        if (_modelState.value != ModelState.Ready) {
            ensureModel()
            return
        }
        RecordingService.start(getApplication())
    }

    fun stopRecording() {
        RecordingService.stop(getApplication())
    }

    fun saveNote(title: String?) {
        val transcript = recordingState.value.fullText
        if (transcript.isBlank()) {
            _saveResult.value = SaveResult.Error("저장할 내용이 없습니다")
            return
        }
        val uriString = vaultUri.value
        if (uriString == null) {
            _saveResult.value = SaveResult.Error("먼저 동기화 폴더를 선택하세요")
            return
        }
        viewModelScope.launch {
            val result = VaultWriter.save(
                context = getApplication(),
                treeUri = Uri.parse(uriString),
                transcript = transcript,
                title = title,
                recordedAt = Date(),
            )
            _saveResult.value = result.fold(
                onSuccess = { SaveResult.Success(it.fileName) },
                onFailure = { SaveResult.Error(it.message ?: "저장 실패") },
            )
        }
    }

    /** Clears the current transcript to start a new note. */
    fun clearTranscript() = RecordingBus.reset()

    fun consumeSaveResult() { _saveResult.value = null }
}
