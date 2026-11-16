package com.c0dex.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.c0dex.core.format.ContainerCodec
import com.c0dex.core.format.RecordPayload
import com.c0dex.core.util.TimeFormat
import java.nio.file.Path
import java.util.UUID
import javax.swing.JFileChooser
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.beans.PropertyChangeListener

fun main() = application {
    val scope = rememberCoroutineScope()
    val prefs = remember { DesktopPrefs() }
    val session = remember { DesktopSession(scope, prefs) }
    val clipboard = remember { DesktopClipboard(scope, prefs) }
    var showSettings by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "PassVault",
        state = rememberWindowState(size = DpSize(640.dp, 780.dp))
    ) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            val st by session.state.collectAsState()
            DisposableEffect(Unit) {
                val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                var wasOurs = true
                val listener = PropertyChangeListener { evt ->
                    if (evt.propertyName == "focusedWindow") {
                        val w = evt.newValue as? Window
                        val nowOurs = w != null && Window.getWindows().contains(w)
                        if (nowOurs && !wasOurs) session.onFocusGained()
                        if (!nowOurs && wasOurs) session.onFocusLost()
                        wasOurs = nowOurs
                    }
                }
                kfm.addPropertyChangeListener(listener)
                onDispose { kfm.removePropertyChangeListener(listener) }
            }
            Surface(Modifier.fillMaxSize()) {
                if (st is DesktopSession.State.Locked) LockPane(session, prefs)
                else if (showSettings) SettingsPane(session, prefs, onBack = { showSettings = false })
                else ListPane(session, clipboard, onSettings = { showSettings = true })
            }
        }
    }
}

private fun chooseFile(save: Boolean): Path? {
    val fc = JFileChooser()
    val ok = if (save) fc.showSaveDialog(null) else fc.showOpenDialog(null)
    return if (ok == JFileChooser.APPROVE_OPTION) fc.selectedFile.toPath() else null
}

@Composable
private fun LockPane(session: DesktopSession, prefs: DesktopPrefs) {
    var master by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(prefs.vaultPath == null) }
    var path by remember { mutableStateOf(prefs.vaultPath ?: "") }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("PassVault", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(if (creating) "Создание нового vault" else "Файл: ${path.ifEmpty { "не выбран" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("Путь к vault") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { chooseFile(save = creating)?.let { path = it.toString() } }, modifier = Modifier.fillMaxWidth()) {
            Text(if (creating) "Выбрать папку и имя…" else "Выбрать файл…")
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = master, onValueChange = { master = it; error = null }, label = { Text("Мастер-пароль") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        if (creating) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = confirm, onValueChange = { confirm = it; error = null }, label = { Text("Повторите") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
        if (error != null) { Text(error!!, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)) }
        Button(
            onClick = {
                if (path.isEmpty()) { error = "Выберите файл"; return@Button }
                if (creating) {
                    if (master != confirm) { error = "Пароли не совпадают"; return@Button }
                    if (!session.create(Path.of(path), master.toCharArray())) error = "Не удалось записать vault"
                } else {
                    error = when (val r = session.unlock(Path.of(path), master.toCharArray())) {
                        is DesktopSession.UnlockResult.Success -> null
                        is DesktopSession.UnlockResult.WrongMaster -> "Неверный мастер-пароль"
                        is DesktopSession.UnlockResult.CorruptFile -> "Файл повреждён или не PMV3"
                        is DesktopSession.UnlockResult.IoError -> "Не удалось прочитать файл"
                        is DesktopSession.UnlockResult.Delayed -> "Подождите ${r.remainingMs / 1000 + 1} с"
                    }
                }
                master = ""; confirm = ""
            },
            enabled = master.isNotEmpty() && (!creating || confirm.isNotEmpty()),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (creating) "Создать vault" else "Разблокировать") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { creating = !creating; error = null }) {
            Text(if (creating) "У меня уже есть vault" else "Создать новый vault")
        }
    }
}

@Composable
private fun ListPane(session: DesktopSession, clipboard: DesktopClipboard, onSettings: () -> Unit) {
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
            TextButton(onClick = onSettings) { Text("Настройки") }
            TextButton(onClick = { session.lock() }) { Text("Лок") }
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