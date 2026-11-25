package com.c0dex.desktop.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.c0dex.desktop.data.DesktopRepository
import com.c0dex.desktop.session.DesktopClipboard
import com.c0dex.desktop.ui.generator.DesktopGenerator

/** Экран записи (зеркало Android DetailScreen): просмотр / правка / удаление с подтверждением. */
@Composable
fun DetailPane(
    id: String,
    repo: DesktopRepository,
    clipboard: DesktopClipboard,
    onBack: () -> Unit
) {
    var record by remember(id) { mutableStateOf(repo.get(id)) }
    var desc by remember(record) { mutableStateOf(record?.description ?: "") }
    var pass by remember(record) { mutableStateOf(record?.password ?: "") }
    var login by remember(record) { mutableStateOf(record?.login ?: "") }
    var revealed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showGen by remember { mutableStateOf(false) }
    var ioError by remember { mutableStateOf<String?>(null) }   // пункт 1: фидбек провала сохранения/удаления

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (record != null) {
                // было:
                // TextButton(onClick = {
                //     if (editing) { desc = record?.description ?: ""; pass = record?.password ?: ""; login = record?.login ?: "" }
                //     editing = !editing
                // }) { Text(if (editing) "Отмена" else "Изменить") }
                TextButton(onClick = {
                    if (editing) { desc = record?.description ?: ""; pass = record?.password ?: ""; login = record?.login ?: "" }
                    editing = !editing
                    ioError = null   // пункт 1: сброс ошибки при входе/выходе из правки
                }) { Text(if (editing) "Отмена" else "Изменить") }
                TextButton(onClick = { confirmDelete = true }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            }
        }
        // пункт 1: причина провала видна сразу под шапкой
        if (ioError != null) {
            Text(ioError!!, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(16.dp))

        // контент занимает всё свободное место → «Назад» прижата к низу (как «Добавить» в списке)
        Column(Modifier.weight(1f)) {
            if (record == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Запись не найдена")
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = desc, onValueChange = { desc = it }, label = { Text("Описание") },
                        readOnly = !editing, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    // D-015: логин между описанием и парольным блоком
                    OutlinedTextField(
                        value = login, onValueChange = { login = it }, label = { Text("Логин") },
                        readOnly = !editing, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = { clipboard.copy(login) }, enabled = login.isNotEmpty()) { Text("📋 Копировать логин") }
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it }, label = { Text("Пароль") },
                        readOnly = !editing, singleLine = true,
                        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { revealed = !revealed }) { Text(if (revealed) "🙈 Скрыть" else "👁 Показать") }
                        OutlinedButton(onClick = { clipboard.copy(pass) }) { Text("📋 Копировать") }
                    }
                    if (editing) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showGen = true }) { Text("🎲 Генератор…") }
                            Button(
                                // было: onClick = { if (repo.update(id, desc, pass, login)) { record = repo.get(id); editing = false } },
                                onClick = {
                                    if (repo.update(id, desc, pass, login)) { record = repo.get(id); editing = false; ioError = null }
                                    else ioError = "Не удалось сохранить — файл изменён снаружи или повреждён"
                                },
                                enabled = desc.isNotEmpty() && pass.isNotEmpty()
                            ) { Text("Сохранить") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Назад") }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Удалить запись?") },
        text = { Text("«${record?.description}» будет помечена удалённой (tombstone) и исчезнет из списка на всех устройствах после синка.") },
        confirmButton = {
            // было: Button(onClick = { confirmDelete = false; if (repo.delete(id)) onBack() }) { Text("Удалить") }
            Button(onClick = {
                confirmDelete = false
                if (repo.delete(id)) onBack()
                else ioError = "Не удалось удалить — файл изменён снаружи или повреждён"
            }) { Text("Удалить") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } }
    )

    if (showGen) DesktopGenerator(
        onDismiss = { showGen = false },
        onUse = { pass = it; editing = true; showGen = false },
        onCopy = { clipboard.copy(it) }
    )
}