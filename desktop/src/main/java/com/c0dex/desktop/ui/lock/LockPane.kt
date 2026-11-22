package com.c0dex.desktop.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.c0dex.desktop.data.DesktopPrefs
import com.c0dex.desktop.session.DesktopSession
import com.c0dex.desktop.ui.chooseFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

@Composable
fun LockPane(session: DesktopSession, prefs: DesktopPrefs) {
    val scope = rememberCoroutineScope()
    var master by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(prefs.vaultPath == null) }
    var path by remember { mutableStateOf(prefs.vaultPath ?: "") }
    var busy by remember { mutableStateOf(false) }

    val fileName = path.takeIf { it.isNotEmpty() }
        ?.let { runCatching { Path.of(it).fileName.toString() }.getOrDefault(it) }
    //val showChooser = creating || path.isEmpty()

    // Первичное действие: разблокировать (unlock) или создать сейф (create).
    // Вынесено, чтобы кнопка и Enter-клавиша вызывали ровно один и тот же код.
    val primaryEnabled = !busy && master.isNotEmpty() && (!creating || confirm.isNotEmpty())

    val primaryClick: () -> Unit = click@{
        if (busy) return@click
        if (creating) {
            if (master != confirm) { error = "Пароли не совпадают"; return@click }
            val p = chooseFile(save = true) ?: return@click
            if (Files.exists(p)) { error = "Файл уже существует — выберите другое имя (create не перезаписывает сейф)"; return@click }
            val m = master.toCharArray()
            scope.launch {
                busy = true
                val err = withContext(Dispatchers.Default) { session.create(p, m) }
                busy = false
                master = ""; confirm = ""
                if (err != null) error = err
            }
        } else {
            if (path.isEmpty()) { error = "Выберите файл"; return@click }
            val p = Path.of(path)
            val m = master.toCharArray()
            scope.launch {
                busy = true
                val r = withContext(Dispatchers.Default) { session.unlock(p, m) }
                busy = false
                master = ""; confirm = ""
                error = when (r) {
                    is DesktopSession.UnlockResult.Success -> null
                    is DesktopSession.UnlockResult.WrongMaster -> "Неверный мастер-пароль"
                    is DesktopSession.UnlockResult.CorruptFile -> "Файл повреждён или не .qdex"
                    is DesktopSession.UnlockResult.IoError -> "Не удалось прочитать файл"
                    is DesktopSession.UnlockResult.Delayed -> "Подождите ${r.remainingMs / 1000 + 1} с"
                }
            }
        }
    }

    // Enter = клик по «Разблокировать»; в create-режиме всё остаётся мышкой.
    val onEnterKey: (KeyEvent) -> Boolean = { event ->
        if (!creating &&
            event.type == KeyEventType.KeyDown &&
            (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
            primaryEnabled
        ) {
            primaryClick()
            true
        } else false
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp).onKeyEvent(onEnterKey),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("PassVault", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (creating) "Создание нового сейфа" else "Сейф-файл: ${fileName ?: "не выбран"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = master, onValueChange = { master = it; error = null },
            label = { Text("Мастер-пароль") }, visualTransformation = PasswordVisualTransformation(),
            singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth()
        )
        if (creating) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = confirm, onValueChange = { confirm = it; error = null },
                label = { Text("Повторите") }, visualTransformation = PasswordVisualTransformation(),
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
        if (error != null) { Text(error!!, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)) }

        // было:
        //        Button(
        //            onClick = {
        //                if (busy) return@Button
        //                if (creating) { ... } else { ... }
        //            },
        //            enabled = !busy && master.isNotEmpty() && (!creating || confirm.isNotEmpty()),
        //            modifier = Modifier.fillMaxWidth()
        //        ) { ... }
        Button(onClick = primaryClick, enabled = primaryEnabled, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (busy) (if (creating) "Создаём сейф…" else "Проверяем пароль…")
                else (if (creating) "Создать сейф-файл" else "Разблокировать")
            )
        }
        Spacer(Modifier.height(24.dp))
        if (!creating) {
            TextButton(
                onClick = { chooseFile(save = false)?.let { path = it.toString() } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (path.isNotEmpty()) "Выбрать другой сейф-файл" else "Выбрать сейф-файл")
            }
            Spacer(Modifier.height(8.dp))
        }
        TextButton(onClick = { creating = !creating; error = null }, enabled = !busy) {
            Text(if (creating) "У меня уже есть сейф-файл" else "Создать новый сейф")
        }
    }
}