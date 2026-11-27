package com.c0dex.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect   // D-031
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.c0dex.desktop.data.DesktopPrefs
import com.c0dex.desktop.data.DesktopSync
import com.c0dex.desktop.session.DesktopSession
import com.c0dex.desktop.ui.chooseFile

private val IDLE_PRESETS = listOf(1, 5, 15, 30, 60, 0)
private val CLIP_PRESETS = listOf(10, 30, 60, 0)
private val GRACE_PRESETS = listOf(0, 30, 60)

@Composable
fun SettingsPane(session: DesktopSession, prefs: DesktopPrefs, onBack: () -> Unit) {
    var idleMin by remember { mutableStateOf((prefs.idleMs / 60_000).toInt()) }
    var clipSec by remember { mutableStateOf((prefs.clipboardMs / 1_000).toInt()) }
    var graceSec by remember { mutableStateOf((prefs.graceMs / 1_000).toInt()) }

    var showMaster by remember { mutableStateOf(false) }
    var newM by remember { mutableStateOf("") }
    var confM by remember { mutableStateOf("") }
    var masterError by remember { mutableStateOf<String?>(null) }

    var mergeMsg by remember { mutableStateOf<String?>(null) }

    //val vaultName = prefs.vaultPath?.let { Path.of(it).fileName.toString() }
    val vaultName = prefs.vaultPath
    val sync = remember { DesktopSync(session) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row {
            //TextButton(onClick = onBack) { Text("← Назад") }
            Text("Настройки", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }

        Text("Безопасность", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showMaster = true }) { Text("Сменить мастер-пароль") }
            OutlinedButton(onClick = { session.lock() }) { Text("Заблокировать") }
        }

        Text("Автолок при бездействии", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IDLE_PRESETS.forEach { m ->
                // было: onClick = { idleMin = m; prefs.idleMs = m * 60_000L }
                FilterChip(selected = idleMin == m, onClick = { idleMin = m; prefs.idleMs = m * 60_000L; session.touch() }, label = { Text(if (m == 0) "∞" else "$m м") })   // D-031: клик = активность
            }
        }

        Text("Очистка буфера обмена", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CLIP_PRESETS.forEach { s ->
                // было: onClick = { clipSec = s; prefs.clipboardMs = s * 1_000L }
                FilterChip(selected = clipSec == s, onClick = { clipSec = s; prefs.clipboardMs = s * 1_000L; session.touch() }, label = { Text(if (s == 0) "∞" else "$s с") })   // D-031
            }
        }

        Text("Лок при потере фокуса", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GRACE_PRESETS.forEach { s ->
                // было: onClick = { graceSec = s; prefs.graceMs = s * 1_000L }
                FilterChip(selected = graceSec == s, onClick = { graceSec = s; prefs.graceMs = s * 1_000L; session.touch() }, label = { Text(if (s == 0) "сразу" else "$s с") })   // D-031
            }
        }

        Text("Файл-сейф", style = MaterialTheme.typography.titleMedium)
        Text("Текущий: ${vaultName ?: "не выбран"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { prefs.vaultPath = null; session.lock() }) { Text("Закрыть и выбрать другой файл") }
        OutlinedButton(onClick = {
            // было: val p = chooseFile(save = false) ?: return@OutlinedButton
            val p = session.withFileDialog { chooseFile(save = false) } ?: return@OutlinedButton
            mergeMsg = when (sync.mergeConflict(p)) {
                DesktopSync.Result.Ok -> "Слито успешно ✓"
                DesktopSync.Result.Locked -> "Сначала разблокируйте vault"
                DesktopSync.Result.ReadFail -> "Не удалось прочитать копию"
                DesktopSync.Result.ParseFail -> "Копия повреждена или не .qdex"
                DesktopSync.Result.ForeignFile -> "Копия из другого сейфа или все записи битые"
                DesktopSync.Result.WriteFail -> "Не удалось записать результат"
            }
        }) { Text("Слить конфликт-копию…") }
        if (mergeMsg != null) Text(mergeMsg!!, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(8.dp))

        //Button(onClick = onBack) { Text("← Назад") }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Назад") }
    }

    // было: if (showMaster) AlertDialog( ... )   — без фигурных скобок
    // D-031: DisposableEffect — САМОСТОЯТЕЛЬНЫЙ вызов рядом с AlertDialog, не его аргумент
    if (showMaster) {
        DisposableEffect(Unit) { session.beginDialog(); onDispose { session.endDialog() } }   // D-031
        AlertDialog(
            onDismissRequest = { showMaster = false },
            title = { Text("Смена мастер-пароля") },
            text = {
                Column {
                    Text("Записи не перешифровываются — меняется только обёртка ключа.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    // было: onValueChange = { newM = it; masterError = null }
                    OutlinedTextField(value = newM, onValueChange = { newM = it; masterError = null; session.touch() }, label = { Text("Новый мастер-пароль") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())   // D-031: ввод = активность
                    Spacer(Modifier.height(8.dp))
                    // было: onValueChange = { confM = it; masterError = null }
                    OutlinedTextField(value = confM, onValueChange = { confM = it; masterError = null; session.touch() }, label = { Text("Повторите") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())   // D-031
                    if (masterError != null) Text(masterError!!, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newM != confM) { masterError = "Пароли не совпадают"; return@Button }
                    if (session.rotateMaster(newM.toCharArray())) { showMaster = false; newM = ""; confM = "" } else masterError = "Не удалось сохранить"
                }, enabled = newM.isNotEmpty() && confM.isNotEmpty()) { Text("Сменить") }
            },
            dismissButton = { TextButton(onClick = { showMaster = false }) { Text("Отмена") } }
        )
    }   // <-- закрывающая скобка if, её раньше не было
}