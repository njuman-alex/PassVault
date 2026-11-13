@file:OptIn(ExperimentalMaterial3Api::class)

package com.c0dex.passvault.ui.vault

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.c0dex.core.generator.PasswordGenerator
import com.c0dex.passvault.data.VaultRepository
import com.c0dex.passvault.session.ClipboardHelper
import com.c0dex.passvault.ui.theme.PassVaultTheme

data class DetailUiState(
    val description: String = "",
    val password: String = "",
    val revealed: Boolean = false,
    val editing: Boolean = false,
    val missing: Boolean = false
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
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.missing) "Запись" else state.description) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
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
                    OutlinedButton(onClick = onGenerate) { Text("🎲 Сгенерировать") }
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
    var revealed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    DetailContent(
        state = DetailUiState(desc, pass, revealed, editing, missing = record == null),
        onDescription = { desc = it },
        onPassword = { pass = it },
        onToggleReveal = { revealed = !revealed },
        onToggleEdit = {
            if (editing) { desc = record?.description ?: ""; pass = record?.password ?: "" }
            editing = !editing
        },
        onSave = {
            if (repo.update(id, desc, pass)) { record = repo.get(id); editing = false }
        },
        onDelete = { confirmDelete = true },
        onCopy = { clipboard.copySensitive("password", pass) },
        onGenerate = { pass = PasswordGenerator.generate(); editing = true },
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
}

@Preview(showBackground = true, name = "View")
@Composable
private fun DetailViewPreview() = PassVaultTheme {
    DetailContent(DetailUiState("Госуслуги", "secret-123"), {}, {}, {}, {}, {}, {}, {}, {}, {})
}

@Preview(showBackground = true, name = "Edit")
@Composable
private fun DetailEditPreview() = PassVaultTheme {
    DetailContent(DetailUiState("Госуслуги", "secret-123", editing = true), {}, {}, {}, {}, {}, {}, {}, {}, {})
}