package com.c0dex.passvault.ui.generator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.c0dex.core.generator.PasswordGenerator
import com.c0dex.passvault.ui.theme.PassVaultTheme

data class GeneratorUiState(
    val mode: Int = 0,                 // 0 = пароль, 1 = passphrase
    val length: Int = 16,
    val lower: Boolean = true,
    val upper: Boolean = true,
    val digits: Boolean = true,
    val special: Boolean = false,
    val excludeLookalikes: Boolean = true,
    val requireEachGroup: Boolean = true,
    val wordCount: Int = 6,
    val separator: String = "-",
    val capitalize: Boolean = false,
    val result: String = "",
    val entropy: Double = 0.0
)

private fun GeneratorUiState.toOptions() = PasswordGenerator.Options(
    length, lower, upper, digits, special, excludeLookalikes, requireEachGroup
)

private fun GeneratorUiState.toPassphraseOptions() = PasswordGenerator.PassphraseOptions(
    wordCount, separator, capitalize
)

@Composable
fun GeneratorContent(
    state: GeneratorUiState,
    onMode: (Int) -> Unit,
    onLength: (Int) -> Unit,
    onToggle: (String) -> Unit,
    onWordCount: (Int) -> Unit,
    onSeparator: (String) -> Unit,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.mode == 0, onClick = { onMode(0) }, label = { Text("Пароль") })
            FilterChip(selected = state.mode == 1, onClick = { onMode(1) }, label = { Text("Кодовая фраза") })
        }

        if (state.mode == 0) {
            Text("Длина: ${state.length}")
            Slider(
                value = state.length.toFloat(),
                onValueChange = { onLength(it.toInt()) },
                valueRange = 8f..64f,
                steps = 55
            )
            ToggleRow("Строчные (a-z)", state.lower) { onToggle("lower") }
            ToggleRow("Заглавные (A-Z)", state.upper) { onToggle("upper") }
            ToggleRow("Цифры (0-9)", state.digits) { onToggle("digits") }
            ToggleRow("Спецсимволы (!@#…)", state.special) { onToggle("special") }
            ToggleRow("Исключать похожие (l,I,o,O,0,1)", state.excludeLookalikes) { onToggle("lookalikes") }
            ToggleRow("Гарантировать каждую группу", state.requireEachGroup) { onToggle("require") }
        } else {
            Text("Слов: ${state.wordCount}")
            Slider(
                value = state.wordCount.toFloat(),
                onValueChange = { onWordCount(it.toInt()) },
                valueRange = 3f..12f,
                steps = 8
            )
            Text("Разделитель")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("-", ".", "_", " ").forEach { s ->
                    FilterChip(selected = state.separator == s, onClick = { onSeparator(s) }, label = { Text(if (s == " ") "пробел" else s) })
                }
            }
            ToggleRow("Каждое слово с заглавной", state.capitalize) { onToggle("capitalize") }
        }

        OutlinedTextField(
            value = state.result,
            onValueChange = {},
            readOnly = true,
            label = { Text("Результат") },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            if (state.result.isEmpty()) "Не удалось: проверьте длину/группы или словарь"
            //else "Энтропия: ~${state.entropy.toInt()} бит",
            else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRegenerate) { Text("🎲 Ещё") }
            OutlinedButton(onClick = onCopy, enabled = state.result.isNotEmpty()) { Text("📋 Копировать") }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
// было: fun GeneratorDialog(onDismiss: () -> Unit, onUse: (String) -> Unit) {
fun GeneratorDialog(onDismiss: () -> Unit, onUse: (String) -> Unit, onCopy: (String) -> Unit) {
    // было:
    // val context = LocalContext.current
    // val scope = rememberCoroutineScope()
    // val prefs = remember { SettingsPrefs(context) }
    // val clipboard = remember { ClipboardHelper(context, prefs, scope) }
    val words = remember { runCatching { PasswordGenerator.Wordlists.effLarge() }.getOrDefault(emptyList()) }

    var state by remember { mutableStateOf(GeneratorUiState()) }
    var nonce by remember { mutableStateOf(0) }

    val result = remember(state, nonce, words) {
        runCatching {
            if (state.mode == 0) PasswordGenerator.generate(state.toOptions())
            else PasswordGenerator.generatePassphrase(state.toPassphraseOptions(), words)
        }.getOrDefault("")
    }
    val entropy = remember(state, words) {
        runCatching {
            if (state.mode == 0) PasswordGenerator.entropyBits(state.toOptions())
            else PasswordGenerator.passphraseEntropyBits(state.toPassphraseOptions(), words.size)
        }.getOrDefault(0.0)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .fillMaxHeight(0.94f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Генератор", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Закрыть") }
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    GeneratorContent(
                        state = state.copy(result = result, entropy = entropy),
                        onMode = { state = state.copy(mode = it) },
                        onLength = { state = state.copy(length = it) },
                        onToggle = { key ->
                            state = when (key) {
                                "lower" -> state.copy(lower = !state.lower)
                                "upper" -> state.copy(upper = !state.upper)
                                "digits" -> state.copy(digits = !state.digits)
                                "special" -> state.copy(special = !state.special)
                                "lookalikes" -> state.copy(excludeLookalikes = !state.excludeLookalikes)
                                "require" -> state.copy(requireEachGroup = !state.requireEachGroup)
                                "capitalize" -> state.copy(capitalize = !state.capitalize)
                                else -> state
                            }
                        },
                        onWordCount = { state = state.copy(wordCount = it) },
                        onSeparator = { state = state.copy(separator = it) },
                        onRegenerate = { nonce++ },
                        // было: onCopy = { clipboard.copySensitive("password", result) }
                        onCopy = { onCopy(result) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onUse(result) }, enabled = result.isNotEmpty()) { Text("Вставить") }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Generator")
@Composable
private fun GeneratorPreview() = PassVaultTheme {
    GeneratorContent(GeneratorUiState(result = "xK9#mQ2vLp4!zR7t", entropy = 96.0), {}, {}, {}, {}, {}, {}, {})
}