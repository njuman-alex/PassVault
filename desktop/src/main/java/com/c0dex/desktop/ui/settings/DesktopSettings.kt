package com.c0dex.desktop.ui.settings

import com.c0dex.desktop.session.DesktopSession
import com.c0dex.desktop.data.DesktopPrefs
import com.c0dex.desktop.io.VaultFileIO
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.c0dex.core.format.ContainerCodec
import com.c0dex.core.merge.VaultMerger
import java.nio.file.Path
import javax.swing.JFileChooser

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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row {
            TextButton(onClick = onBack) { Text("← Назад") }
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
                FilterChip(selected = idleMin == m, onClick = { idleMin = m; prefs.idleMs = m * 60_000L }, label = { Text(if (m == 0) "∞" else "$m м") })
            }
        }

        Text("Очистка буфера обмена", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CLIP_PRESETS.forEach { s ->
                FilterChip(selected = clipSec == s, onClick = { clipSec = s; prefs.clipboardMs = s * 1_000L }, label = { Text(if (s == 0) "∞" else "$s с") })
            }
        }

        Text("Лок при потере фокуса", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GRACE_PRESETS.forEach { s ->
                FilterChip(selected = graceSec == s, onClick = { graceSec = s; prefs.graceMs = s * 1_000L }, label = { Text(if (s == 0) "сразу" else "$s с") })
            }
        }

        Text("Файл-сейф", style = MaterialTheme.typography.titleMedium)
        Text("Текущий: ${vaultName ?: "не выбран"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { prefs.vaultPath = null; session.lock() }) { Text("Закрыть и выбрать другой файл") }
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
                mergeMsg = when {
                    merged == null -> "Сначала разблокируйте vault"
                    !session.replaceContainer(merged) -> "Не удалось записать результат"
                    else -> "Слито успешно ✓"
                }
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
                OutlinedTextField(value = newM, onValueChange = { newM = it; masterError = null }, label = { Text("Новый мастер-пароль") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = confM, onValueChange = { confM = it; masterError = null }, label = { Text("Повторите") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
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
}