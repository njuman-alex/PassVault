package com.c0dex.passvault.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.c0dex.core.util.SearchHighlight

/**
 * Текст с подсветкой фрагментов, совпавших с поисковым запросом.
 * Цвет подсветки — системный colorScheme.primary (как остальные акценты проекта).
 */
@Composable
fun HighlightedText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val highlight = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        append(text)
        SearchHighlight.ranges(text, query).forEach { r ->
            addStyle(SpanStyle(color = highlight, fontWeight = FontWeight.SemiBold), r.first, r.endExclusive)
        }
    }
    Text(annotated, modifier = modifier, style = style, maxLines = maxLines, overflow = overflow)
}

@Preview(showBackground = true, name = "Highlighted")
@Composable
private fun HighlightedPreview() = MaterialTheme {
    HighlightedText(text = "Госуслуги — вход через ЕСИА", query = "госу")
}

@Preview(showBackground = true, name = "NoQuery")
@Composable
private fun PlainPreview() = MaterialTheme {
    HighlightedText(text = "Госуслуги — вход через ЕСИА", query = "")
}