package com.c0dex.core.util

/**
 * Диапазоны совпадений query в text: без регистра, все вхождения, без пересечений.
 * Пустой query или text → пустой список. Чистая логика, паритет Android/Desktop.
 */
object SearchHighlight {

    fun ranges(text: String, query: String): List<IntRange> {
        if (query.isEmpty() || text.isEmpty()) return emptyList()
        val out = mutableListOf<IntRange>()
        var i = text.indexOf(query, ignoreCase = true)
        while (i >= 0) {
            out += i until i + query.length
            i = text.indexOf(query, startIndex = i + query.length, ignoreCase = true)
        }
        return out
    }
}