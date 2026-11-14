@file:OptIn(ExperimentalMaterial3Api::class)

package com.c0dex.passvault.ui.settings

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.c0dex.passvault.data.SettingsPrefs
import com.c0dex.passvault.session.SessionManager
import com.c0dex.passvault.ui.theme.PassVaultTheme

data class SettingsUiState(
    val idleMinutes: Int = 5,
    val clipboardSeconds: Int = 30,
    val graceSeconds: Int = 0,
    val vaultName: String? = null
)

private val IDLE_PRESETS = listOf(1, 5, 15, 30, 60, 0)
private val CLIP_PRESETS = listOf(10, 30, 60, 0)
private val GRACE_PRESETS = listOf(0, 30, 60)

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onIdle: (Int) -> Unit,
    onClipboard: (Int) -> Unit,
    onGrace: (Int) -> Unit,
    onChangeMaster: () -> Unit,
    onLock: () -> Unit,
    onChangeVault: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Безопасность", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onChangeMaster) { Text("Сменить мастер-пароль") }
                OutlinedButton(onClick = onLock) { Text("Заблокировать") }
            }

            Text("Автолок при бездействии", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IDLE_PRESETS.forEach { m ->
                    FilterChip(selected = state.idleMinutes == m, onClick = { onIdle(m) }, label = { Text(if (m == 0) "∞" else "$m м") })
                }
            }

            Text("Очистка буфера обмена", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CLIP_PRESETS.forEach { s ->
                    FilterChip(selected = state.clipboardSeconds == s, onClick = { onClipboard(s) }, label = { Text(if (s == 0) "∞" else "$s с") })
                }
            }

            Text("Лок при уходе в фон", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GRACE_PRESETS.forEach { s ->
                    FilterChip(selected = state.graceSeconds == s, onClick = { onGrace(s) }, label = { Text(if (s == 0) "сразу" else "$s с") })
                }
            }

            Text("Vault", style = MaterialTheme.typography.titleMedium)
            Text("Файл: ${state.vaultName ?: "не выбран"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onChangeVault) { Text("Закрыть и выбрать другой vault") }
        }
    }
}

@Composable
fun SettingsScreen(session: SessionManager, prefs: SettingsPrefs, onBack: () -> Unit) {
    // локальное зеркало prefs: prefs не observable, UI должен реагировать сам
    var idleMin by remember { mutableStateOf((prefs.idleTimeoutMs / 60_000).toInt()) }
    var clipSec by remember { mutableStateOf((prefs.clipboardTimeoutMs / 1_000).toInt()) }
    var graceSec by remember { mutableStateOf((prefs.backgroundGraceMs / 1_000).toInt()) }

    var showMaster by remember { mutableStateOf(false) }
    var newM by remember { mutableStateOf("") }
    var confM by remember { mutableStateOf("") }
    var masterError by remember { mutableStateOf<String?>(null) }

    SettingsContent(
        state = SettingsUiState(
            idleMinutes = idleMin,
            clipboardSeconds = clipSec,
            graceSeconds = graceSec,
            vaultName = prefs.vaultUri?.let { Uri.parse(it).lastPathSegment }
        ),
        onIdle = { idleMin = it; prefs.idleTimeoutMs = it * 60_000L },
        onClipboard = { clipSec = it; prefs.clipboardTimeoutMs = it * 1_000L },
        onGrace = { graceSec = it; prefs.backgroundGraceMs = it * 1_000L },
        onChangeMaster = { showMaster = true },
        onLock = { session.lock() },
        onChangeVault = { prefs.vaultUri = null; session.lock() },
        onBack = onBack
    )

    if (showMaster) {
        AlertDialog(
            onDismissRequest = { showMaster = false },
            title = { Text("Смена мастер-пароля") },
            text = {
                Column {
                    Text("Записи не перешифровываются — меняется только обёртка ключа.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = newM, onValueChange = { newM = it; masterError = null }, label = { Text("Новый мастер-пароль") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = confM, onValueChange = { confM = it; masterError = null }, label = { Text("Повторите") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (masterError != null) Text(masterError!!, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newM != confM) { masterError = "Пароли не совпадают"; return@Button }
                    val ok = session.rotateMaster(newM.toCharArray())
                    newM = ""; confM = ""
                    if (ok) showMaster = false else masterError = "Не удалось сохранить"
                }, enabled = newM.isNotEmpty() && confM.isNotEmpty()) { Text("Сменить") }
            },
            dismissButton = { TextButton(onClick = { showMaster = false }) { Text("Отмена") } }
        )
    }
}

@Preview(showBackground = true, name = "Settings")
@Composable
private fun SettingsPreview() = PassVaultTheme {
    SettingsContent(SettingsUiState(vaultName = "vault.pmv3"), {}, {}, {}, {}, {}, {}, {})
}