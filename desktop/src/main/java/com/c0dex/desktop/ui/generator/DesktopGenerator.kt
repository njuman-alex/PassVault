package com.c0dex.desktop.ui.generator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.c0dex.core.generator.PasswordGenerator

data class GenState(
    val mode: Int = 0, val length: Int = 16,
    val lower: Boolean = true, val upper: Boolean = true, val digits: Boolean = true, val special: Boolean = false,
    val excludeLookalikes: Boolean = true, val requireEachGroup: Boolean = true,
    val wordCount: Int = 6, val separator: String = "-", val capitalize: Boolean = false
)

private fun GenState.opts() = PasswordGenerator.Options(length, lower, upper, digits, special, excludeLookalikes, requireEachGroup)
private fun GenState.popts() = PasswordGenerator.PassphraseOptions(wordCount, separator, capitalize)

@Composable
fun DesktopGenerator(onDismiss: () -> Unit, onUse: (String) -> Unit, onCopy: (String) -> Unit) {
    val words = remember { runCatching { PasswordGenerator.Wordlists.effLarge() }.getOrDefault(emptyList()) }
    var s by remember { mutableStateOf(GenState()) }
    var nonce by remember { mutableStateOf(0) }
    val result = remember(s, nonce, words) {
        runCatching {
            if (s.mode == 0) PasswordGenerator.generate(s.opts())
            else PasswordGenerator.generatePassphrase(s.popts(), words)
        }.getOrDefault("")
    }
    val entropy = remember(s, words) {
        runCatching {
            if (s.mode == 0) PasswordGenerator.entropyBits(s.opts())
            else PasswordGenerator.passphraseEntropyBits(s.popts(), words.size)
        }.getOrDefault(0.0)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Генератор") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = s.mode == 0, onClick = { s = s.copy(mode = 0) }, label = { Text("Пароль") })
                    FilterChip(selected = s.mode == 1, onClick = { s = s.copy(mode = 1) }, label = { Text("Passphrase") })
                }
                Spacer(Modifier.height(8.dp))
                if (s.mode == 0) {
                    Text("Длина: ${s.length}")
                    Slider(value = s.length.toFloat(), onValueChange = { s = s.copy(length = it.toInt()) }, valueRange = 8f..64f, steps = 55)
                    Toggle("Строчные (a-z)", s.lower) { s = s.copy(lower = !s.lower) }
                    Toggle("Заглавные (A-Z)", s.upper) { s = s.copy(upper = !s.upper) }
                    Toggle("Цифры (0-9)", s.digits) { s = s.copy(digits = !s.digits) }
                    Toggle("Спецсимволы (!@#…)", s.special) { s = s.copy(special = !s.special) }
                    Toggle("Исключать похожие (l,I,o,O,0,1)", s.excludeLookalikes) { s = s.copy(excludeLookalikes = !s.excludeLookalikes) }
                    Toggle("Гарантировать каждую группу", s.requireEachGroup) { s = s.copy(requireEachGroup = !s.requireEachGroup) }
                } else {
                    Text("Слов: ${s.wordCount}")
                    Slider(value = s.wordCount.toFloat(), onValueChange = { s = s.copy(wordCount = it.toInt()) }, valueRange = 3f..12f, steps = 8)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("-", ".", "_", " ").forEach { sep ->
                            FilterChip(selected = s.separator == sep, onClick = { s = s.copy(separator = sep) }, label = { Text(if (sep == " ") "пробел" else sep) })
                        }
                    }
                    Toggle("Каждое слово с заглавной", s.capitalize) { s = s.copy(capitalize = !s.capitalize) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = result, onValueChange = {}, readOnly = true, label = { Text("Результат") }, modifier = Modifier.fillMaxWidth())
                Text(if (result.isEmpty()) "проверьте длину/группы или словарь" else "~${entropy.toInt()} бит", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nonce++ }) { Text("🎲 Ещё") }
                    OutlinedButton(onClick = { onCopy(result) }, enabled = result.isNotEmpty()) { Text("📋 Копировать") }
                }
            }
        },
        confirmButton = { Button(onClick = { onUse(result) }, enabled = result.isNotEmpty()) { Text("Вставить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}