package com.c0dex.passvault.ui.lock

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.c0dex.passvault.data.SettingsPrefs
import com.c0dex.passvault.data.VaultFileStore
import com.c0dex.passvault.session.SessionManager
import com.c0dex.passvault.ui.theme.PassVaultTheme

/** Состояние лок-скрина, поднятое наверх (превью/тестируемость). */
data class LockUiState(
    val creating: Boolean = false,
    val fileName: String? = null,
    val master: String = "",
    val confirm: String = "",
    val error: String? = null,
    val hasVaultFile: Boolean = false,
    val treeChosen: Boolean = false
)

/** Чистый UI лок-скрина: только состояние и колбэки, никаких зависимостей. */
@Composable
fun LockScreenContent(
    state: LockUiState,
    onMasterChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onPickFile: () -> Unit,
    onPickTree: () -> Unit,
    onUnlock: () -> Unit,
    onCreate: () -> Unit,
    onToggleMode: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("PassVault", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.creating) "Создание нового vault" else "Файл: ${state.fileName ?: "не выбран"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = state.master,
            onValueChange = onMasterChange,
            label = { Text("Мастер-пароль") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (state.creating) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.confirm,
                onValueChange = onConfirmChange,
                label = { Text("Повторите мастер-пароль") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))

        if (state.error != null) {
            Text(state.error, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        if (state.creating) {
            OutlinedButton(onClick = onPickTree, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.treeChosen) "Папка выбрана ✓" else "Выбрать папку для vault")
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onCreate,
                enabled = state.master.isNotEmpty() && state.confirm.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Создать vault") }
        } else {
            if (!state.hasVaultFile) {
                OutlinedButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
                    Text("Выбрать файл vault")
                }
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = onUnlock,
                enabled = state.master.isNotEmpty() && state.hasVaultFile,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Разблокировать") }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onToggleMode) {
            Text(if (state.creating) "У меня уже есть vault" else "Создать новый vault")
        }
        TextButton(onClick = {}, enabled = false) { Text("Recovery (скоро)") }
    }
}

/** Обёртка: держит состояние, picker'ы и вызывает сессию. */
@Composable
fun LockScreen(session: SessionManager, store: VaultFileStore, prefs: SettingsPrefs) {
    val context = LocalContext.current
    var master by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(prefs.vaultUri == null) }
    var vaultUri by remember { mutableStateOf(prefs.vaultUri?.let(Uri::parse)) }
    var pendingTree by remember { mutableStateOf<Uri?>(null) }

    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { persist(context, it); vaultUri = it; prefs.vaultUri = it.toString(); error = null }
    }
    val openTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree?.let { persist(context, it); pendingTree = it; error = null }
    }

    val state = LockUiState(
        creating = creating,
        fileName = vaultUri?.let { DocumentFile.fromSingleUri(context, it)?.name ?: it.lastPathSegment },
        master = master,
        confirm = confirm,
        error = error,
        hasVaultFile = vaultUri != null,
        treeChosen = pendingTree != null
    )

    LockScreenContent(
        state = state,
        onMasterChange = { master = it; error = null },
        onConfirmChange = { confirm = it; error = null },
        onPickFile = { openDoc.launch(arrayOf("*/*")) },
        onPickTree = { openTree.launch(null) },
        onUnlock = {
            val uri = vaultUri ?: return@LockScreenContent
            val result = session.unlock(uri, master.toCharArray())
            master = ""
            error = when (result) {
                is SessionManager.UnlockResult.Success -> null
                is SessionManager.UnlockResult.WrongMaster -> "Неверный мастер-пароль"
                is SessionManager.UnlockResult.CorruptFile -> "Файл повреждён или не PMV3"
                is SessionManager.UnlockResult.IoError -> "Не удалось прочитать файл"
                is SessionManager.UnlockResult.Delayed ->
                    "Слишком много попыток. Подождите ${result.remainingMs / 1000 + 1} с"
            }
        },
        onCreate = {
            val tree = pendingTree
            if (tree == null) { error = "Сначала выберите папку"; return@LockScreenContent }
            if (master != confirm) { error = "Пароли не совпадают"; return@LockScreenContent }
            val uri = store.createInTree(tree, "vault.pmv3")
            if (uri == null) { error = "Не удалось создать файл"; return@LockScreenContent }
            persist(context, uri)
            val ok = session.create(uri, master.toCharArray())
            master = ""; confirm = ""
            if (!ok) error = "Не удалось записать vault"
        },
        onToggleMode = { creating = !creating; error = null; master = ""; confirm = "" }
    )
}

private fun persist(context: android.content.Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    } catch (_: Exception) {
    }
}

// ===== ПРЕВЬЮ =====

@Preview(showBackground = true, name = "Unlock")
@Composable
private fun LockPreviewUnlock() = PassVaultTheme {
    LockScreenContent(
        state = LockUiState(creating = false, fileName = "vault.pmv3", hasVaultFile = true),
        onMasterChange = {}, onConfirmChange = {}, onPickFile = {}, onPickTree = {},
        onUnlock = {}, onCreate = {}, onToggleMode = {}
    )
}

@Preview(showBackground = true, name = "Create")
@Composable
private fun LockPreviewCreate() = PassVaultTheme {
    LockScreenContent(
        state = LockUiState(creating = true, treeChosen = true),
        onMasterChange = {}, onConfirmChange = {}, onPickFile = {}, onPickTree = {},
        onUnlock = {}, onCreate = {}, onToggleMode = {}
    )
}

@Preview(showBackground = true, name = "Error")
@Composable
private fun LockPreviewError() = PassVaultTheme {
    LockScreenContent(
        state = LockUiState(creating = false, fileName = "vault.pmv3", hasVaultFile = true, error = "Неверный мастер-пароль"),
        onMasterChange = {}, onConfirmChange = {}, onPickFile = {}, onPickTree = {},
        onUnlock = {}, onCreate = {}, onToggleMode = {}
    )
}