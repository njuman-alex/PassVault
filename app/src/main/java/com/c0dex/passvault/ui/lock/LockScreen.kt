package com.c0dex.passvault.ui.lock

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.c0dex.core.format.VaultFiles
import com.c0dex.passvault.data.SettingsPrefs
import com.c0dex.passvault.data.VaultFileStore
import com.c0dex.passvault.session.SessionManager
import com.c0dex.passvault.ui.theme.PassVaultTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Состояние лок-скрина, поднятое наверх (превью/тестируемость). */
data class LockUiState(
    val creating: Boolean = false,
    val fileName: String? = null,
    val master: String = "",
    val confirm: String = "",
    val error: String? = null,
    val hasVaultFile: Boolean = false,
    // D-014: убрать
    //val treeChosen: Boolean = false,
    val busy: Boolean = false
)

/** Чистый UI лок-скрина: только состояние и колбэки, никаких зависимостей. */
@Composable
fun LockScreenContent(
    state: LockUiState,
    onMasterChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onPickFile: () -> Unit,
    // D-014: убрать
    //onPickTree: () -> Unit,
    onUnlock: () -> Unit,
    onCreate: () -> Unit,
    onToggleMode: () -> Unit
) {
    Scaffold { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("PassVault", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                if (state.creating) "Создание нового сейфа" else "Сейф-файл: ${state.fileName ?: "не выбран"}",
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
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.master.isEmpty()) {
                Text(
                    if (state.creating) "Придумайте мастер-пароль — он не хранится нигде" else "Введите мастер-пароль",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.creating) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.confirm,
                    onValueChange = onConfirmChange,
                    label = { Text("Повторите мастер-пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(16.dp))

            if (state.error != null) {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }

            if (state.creating) {
//                OutlinedButton(
//                    onClick = onPickTree,
//                    enabled = !state.busy,
//                    modifier = Modifier.fillMaxWidth()
//                ) { Text(if (state.treeChosen) "Папка выбрана ✓" else "Выбрать папку для сохранения") }
//                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onCreate,
                    enabled = !state.busy && state.master.isNotEmpty() && state.confirm.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (state.busy) "Создаём сейф…" else "Создать сейф") }
            } else {
                if (!state.hasVaultFile) {
                    OutlinedButton(
                        onClick = onPickFile,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Выбрать сейф-файл") }
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = onUnlock,
                    enabled = !state.busy && state.master.isNotEmpty() && state.hasVaultFile,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (state.busy) "Проверяем пароль…" else "Разблокировать") }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onToggleMode, enabled = !state.busy) {
                Text(if (state.creating) "У меня уже есть сейф" else "Создать новый сейф")
            }
        }
    }
}

/** Обёртка: держит состояние, picker'ы и вызывает сессию. KDF — вне main-потока. */
@Composable
fun LockScreen(session: SessionManager, store: VaultFileStore, prefs: SettingsPrefs) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var master by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(prefs.vaultUri == null) }
    var vaultUri by remember { mutableStateOf(prefs.vaultUri?.let(Uri::parse)) }
    //var pendingTree by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }

    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { persist(context, it); vaultUri = it; prefs.vaultUri = it.toString(); error = null }
    }
