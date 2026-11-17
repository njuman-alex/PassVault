package com.c0dex.desktop.ui.vault

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.c0dex.core.format.ContainerCodec
import com.c0dex.core.format.RecordPayload
import com.c0dex.core.util.TimeFormat
import com.c0dex.desktop.session.DesktopClipboard
import com.c0dex.desktop.session.DesktopSession
import com.c0dex.desktop.ui.generator.DesktopGenerator
import java.util.UUID

@Composable
fun ListPane(session: DesktopSession, clipboard: DesktopClipboard, onSettings: () -> Unit) {
    var items by remember { mutableStateOf(listOf<Triple<String, String, Long>>()) }
    var tick by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var desc by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<String?>(null) }
    var genTarget by remember { mutableStateOf<((String) -> Unit)?>(null) }

    LaunchedEffect(tick) {
        items = session.withDek { dek ->
            session.currentContainer()?.records?.mapNotNull { e ->
                ContainerCodec.openPayload(dek, e)?.takeIf { !it.deleted }?.let { Triple(e.id, it.description, it.updatedAt) }
            }?.sortedBy { it.second.lowercase() } ?: emptyList()
        } ?: emptyList()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PassVault", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            //TextButton(onClick = onSettings) { Text("Настройки") }
            TextButton(onClick = onSettings) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Настройки")
                }
            }
            //TextButton(onClick = { session.lock() }) { Text("Лок") }
            TextButton(onClick = {session.lock()}) {
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
            items(items.filter { it.second.contains(query, ignoreCase = true) }, key = { it.first }) { (id, d, ts) ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d, style = MaterialTheme.typography.titleMedium)
                            Text(TimeFormat.human(ts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { detail = id }) { Text("Открыть") }
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
                OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Пароль") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { genTarget = { pass = it } }) { Text("🎲 Генератор…") }
            }
        },
        confirmButton = {
            Button(onClick = {
                val ok = session.withDek { dek ->
                    val c = session.currentContainer() ?: return@withDek false
                    val now = System.currentTimeMillis()
                    val env = ContainerCodec.sealPayload(dek, UUID.randomUUID().toString(), RecordPayload(desc, pass, now, now))
                    session.replaceContainer(c.copy(records = c.records + env))
                } ?: false
                if (ok) { showAdd = false; desc = ""; pass = ""; tick++ }
            }, enabled = desc.isNotEmpty() && pass.isNotEmpty()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Отмена") } }
    )

    detail?.let { id ->
        val rec = session.withDek { dek ->
            session.currentContainer()?.records?.firstOrNull { it.id == id }?.let { e -> ContainerCodec.openPayload(dek, e)?.let { e to it } }
        }
        if (rec != null) {
            var d by remember(rec) { mutableStateOf(rec.second.description) }
            var p by remember(rec) { mutableStateOf(rec.second.password) }
            var revealed by remember(rec) { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { detail = null },
                title = { Text(d) },
                text = {
                    Column {
                        OutlinedTextField(value = d, onValueChange = { d = it }, label = { Text("Описание") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = p, onValueChange = { p = it }, label = { Text("Пароль") }, singleLine = true,
                            visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { revealed = !revealed }) { Text(if (revealed) "🙈 Скрыть" else "👁 Показать") }
                            TextButton(onClick = { clipboard.copy(p) }) { Text("📋 Копировать") }
                            TextButton(onClick = { genTarget = { p = it } }) { Text("🎲 Генератор…") }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val ok = session.withDek { dek ->
                            val c = session.currentContainer() ?: return@withDek false
                            val now = System.currentTimeMillis()
                            val env = ContainerCodec.sealPayload(dek, id, rec.second.copy(description = d, password = p, updatedAt = now))
                            session.replaceContainer(c.copy(records = c.records.map { if (it.id == id) env else it }))
                        } ?: false
                        if (ok) { detail = null; tick++ }
                    }) { Text("Сохранить") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            val ok = session.withDek { dek ->
                                val c = session.currentContainer() ?: return@withDek false
                                val now = System.currentTimeMillis()
                                val env = ContainerCodec.sealPayload(dek, id, rec.second.copy(deleted = true, updatedAt = now))
                                session.replaceContainer(c.copy(records = c.records.map { if (it.id == id) env else it }))
                            } ?: false
                            if (ok) { detail = null; tick++ }
                        }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                        TextButton(onClick = { detail = null }) { Text("Закрыть") }
                    }
                }
            )
        }
    }

    genTarget?.let { target ->
        DesktopGenerator(
            onDismiss = { genTarget = null },
            onUse = { target(it); genTarget = null },
            onCopy = { clipboard.copy(it) }
        )
    }
}