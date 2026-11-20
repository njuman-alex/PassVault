package com.c0dex.core.util

import org.junit.Test
import org.junit.Assert.assertEquals


class SearchHighlightTest {

    @Test fun emptyQuery() = assertEquals(emptyList<IntRange>(), SearchHighlight.ranges("Госуслуги", ""))

    @Test fun emptyText() = assertEquals(emptyList<IntRange>(), SearchHighlight.ranges("", "го"))

    @Test fun caseInsensitive() = assertEquals(listOf(0..1), SearchHighlight.ranges("Госуслуги", "го"))

    @Test fun allOccurrences() = assertEquals(listOf(0..2, 4..6), SearchHighlight.ranges("abc abc", "ABC"))

    @Test fun noMatch() = assertEquals(emptyList<IntRange>(), SearchHighlight.ranges("Госуслуги", "яндекс"))

    @Test fun unicodeMiddle() = assertEquals(listOf(4..6), SearchHighlight.ranges("Мой Госуслуги вход", "гос"))
}