//    val openTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
//        tree?.let { persist(context, it); pendingTree = it; error = null }
//    }

    // D-014: системный диалог «папка + имя»; возвращает Uri созданного (пустого) документа
    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) { busy = false; return@rememberLauncherForActivityResult }
        persist(context, uri)
        val m = master.toCharArray()
        scope.launch {
            val ok = withContext(Dispatchers.Default) { session.create(uri, m) }
            busy = false
            master = ""; confirm = ""
            if (ok) {
                vaultUri = uri; error = null
            } else {
                runCatching { DocumentFile.fromSingleUri(context, uri)?.delete() }
                error = "Не удалось записать сейф-файл"
            }
        }
    }

    val state = LockUiState(
        creating = creating,
        fileName = vaultUri?.let { DocumentFile.fromSingleUri(context, it)?.name ?: it.lastPathSegment },
        master = master,
        confirm = confirm,
        error = error,
        hasVaultFile = vaultUri != null,
        //treeChosen = pendingTree != null,
        busy = busy
    )

    LockScreenContent(
        state = state,
        onMasterChange = { master = it; error = null },
        onConfirmChange = { confirm = it; error = null },
        onPickFile = { openDoc.launch(arrayOf("*/*")) },
        //onPickTree = { openTree.launch(null) },
        onUnlock = {
            val uri = vaultUri ?: return@LockScreenContent
            if (busy) return@LockScreenContent
            val m = master.toCharArray()
            scope.launch {
                busy = true
                val result = withContext(Dispatchers.Default) { session.unlock(uri, m) }
                busy = false
                master = ""
                error = when (result) {
                    is SessionManager.UnlockResult.Success -> null
                    is SessionManager.UnlockResult.WrongMaster -> "Неверный мастер-пароль"
                    is SessionManager.UnlockResult.CorruptFile -> "Файл повреждён или не .qdex"
                    is SessionManager.UnlockResult.IoError -> "Не удалось прочитать файл"
                    is SessionManager.UnlockResult.Delayed ->
                        "Слишком много попыток. Подождите ${result.remainingMs / 1000 + 1} с"
                }
            }
        },
//        onCreate = {
//            val tree = pendingTree
//            if (tree == null) { error = "Сначала выберите папку"; return@LockScreenContent }
//            if (master != confirm) { error = "Пароли не совпадают"; return@LockScreenContent }
//            if (busy) return@LockScreenContent
//            val m = master.toCharArray()
//            scope.launch {
//                busy = true
//                val outcome = withContext(Dispatchers.Default) {
//                    val uri = store.createInTree(tree, VaultFiles.DEFAULT_NAME)
//                        ?: return@withContext null to "Не удалось создать файл"
//                    persist(context, uri)
//                    if (session.create(uri, m)) {
//                        uri to null
//                    } else {
//                        // не оставляем пустой огрызок в папке пользователя
//                        runCatching { DocumentFile.fromSingleUri(context, uri)?.delete() }
//                        null to "Не удалось записать vault"
//                    }
//                }
//                busy = false
//                master = ""; confirm = ""
//                outcome.first?.let { vaultUri = it; pendingTree = null }
//                error = outcome.second
//            }
//        },

        onCreate = {
            if (busy) return@LockScreenContent
            if (master != confirm) { error = "Пароли не совпадают"; return@LockScreenContent }
            busy = true
            createDoc.launch(VaultFiles.DEFAULT_NAME)
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
        state = LockUiState(creating = false, fileName = "vault.qdex", hasVaultFile = true),
        onMasterChange = {}, onConfirmChange = {}, onPickFile = {},
        //onPickTree = {},
        onUnlock = {}, onCreate = {}, onToggleMode = {}
    )
}

@Preview(showBackground = true, name = "Create")
@Composable
private fun LockPreviewCreate() = PassVaultTheme {
    LockScreenContent(
        // D-014: убрать treeChosen = true
        state = LockUiState(creating = true /*, treeChosen = true */),
        onMasterChange = {}, onConfirmChange = {}, onPickFile = {},
        //onPickTree = {},
        onUnlock = {}, onCreate = {}, onToggleMode = {}
    )
}

@Preview(showBackground = true, name = "Error")
@Composable
private fun LockPreviewError() = PassVaultTheme {
    LockScreenContent(
        state = LockUiState(creating = false, fileName = "vault.qdex", hasVaultFile = true, error = "Неверный мастер-пароль"),
        onMasterChange = {}, onConfirmChange = {}, onPickFile = {},
        //onPickTree = {},
        onUnlock = {}, onCreate = {}, onToggleMode = {}
    )
}

@Preview(showBackground = true, name = "Busy")
@Composable
private fun LockPreviewBusy() = PassVaultTheme {
    LockScreenContent(
        state = LockUiState(creating = false, fileName = "vault.qdex", hasVaultFile = true, master = "secret", busy = true),
        onMasterChange = {}, onConfirmChange = {}, onPickFile = {},
        //onPickTree = {},
        onUnlock = {}, onCreate = {}, onToggleMode = {}
    )
}