package com.obsidianvoice.notes

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import com.obsidianvoice.notes.ui.RecordScreen
import com.obsidianvoice.notes.ui.RecordViewModel
import com.obsidianvoice.notes.ui.theme.ObsidianVoiceNotesTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ObsidianVoiceNotesTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val viewModel: RecordViewModel = viewModel()
    val vaultUri by viewModel.vaultUri.collectAsState()

    // Runtime permission for the microphone.
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handled implicitly: user retries record if denied */ }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // Persisted folder picker (Storage Access Framework).
    val context = androidx.compose.ui.platform.LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            viewModel.setVaultUri(uri.toString())
        }
    }

    // Ask for permissions and kick off the model download on first composition.
    LaunchedEffect(Unit) {
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.ensureModel()
    }

    val vaultLabel = remember(vaultUri) {
        vaultUri?.let { runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }.getOrNull() }
    }

    RecordScreen(
        viewModel = viewModel,
        onPickFolder = { folderPicker.launch(null) },
        vaultFolderLabel = vaultLabel,
    )
}
