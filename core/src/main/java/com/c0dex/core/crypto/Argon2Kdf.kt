package com.c0dex.core.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

/**
 * Argon2id KDF по контракту PMV3 (FORMAT.md §2, §3).
 *
 * мастер-пароль + salt + params --Argon2id--> KEK (32 байта для AES-256).
 *
 * Контракт:
 *  - версия Argon2 = 1.3, тип = id;
 *  - salt ровно 16 байт;
 *  - выход ровно 32 байта;
 *  - параметры живут в заголовке контейнера (можно поднять стойкость позже);
 *  - пароль принимается как CharArray, чтобы вызывающий мог затереть его после derive;
 *  - промежуточные байтовые копии пароля затираются внутри derive.
 *
 * Платформенно-нейтрально: BouncyCastle (чистая Java), ноль Android-импортов, ноль JNI.
 */
object Argon2Kdf {

    const val KEY_SIZE_BYTES = 32
    const val SALT_SIZE_BYTES = 16

    const val DEFAULT_MEMORY_KIB = 65536   // 64 MiB
    const val DEFAULT_ITERATIONS = 3
    const val DEFAULT_PARALLELISM = 4

    private val random = SecureRandom()

    /** Параметры KDF из заголовка контейнера. */
    data class Params(
        val memoryKiB: Int = DEFAULT_MEMORY_KIB,
        val iterations: Int = DEFAULT_ITERATIONS,
        val parallelism: Int = DEFAULT_PARALLELISM,
        val salt: ByteArray
    )

    fun newSalt(): ByteArray = ByteArray(SALT_SIZE_BYTES).also(random::nextBytes)

    /** Вывод KEK. Не мутирует переданный password. */
    fun derive(password: CharArray, params: Params): ByteArray {
        require(params.iterations >= 1) { "iterations must be >= 1" }
        require(params.parallelism >= 1) { "parallelism must be >= 1" }
        require(params.memoryKiB >= 8 * params.parallelism) { "memoryKiB must be >= 8 * parallelism" }
        require(params.salt.size == SALT_SIZE_BYTES) { "salt must be $SALT_SIZE_BYTES bytes" }

        val argonParams = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(params.memoryKiB)
            .withIterations(params.iterations)
            .withParallelism(params.parallelism)
            .withSalt(params.salt)
            .build()

        val generator = Argon2BytesGenerator()
        generator.init(argonParams)

        val out = ByteArray(KEY_SIZE_BYTES)
        val buf = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password))
        val pwdBytes = ByteArray(buf.remaining()).also(buf::get)
        try {
            generator.generateBytes(pwdBytes, out, 0, out.size)
        } finally {
            pwdBytes.fill(0)
            if (buf.hasArray()) buf.array().fill(0)
        }
        return out
    }
}