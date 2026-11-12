package com.c0dex.core.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM примитив по контракту PMV3 (FORMAT.md §2, §6).
 *
 * Контракт:
 *  - ключ ровно 32 байта (AES-256);
 *  - IV ровно 12 байт, случайный на каждую операцию шифрования;
 *  - тег аутентичности 128 бит;
 *  - AAD опционален (в PMV3 = id записи, защита от перестановки шифротекстов);
 *  - любая ошибка расшифровки (неверный ключ/IV/AAD, повреждение) -> null, без исключений.
 *
 * Платформенно-нейтрально: только JDK (javax.crypto), ноль Android-импортов.
 */
object AesGcm {

    const val KEY_SIZE_BYTES = 32
    const val IV_SIZE_BYTES = 12
    const val TAG_SIZE_BITS = 128

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ALGORITHM = "AES"

    private val random = SecureRandom()

    /** Результат шифрования: IV + шифротекст с тегом. */
    data class Encrypted(val iv: ByteArray, val ciphertext: ByteArray)

    /** Шифрование со случайным IV. Основной production-путь. */
    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): Encrypted {
        val iv = ByteArray(IV_SIZE_BYTES).also(random::nextBytes)
        return seal(key, iv, plaintext, aad)
    }

    /**
     * Детерминированное шифрование с заданным IV.
     * ТОЛЬКО для тест-векторов и кросс-платформенной сверки.
     * НИКОГДА не переиспользуй IV с тем же ключом в production (катастрофа для GCM).
     */
    fun seal(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): Encrypted {
        require(key.size == KEY_SIZE_BYTES) { "key must be $KEY_SIZE_BYTES bytes, got ${key.size}" }
        require(iv.size == IV_SIZE_BYTES) { "iv must be $IV_SIZE_BYTES bytes, got ${iv.size}" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, ALGORITHM), GCMParameterSpec(TAG_SIZE_BITS, iv))
        aad?.let(cipher::updateAAD)
        return Encrypted(iv, cipher.doFinal(plaintext))
    }

    /** Расшифровка. null при любой ошибке аутентичности/параметров. */
    fun decrypt(key: ByteArray, encrypted: Encrypted, aad: ByteArray? = null): ByteArray? =
        decrypt(key, encrypted.iv, encrypted.ciphertext, aad)

    fun decrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray? = null): ByteArray? {
        if (key.size != KEY_SIZE_BYTES || iv.size != IV_SIZE_BYTES) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, ALGORITHM), GCMParameterSpec(TAG_SIZE_BITS, iv))
            aad?.let(cipher::updateAAD)
            cipher.doFinal(ciphertext)
        } catch (e: GeneralSecurityException) {
            null
        }
    }
}