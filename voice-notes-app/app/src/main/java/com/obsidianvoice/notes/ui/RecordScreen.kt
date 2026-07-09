package com.obsidianvoice.notes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    viewModel: RecordViewModel,
    onPickFolder: () -> Unit,
    vaultFolderLabel: String?,
) {
    val recording by viewModel.recordingState.collectAsState()
    val model by viewModel.modelState.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }

    LaunchedEffect(saveResult) {
        when (val r = saveResult) {
            is SaveResult.Success -> {
                snackbar.showSnackbar("저장됨: ${r.fileName}")
                title = ""
                viewModel.clearTranscript()
                viewModel.consumeSaveResult()
            }
            is SaveResult.Error -> {
                snackbar.showSnackbar(r.message)
                viewModel.consumeSaveResult()
            }
            null -> Unit
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Obsidian Voice Notes") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            ModelBanner(model, onRetry = viewModel::ensureModel)

            VaultRow(vaultFolderLabel, onPickFolder)

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("제목 (선택)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            TranscriptCard(
                text = recording.fullText,
                isRecording = recording.isRecording,
                modifier = Modifier.weight(1f),
            )

            recording.error?.let {
                Spacer(Modifier.height(8.dp))
                Text("오류: $it", color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (recording.isRecording) {
                    Button(
                        onClick = viewModel::stopRecording,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Text("  정지")
                    }
                } else {
                    Button(
                        onClick = viewModel::startRecording,
                        enabled = model !is ModelState.Downloading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null)
                        Text("  녹음")
                    }
                }

                OutlinedButton(
                    onClick = { scope.launch { viewModel.saveNote(title.ifBlank { null }) } },
                    enabled = recording.hasContent && !recording.isRecording,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Text("  저장")
                }
            }
        }
    }
}

@Composable
private fun ModelBanner(model: ModelState, onRetry: () -> Unit) {
    when (model) {
        is ModelState.Downloading -> {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("한국어 음성 모델 다운로드 중…", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    val p = model.progress
                    if (p == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = p,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        is ModelState.Error -> {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("모델 준비 실패: ${model.message}", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRetry) { Text("다시 시도") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        ModelState.NotReady -> {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("처음 사용 시 오프라인 음성 모델을 내려받아야 합니다 (약 80MB).")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRetry) { Text("모델 다운로드") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        ModelState.Ready -> Unit
    }
}

@Composable
private fun VaultRow(label: String?, onPickFolder: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "저장 폴더: ${label ?: "미선택"}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onPickFolder) { Text("폴더 선택") }
    }
}

@Composable
private fun TranscriptCard(text: String, isRecording: Boolean, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        val scroll = rememberScrollState()
        LaunchedEffect(text) { scroll.scrollTo(scroll.maxValue) }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(16.dp),
        ) {
            if (text.isBlank()) {
                Text(
                    if (isRecording) "듣고 있어요… 말씀하세요." else "녹음 버튼을 눌러 시작하세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
