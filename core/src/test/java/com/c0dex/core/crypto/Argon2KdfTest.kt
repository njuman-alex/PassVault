package com.c0dex.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class Argon2KdfTest {

    private val password = "мастер-пароль-тест".toCharArray()
    private val salt = ByteArray(16) { it.toByte() }

    // Лёгкие параметры для быстрых тестов чувствительности
    private val light = Argon2Kdf.Params(memoryKiB = 8192, iterations = 1, parallelism = 1, salt = salt)

    @Test
    fun deterministicForSameInputs() {
        val a = Argon2Kdf.derive(password, light)
        val b = Argon2Kdf.derive(password, light)
        assertArrayEquals(a, b)
    }

    @Test
    fun keyLengthIs32() {
        assertEquals(32, Argon2Kdf.derive(password, light).size)
    }

    @Test
    fun defaultParamsProduce32ByteKey() {
        val params = Argon2Kdf.Params(salt = salt) // 64 MiB / 3 / 4
        assertEquals(32, Argon2Kdf.derive(password, params).size)
    }

    @Test
    fun saltChangesKey() {
        val other = light.copy(salt = ByteArray(16) { (it + 1).toByte() })
        assertFalse(Argon2Kdf.derive(password, light).contentEquals(Argon2Kdf.derive(password, other)))
    }

    @Test
    fun passwordChangesKey() {
        val other = "другой-пароль".toCharArray()
        assertFalse(Argon2Kdf.derive(password, light).contentEquals(Argon2Kdf.derive(other, light)))
    }

    @Test
    fun memoryChangesKey() {
        val other = light.copy(memoryKiB = 16384)
        assertFalse(Argon2Kdf.derive(password, light).contentEquals(Argon2Kdf.derive(password, other)))
    }

    @Test
    fun iterationsChangesKey() {
        val other = light.copy(iterations = 2)
        assertFalse(Argon2Kdf.derive(password, light).contentEquals(Argon2Kdf.derive(password, other)))
    }

    @Test
    fun parallelismChangesKey() {
        val other = light.copy(parallelism = 2, memoryKiB = 16384)
        assertFalse(Argon2Kdf.derive(password, light).contentEquals(Argon2Kdf.derive(password, other)))
    }

    @Test
    fun invalidParamsThrow() {
        assertThrows(IllegalArgumentException::class.java) {
            Argon2Kdf.derive(password, light.copy(iterations = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Argon2Kdf.derive(password, light.copy(parallelism = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Argon2Kdf.derive(password, light.copy(memoryKiB = 7))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Argon2Kdf.derive(password, light.copy(salt = ByteArray(8)))
        }
    }

    @Test
    fun passwordArrayNotMutated() {
        val copy = password.copyOf()
        Argon2Kdf.derive(password, light)
        assertArrayEquals(copy, password)
    }
}