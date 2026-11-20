@file:OptIn(ExperimentalMaterial3Api::class)

package com.c0dex.passvault.ui.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.c0dex.core.util.TimeFormat
import com.c0dex.passvault.data.VaultRepository
import com.c0dex.passvault.session.SessionManager
import com.c0dex.passvault.ui.common.HighlightedText
import com.c0dex.passvault.ui.generator.GeneratorDialog
import com.c0dex.passvault.ui.theme.PassVaultTheme

data class VaultListUiState(
    val items: List<VaultRepository.RecordView> = emptyList(),
    val query: String = ""
)

@Composable
fun VaultListContent(
    state: VaultListUiState,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onLock: () -> Unit,
    onSettings: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PassVault") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Настройки") }
                    IconButton(onClick = onLock) { Icon(Icons.Default.Lock, "Заблокировать") }
                }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, "Добавить") } }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                label = { Text("Поиск") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
            val filtered = state.items.filter { it.description.contains(state.query, ignoreCase = true) }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.items.isEmpty()) "Пока пусто — добавьте первый пароль" else "Ничего не найдено",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { r ->
                        Card(modifier = Modifier.fillMaxWidth().clickable { onOpen(r.id) }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    //Text(r.description, style = MaterialTheme.typography.titleMedium)
                                    HighlightedText(r.description, state.query, style = MaterialTheme.typography.titleMedium)
                                    //Text("обновлено: ${r.updatedAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("обновлено: ${TimeFormat.human(r.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VaultListScreen(
    session: SessionManager,
    repo: VaultRepository,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit = {}
) {
    val sessionState by session.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(repo.list()) }
    var tick by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var desc by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var showGen by remember { mutableStateOf(false) }

    LaunchedEffect(tick, sessionState) { items = repo.list() }

    VaultListContent(
        state = VaultListUiState(items, query),
        onQuery = { query = it },
        onOpen = onOpen,
        onAdd = { showAdd = true },
        onLock = { session.lock() },
        onSettings = onSettings
    )

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Новый пароль") },
            text = {
                Column {
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Описание") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Пароль") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { showGen = true }) { Text("🎲 Генератор…") }
                }
            },
            confirmButton = {
                Button(
                    onClick = { if (repo.add(desc, pass)) { showAdd = false; desc = ""; pass = ""; tick++ } },
                    enabled = desc.isNotEmpty() && pass.isNotEmpty()
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Отмена") } }
        )
    }
    if (showGen) GeneratorDialog(onDismiss = { showGen = false }, onUse = { pass = it; showGen = false })
}

@Preview(showBackground = true, name = "List")
@Composable
private fun ListPreview() = PassVaultTheme {
    VaultListContent(
        state = VaultListUiState(
            items = listOf(
                VaultRepository.RecordView("1", "Госуслуги", "x", 1L),
                VaultRepository.RecordView("2", "Банк", "y", 2L)
            )
        ),
        onQuery = {}, onOpen = {}, onAdd = {}, onLock = {}
    )
}

@Preview(showBackground = true, name = "Empty")
@Composable
private fun ListEmptyPreview() = PassVaultTheme {
    VaultListContent(state = VaultListUiState(), onQuery = {}, onOpen = {}, onAdd = {}, onLock = {})
}

@Preview(showBackground = true, name = "SearchHighlight")
@Composable
private fun ListSearchPreview() = PassVaultTheme {
    VaultListContent(
        state = VaultListUiState(
            items = listOf(
                VaultRepository.RecordView("1", "Госуслуги", "x", 1L),
                VaultRepository.RecordView("2", "Банк", "y", 2L)
            ),
            query = "госу"
        ),
        onQuery = {}, onOpen = {}, onAdd = {}, onLock = {}
    )
}