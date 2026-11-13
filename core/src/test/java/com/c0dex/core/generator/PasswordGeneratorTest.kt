package com.c0dex.core.generator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    private val words = listOf("alpha", "bravo", "charlie", "delta", "echo", "foxtrot")

    @Test
    fun lengthRespected() {
        repeat(20) {
            assertEquals(24, PasswordGenerator.generate(PasswordGenerator.Options(length = 24)).length)
        }
    }

    @Test
    fun onlyEnabledGroupsUsed() {
        val o = PasswordGenerator.Options(lower = false, upper = false, special = false, digits = true)
        repeat(20) {
            assertTrue(PasswordGenerator.generate(o).all { it in "23456789" })
        }
    }

    @Test
    fun requireEachGroupGuaranteesPresence() {
        val o = PasswordGenerator.Options(length = 8, special = true)
        repeat(50) {
            val s = PasswordGenerator.generate(o)
            assertTrue(s.any { it in "abcdefghijkmnpqrstuvwxyz" })
            assertTrue(s.any { it in "ABCDEFGHJKMNPQRSTUVWXYZ" })
            assertTrue(s.any { it in "23456789" })
            assertTrue(s.any { it in "!@#$%^&*()_+-=[]{}|;:,.<>?" })
        }
    }

    @Test
    fun lookalikesExcluded() {
        val o = PasswordGenerator.Options(length = 32, special = false)
        repeat(30) {
            assertTrue(PasswordGenerator.generate(o).none { it in "lIoO01" })
        }
    }

    @Test
    fun lookalikesCanBeKept() {
        val o = PasswordGenerator.Options(length = 200, excludeLookalikes = false, special = false)
        val s = PasswordGenerator.generate(o)
        assertTrue(s.any { it in "lIoO01" })
    }

    @Test
    fun noGroupsThrows() {
        val o = PasswordGenerator.Options(lower = false, upper = false, digits = false, special = false)
        assertThrows(IllegalArgumentException::class.java) { PasswordGenerator.generate(o) }
    }

    @Test
    fun tooShortForGroupsThrows() {
        val o = PasswordGenerator.Options(length = 2, special = true)
        assertThrows(IllegalArgumentException::class.java) { PasswordGenerator.generate(o) }
    }

    @Test
    fun entropyGrowsWithLength() {
        val a = PasswordGenerator.entropyBits(PasswordGenerator.Options(length = 8))
        val b = PasswordGenerator.entropyBits(PasswordGenerator.Options(length = 16))
        assertTrue(b > a)
        assertTrue(a > 0)
    }

    @Test
    fun passphraseShapeAndCapitalize() {
        val o = PasswordGenerator.PassphraseOptions(wordCount = 4, separator = ".", capitalize = true)
        val s = PasswordGenerator.generatePassphrase(o, words)
        val parts = s.split(".")
        assertEquals(4, parts.size)
        assertTrue(parts.all { it[0].isUpperCase() })
    }

    @Test
    fun passphraseEntropyMatchesWordlist() {
        val o = PasswordGenerator.PassphraseOptions(wordCount = 6)
        val bits = PasswordGenerator.passphraseEntropyBits(o, words.size)
        assertEquals(6 * kotlin.math.log2(words.size.toDouble()), bits, 1e-9)
    }

    @Test
    fun consecutiveGenerationsDiffer() {
        val a = PasswordGenerator.generate(PasswordGenerator.Options(length = 16))
        val b = PasswordGenerator.generate(PasswordGenerator.Options(length = 16))
        assertFalse(a == b)
    }
}