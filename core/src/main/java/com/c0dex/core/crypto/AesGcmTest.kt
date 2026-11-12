package com.c0dex.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AesGcmTest {

    private val key = ByteArray(32) { it.toByte() }
    private val otherKey = ByteArray(32) { (it + 1).toByte() }
    private val aad = "record-id-123".toByteArray()
    private val plaintext = "пароль с юникодом: ñ 日本語 🔐".toByteArray()

    @Test
    fun roundtripWithAad() {
        val enc = AesGcm.encrypt(key, plaintext, aad)
        assertArrayEquals(plaintext, AesGcm.decrypt(key, enc, aad))
    }

    @Test
    fun roundtripWithoutAad() {
        val enc = AesGcm.encrypt(key, plaintext)
        assertArrayEquals(plaintext, AesGcm.decrypt(key, enc))
    }

    @Test
    fun emptyPlaintextRoundtrip() {
        val enc = AesGcm.encrypt(key, ByteArray(0), aad)
        assertArrayEquals(ByteArray(0), AesGcm.decrypt(key, enc, aad))
    }

    @Test
    fun tamperedCiphertextFails() {
        val enc = AesGcm.encrypt(key, plaintext, aad)
        val broken = enc.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertNull(AesGcm.decrypt(key, enc.iv, broken, aad))
    }

    @Test
    fun tamperedAadFails() {
        val enc = AesGcm.encrypt(key, plaintext, aad)
        assertNull(AesGcm.decrypt(key, enc, "other-id".toByteArray()))
    }

    @Test
    fun wrongKeyFails() {
        val enc = AesGcm.encrypt(key, plaintext, aad)
        assertNull(AesGcm.decrypt(otherKey, enc, aad))
    }

    @Test
    fun wrongIvFails() {
        val enc = AesGcm.encrypt(key, plaintext, aad)
        val badIv = enc.iv.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertNull(AesGcm.decrypt(key, badIv, enc.ciphertext, aad))
    }

    @Test
    fun ivIsUniquePerEncrypt() {
        val a = AesGcm.encrypt(key, plaintext)
        val b = AesGcm.encrypt(key, plaintext)
        assertFalse(a.iv.contentEquals(b.iv))
    }

    @Test
    fun sealIsDeterministicForVectors() {
        val iv = ByteArray(12) { it.toByte() }
        val a = AesGcm.seal(key, iv, plaintext, aad)
        val b = AesGcm.seal(key, iv, plaintext, aad)
        assertArrayEquals(a.ciphertext, b.ciphertext)
        assertArrayEquals(plaintext, AesGcm.decrypt(key, a, aad))
    }

    @Test
    fun invalidKeySizeThrowsOnEncrypt() {
        assertThrows(IllegalArgumentException::class.java) {
            AesGcm.encrypt(ByteArray(16), plaintext)
        }
    }

    @Test
    fun invalidIvSizeThrowsOnSeal() {
        assertThrows(IllegalArgumentException::class.java) {
            AesGcm.seal(key, ByteArray(16), plaintext)
        }
    }

    @Test
    fun decryptWithBadSizesReturnsNull() {
        val enc = AesGcm.encrypt(key, plaintext)
        assertNull(AesGcm.decrypt(ByteArray(16), enc))
        assertNull(AesGcm.decrypt(key, ByteArray(16), enc.ciphertext))
    }

    @Test
    fun ciphertextOverheadIsTagOnly() {
        val enc = AesGcm.encrypt(key, plaintext)
        assertEquals(plaintext.size + 16, enc.ciphertext.size) // 128-битный тег
    }
}