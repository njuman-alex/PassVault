package com.c0dex.desktop.ui.settings

import com.c0dex.desktop.session.DesktopSession
import com.c0dex.desktop.data.DesktopPrefs
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.c0dex.core.format.ContainerCodec
import com.c0dex.desktop.io.VaultFileIO
import com.c0dex.core.merge.VaultMerger
import javax.swing.JFileChooser

@Composable
fun SettingsPane(session: DesktopSession, prefs: DesktopPrefs, onBack: () -> Unit) {
    var idleMin by remember { mutableStateOf((prefs.idleMs / 60000).toInt()) }
    var graceSec by remember { mutableStateOf((prefs.graceMs / 1000).toInt()) }
    var clipSec by remember { mutableStateOf((prefs.clipboardMs / 1000).toInt()) }
    var mergeMsg by remember { mutableStateOf<String?>(null) }
    var showMaster by remember { mutableStateOf(false) }
    var newM by remember { mutableStateOf("") }
    var confM by remember { mutableStateOf("") }
    var mErr by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row { Text("Настройки", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); TextButton(onClick = onBack) { Text("Назад") } }

        Text("Автолок при бездействии", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 5, 15, 30, 60, 0).forEach { m ->
                FilterChip(selected = idleMin == m, onClick = { idleMin = m; prefs.idleMs = m * 60000L }, label = { Text(if (m == 0) "∞" else "$m м") })
            }
        }
        Text("Лок при потере фокуса", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 30, 60).forEach { g ->
                FilterChip(selected = graceSec == g, onClick = { graceSec = g; prefs.graceMs = g * 1000L }, label = { Text(if (g == 0) "сразу" else "$g с") })
            }
        }
        Text("Очистка буфера обмена", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 30, 60, 0).forEach { c ->
                FilterChip(selected = clipSec == c, onClick = { clipSec = c; prefs.clipboardMs = c * 1000L }, label = { Text(if (c == 0) "∞" else "$c с") })
            }
        }

        Text("Безопасность", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showMaster = true }) { Text("Сменить мастер") }
            OutlinedButton(onClick = { session.lock() }) { Text("Заблокировать") }
        }
        OutlinedButton(onClick = { prefs.vaultPath = null; session.lock() }) { Text("Закрыть и выбрать другой vault") }

        Text("Синк", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = {
            val fc = JFileChooser()
            if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                val other = try {
                    ContainerCodec.parse(VaultFileIO.read(fc.selectedFile.toPath()) ?: error("read"))
                } catch (e: Exception) {
                    mergeMsg = "Копия повреждена или не PMV3"; return@OutlinedButton
                }
                val merged = session.withDek { dek ->
                    val base = session.currentContainer() ?: return@withDek null
                    VaultMerger.merge(dek, base, other)
                }
                mergeMsg = if (merged != null && session.replaceContainer(merged)) "Слито успешно ✓" else "Сначала разблокируйте vault"
            }
        }) { Text("Слить конфликт-копию…") }
        if (mergeMsg != null) Text(mergeMsg!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (showMaster) AlertDialog(
        onDismissRequest = { showMaster = false },
        title = { Text("Смена мастер-пароля") },
        text = {
            Column {
                Text("Записи не перешифровываются — меняется только обёртка ключа.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = newM, onValueChange = { newM = it; mErr = null }, label = { Text("Новый мастер") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = confM, onValueChange = { confM = it; mErr = null }, label = { Text("Повторите") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (mErr != null) Text(mErr!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (newM != confM) { mErr = "Пароли не совпадают"; return@Button }
                if (session.rotateMaster(newM.toCharArray())) { showMaster = false; newM = ""; confM = "" } else mErr = "Не удалось сохранить"
            }, enabled = newM.isNotEmpty() && confM.isNotEmpty()) { Text("Сменить") }
        },
        dismissButton = { TextButton(onClick = { showMaster = false }) { Text("Отмена") } }
    )
}