package com.c0dex.desktop.ui.vault

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
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Поиск") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items.filter { it.description.contains(query, ignoreCase = true) }, key = { it.id }) { rec ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            //Text(rec.description, style = MaterialTheme.typography.titleMedium)
                            HighlightedText(rec.description, query, style = MaterialTheme.typography.titleMedium)
                            Text(TimeFormat.human(rec.updatedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onOpen(rec.id) }) { Text("Открыть") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("Добавить") }
    }

    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false },
        title = { Text("Новый пароль") },
        text = {
            Column {
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Описание") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                // D-015: опциональный логин между описанием и паролем
                OutlinedTextField(value = login, onValueChange = { login = it }, label = { Text("Логин (необязательно)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Пароль") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { genTarget = { pass = it } }) { Text("🎲 Генератор…") }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (repo.add(desc, pass, login)) { showAdd = false; desc = ""; login = ""; pass = ""; tick++ }
            }, enabled = desc.isNotEmpty() && pass.isNotEmpty()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Отмена") } }
    )

    genTarget?.let { target ->
        DesktopGenerator(
            onDismiss = { genTarget = null },
            onUse = { target(it); genTarget = null },
            onCopy = { clipboard.copy(it) }
        )
    }
}