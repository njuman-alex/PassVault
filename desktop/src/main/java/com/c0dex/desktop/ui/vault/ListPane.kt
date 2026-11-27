package com.c0dex.desktop.ui.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.c0dex.core.util.TimeFormat
import com.c0dex.desktop.data.DesktopRepository
import com.c0dex.desktop.session.DesktopClipboard
import com.c0dex.desktop.session.DesktopSession
import com.c0dex.desktop.ui.common.HighlightedText
import com.c0dex.desktop.ui.generator.DesktopGenerator

@Composable
fun ListPane(
    session: DesktopSession,
    repo: DesktopRepository,
    clipboard: DesktopClipboard,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit
) {
    var items by remember { mutableStateOf(listOf<DesktopRepository.RecordView>()) }
    var tick by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var desc by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var genTarget by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var ioError by remember { mutableStateOf<String?>(null) }   // пункт 1: фидбек провала сохранения

    LaunchedEffect(tick) { items = repo.list() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PassVault", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onSettings) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Настройки")
                }
            }
            TextButton(onClick = { session.lock() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Лок")
                }
            }
        }
        // Счётчик: поднимаем фильтр сюда, чтобы использовать и в счётчике, и в списке ниже
        val filtered = items.filter { it.description.contains(query, ignoreCase = true) }
        Text(
            if (query.isEmpty()) "Паролей в сейфе: ${items.size}"
            else "Паролей в сейфе: ${items.size}, найдено: ${filtered.size}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = query, onValueChange = { query = it; session.touch() }, label = { Text("Поиск") }, singleLine = true, modifier = Modifier.fillMaxWidth())   // D-031: ввод = активность
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { rec ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onOpen(rec.id)
                        }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            //Text(rec.description, style = MaterialTheme.typography.titleMedium)
                            HighlightedText(rec.description, query, style = MaterialTheme.typography.titleMedium)
                            Text(TimeFormat.human(rec.updatedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        //TextButton(onClick = { onOpen(rec.id) }) { Text("Открыть") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // было: Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("Добавить") }
        Button(onClick = { ioError = null; showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("Добавить") }
    }

    // было: if (showAdd) AlertDialog( ... )   — без фигурных скобок
    // D-031-fix: DisposableEffect — САМОСТОЯТЕЛЬНЫЙ вызов рядом с AlertDialog, не его аргумент
    if (showAdd) {
        DisposableEffect(Unit) { session.beginDialog(); onDispose { session.endDialog() } }   // D-031
        AlertDialog(
            // было: onDismissRequest = { showAdd = false },
            onDismissRequest = { showAdd = false; ioError = null },
            title = { Text("Новый пароль") },
            text = {
                Column {
                    OutlinedTextField(value = desc, onValueChange = { desc = it; session.touch() }, label = { Text("Описание") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    // D-015: опциональный логин между описанием и паролем
                    OutlinedTextField(value = login, onValueChange = { login = it; session.touch() }, label = { Text("Логин (необязательно)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = pass, onValueChange = { pass = it; session.touch() }, label = { Text("Пароль") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { genTarget = { pass = it } }) { Text("🎲 Генератор…") }
                    // пункт 1: показываем причину провала, а не молчим
                    if (ioError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(ioError!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                // было:
                // Button(onClick = {
                //     if (repo.add(desc, pass, login)) { showAdd = false; desc = ""; login = ""; pass = ""; tick++ }
                // }, enabled = desc.isNotEmpty() && pass.isNotEmpty()) { Text("Сохранить") }
                Button(onClick = {
                    if (repo.add(desc, pass, login)) { showAdd = false; desc = ""; login = ""; pass = ""; tick++; ioError = null }
                    else ioError = "Не удалось сохранить — файл изменён снаружи или повреждён"
                }, enabled = desc.isNotEmpty() && pass.isNotEmpty()) { Text("Сохранить") }
            },
            // было: dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Отмена") } }
            dismissButton = { TextButton(onClick = { showAdd = false; ioError = null }) { Text("Отмена") } }
        )
    }   // <-- закрывающая скобка if, её раньше не было

    genTarget?.let { target ->
        DisposableEffect(Unit) { session.beginDialog(); onDispose { session.endDialog() } }   // D-031: тут место верное (let — inline-лямбда)
        DesktopGenerator(
            onDismiss = { genTarget = null },
            onUse = { target(it); genTarget = null },
            onCopy = { clipboard.copy(it) }
        )
    }
}