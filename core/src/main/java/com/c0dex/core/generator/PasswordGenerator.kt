package com.c0dex.core.generator

import java.security.SecureRandom
import kotlin.math.log2

/**
 * Генератор паролей и passphrase (доменная логика, паритет Android/Windows).
 *
 * Контракт:
 *  - группы: lower/upper/digits/special; excludeLookalikes убирает l,I,o,O,0,1;
 *  - requireEachGroup гарантирует >=1 символ из каждой включённой группы;
 *  - порядок символов перемешивается (Fisher-Yates) — без позиционного смещения;
 *  - энтропия = length * log2(pool) (оценка сверху при requireEachGroup);
 *  - passphrase: список слов передаётся вызывающим (тестируемо), production берёт EFF large.
 */
object PasswordGenerator {

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SPECIAL = "!@#$%^&*()_+-=[]{}|;:,.<>?"
    private const val LOOKALIKES = "lIoO01"

    data class Options(
        val length: Int = 16,
        val lower: Boolean = true,
        val upper: Boolean = true,
        val digits: Boolean = true,
        val special: Boolean = false,
        val excludeLookalikes: Boolean = true,
        val requireEachGroup: Boolean = true
    )

    data class PassphraseOptions(
        val wordCount: Int = 6,
        val separator: String = "-",
        val capitalize: Boolean = false
    )

    fun groups(o: Options): List<String> {
        val g = mutableListOf<String>()
        if (o.lower) g += strip(LOWER, o)
        if (o.upper) g += strip(UPPER, o)
        if (o.digits) g += strip(DIGITS, o)
        if (o.special) g += SPECIAL
        return g.filter { it.isNotEmpty() }
    }

    private fun strip(s: String, o: Options): String =
        if (o.excludeLookalikes) s.filterNot { it in LOOKALIKES } else s

    fun pool(o: Options): String = groups(o).joinToString("")

    fun entropyBits(o: Options): Double {
        val p = pool(o).length
        require(p > 0) { "empty pool" }
        return o.length * log2(p.toDouble())
    }

    fun generate(o: Options = Options(), random: SecureRandom = SecureRandom()): String {
        val g = groups(o)
        require(g.isNotEmpty()) { "at least one group must be enabled" }
        require(o.length >= 1) { "length must be >= 1" }
        if (o.requireEachGroup) require(o.length >= g.size) { "length must be >= enabled group count" }

        val p = pool(o)
        val chars = ArrayList<Char>(o.length)
        if (o.requireEachGroup) for (group in g) chars += group[random.nextInt(group.length)]
        while (chars.size < o.length) chars += p[random.nextInt(p.length)]

        for (i in chars.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val t = chars[i]; chars[i] = chars[j]; chars[j] = t
        }
        return String(chars.toCharArray())
    }

    fun generatePassphrase(o: PassphraseOptions, words: List<String>, random: SecureRandom = SecureRandom()): String {
        require(words.isNotEmpty()) { "wordlist empty" }
        require(o.wordCount >= 1) { "wordCount must be >= 1" }
        return (1..o.wordCount).joinToString(o.separator) {
            val w = words[random.nextInt(words.size)]
            if (o.capitalize) w.replaceFirstChar { c -> c.uppercase() } else w
        }
    }

    fun passphraseEntropyBits(o: PassphraseOptions, wordlistSize: Int): Double {
        require(wordlistSize > 0) { "wordlist empty" }
        return o.wordCount * log2(wordlistSize.toDouble())
    }

    /** Production-словарь: EFF large (7776 слов). Файл кладётся в core/src/main/resources/words/. */
    object Wordlists {
        fun effLarge(): List<String> =
            PasswordGenerator::class.java.getResourceAsStream("/words/eff_large.txt")
                ?.bufferedReader()?.readLines()?.filter { it.isNotBlank() }
                ?: error("words/eff_large.txt missing in core resources")
    }
}