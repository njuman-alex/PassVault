@file:OptIn(ExperimentalMaterial3Api::class)

package com.c0dex.passvault.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.c0dex.passvault.data.VaultRepository
import com.c0dex.passvault.session.ClipboardHelper
import com.c0dex.passvault.ui.generator.GeneratorDialog
import com.c0dex.passvault.ui.theme.PassVaultTheme

data class DetailUiState(
    val description: String = "",
    val password: String = "",
    val revealed: Boolean = false,
    val editing: Boolean = false,
    val missing: Boolean = false,
    val login: String = ""
)

@Composable
fun DetailContent(
    state: DetailUiState,
    onDescription: (String) -> Unit,
    onPassword: (String) -> Unit,
    onToggleReveal: () -> Unit,
    onToggleEdit: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onGenerate: () -> Unit,
    onBack: () -> Unit,
    onLogin: (String) -> Unit = {},
    onCopyLogin: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                //title = { Text(if (state.missing) "Запись" else state.description) },
                title = { Text("") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    if (!state.missing) {
                        TextButton(onClick = onToggleEdit) { Text(if (state.editing) "Отмена" else "Изменить") }
                        TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.missing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Запись не найдена") }
                return@Column
            }
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescription,
                label = { Text("Описание") },
                readOnly = !state.editing,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            // D-015: логин между описанием и парольным блоком
            OutlinedTextField(
                value = state.login,
                onValueChange = onLogin,
                label = { Text("Логин") },
                readOnly = !state.editing,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = onCopyLogin, enabled = state.login.isNotEmpty()) { Text("📋 Копировать логин") }
            OutlinedTextField(
                value = state.password,
                onValueChange = onPassword,
                label = { Text("Пароль") },
                readOnly = !state.editing,
                singleLine = true,
                visualTransformation = if (state.revealed) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleReveal) { Text(if (state.revealed) "🙈 Скрыть" else "👁 Показать") }
                OutlinedButton(onClick = onCopy) { Text("📋 Копировать") }
            }
            if (state.editing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    //OutlinedButton(onClick = onGenerate) { Text("🎲 Сгенерировать") }
                    //OutlinedButton(onClick = { showGen = true }) { Text("🎲 Генератор…") }
                    OutlinedButton(onClick = onGenerate) { Text("🎲 Генератор…") }
                    Button(onClick = onSave, enabled = state.description.isNotEmpty() && state.password.isNotEmpty()) { Text("Сохранить") }
                }
            }
        }
    }
}

@Composable
fun DetailScreen(
    id: String,
    repo: VaultRepository,
    clipboard: ClipboardHelper,
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

    DetailContent(
        state = DetailUiState(desc, pass, revealed, editing, missing = record == null, login = login),
        onDescription = { desc = it },
        onPassword = { pass = it },
        onLogin = { login = it },
        onToggleReveal = { revealed = !revealed },
        onToggleEdit = {
            if (editing) { desc = record?.description ?: ""; pass = record?.password ?: ""; login = record?.login ?: "" }
            editing = !editing
        },
        onSave = {
            if (repo.update(id, desc, pass, login)) { record = repo.get(id); editing = false }
        },
        onDelete = { confirmDelete = true },
        onCopy = { clipboard.copySensitive("password", pass) },
        onCopyLogin = { clipboard.copySensitive("login", login) },
        //onGenerate = { pass = PasswordGenerator.generate(); editing = true },
        onGenerate = { showGen = true },
        onBack = onBack
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить запись?") },
            text = { Text("«${record?.description}» будет помечена удалённой (tombstone) и исчезнет из списка на всех устройствах после синка.") },
            confirmButton = {
                Button(onClick = { confirmDelete = false; if (repo.delete(id)) onBack() }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } }
        )
    }
    if (showGen) GeneratorDialog(onDismiss = { showGen = false }, onUse = { pass = it; editing = true; showGen = false })
}

@Preview(showBackground = true, name = "View")
@Composable
private fun DetailViewPreview() = PassVaultTheme {
    DetailContent(DetailUiState("Госуслуги", "secret-123", login = "user@mail"), {}, {}, {}, {}, {}, {}, {}, {}, {})
}

@Preview(showBackground = true, name = "Edit")
@Composable
private fun DetailEditPreview() = PassVaultTheme {
    DetailContent(DetailUiState("Госуслуги", "secret-123", editing = true, login = "user@mail"), {}, {}, {}, {}, {}, {}, {}, {}, {})
}