package com.c0dex.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class KeyHierarchyTest {

    private val light = Argon2Kdf.Params(
        memoryKiB = 8192, iterations = 1, parallelism = 1,
        salt = ByteArray(16) { it.toByte() }
    )
    private val kek = KeyHierarchy.deriveKek("старый-мастер".toCharArray(), light)
    private val newKek = KeyHierarchy.deriveKek("новый-мастер".toCharArray(), light)

    @Test
    fun newDekIs32AndUnique() {
        val a = KeyHierarchy.newDek()
        val b = KeyHierarchy.newDek()
        assertArrayEquals(ByteArray(32), ByteArray(32)) // sanity
        assertFalse(a.contentEquals(b))
        assert(a.size == 32)
    }

    @Test
    fun wrapUnwrapRoundtrip() {
        val dek = KeyHierarchy.newDek()
        val wrap = KeyHierarchy.wrapDek(dek, kek)
        assertArrayEquals(dek, KeyHierarchy.unwrapDek(wrap, kek))
    }

    @Test
    fun wrongKekFails() {
        val wrap = KeyHierarchy.wrapDek(KeyHierarchy.newDek(), kek)
        assertNull(KeyHierarchy.unwrapDek(wrap, newKek))
    }

    @Test
    fun tamperedCiphertextFails() {
        val wrap = KeyHierarchy.wrapDek(KeyHierarchy.newDek(), kek)
        val broken = wrap.ct.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertNull(KeyHierarchy.unwrapDek(wrap.copy(ct = broken), kek))
    }

    @Test
    fun wrapTypeMismatchFails() {
        val wrap = KeyHierarchy.wrapDek(KeyHierarchy.newDek(), kek) // type=master
        val forged = wrap.copy(type = "recovery")
        assertNull(KeyHierarchy.unwrapDek(forged, kek))
    }

    @Test
    fun rotateKeepsDekAndKillsOldKek() {
        val dek = KeyHierarchy.newDek()
        val wrap = KeyHierarchy.wrapDek(dek, kek)
        val rotated = KeyHierarchy.rotateMaster(wrap, kek, newKek)!!
        assertArrayEquals(dek, KeyHierarchy.unwrapDek(rotated, newKek))
        assertNull(KeyHierarchy.unwrapDek(rotated, kek))
    }

    @Test
    fun rotateWithWrongOldKekFails() {
        val wrap = KeyHierarchy.wrapDek(KeyHierarchy.newDek(), kek)
        assertNull(KeyHierarchy.rotateMaster(wrap, newKek, kek))
    }

    @Test
    fun wrapValidatesSizesAndType() {
        val dek = KeyHierarchy.newDek()
        assertThrows(IllegalArgumentException::class.java) { KeyHierarchy.wrapDek(ByteArray(16), kek) }
        assertThrows(IllegalArgumentException::class.java) { KeyHierarchy.wrapDek(dek, ByteArray(16)) }
        assertThrows(IllegalArgumentException::class.java) { KeyHierarchy.wrapDek(dek, kek, " ") }
    }

    @Test
    fun unwrapRejectsWrongDekSize() {
        // вручную кладём в обёртку 16-байтовый payload вместо 32
        val enc = AesGcm.encrypt(kek, ByteArray(16), "master".toByteArray())
        val wrap = KeyHierarchy.Wrap("master", enc.iv, enc.ciphertext)
        assertNull(KeyHierarchy.unwrapDek(wrap, kek))
    }
}