package com.c0dex.desktop.ui.lock

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.c0dex.desktop.data.DesktopPrefs
import com.c0dex.desktop.session.DesktopSession
import com.c0dex.desktop.ui.chooseFile
import java.nio.file.Path

@Composable
fun LockPane(session: DesktopSession, prefs: DesktopPrefs) {
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
        Text(if (creating) "Создание нового сейфа" else "Файл: ${path.ifEmpty { "не выбран" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("Путь к файлу") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
                    val err = session.create(Path.of(path), master.toCharArray())
                    if (err != null) error = err
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
        ) { Text(if (creating) "Создать сейф-файл" else "Разблокировать") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { creating = !creating; error = null }) {
            Text(if (creating) "У меня уже есть сейф-файл" else "Создать новый сейф")
        }
    }
}