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
    // D-025: верхние границы против битого/злого заголовка (дефолты сильно ниже)
    const val MAX_MEMORY_KIB = 1_048_576   // 1 GiB
    const val MAX_ITERATIONS = 100
    const val MAX_PARALLELISM = 16

    private val random = SecureRandom()

    /** Параметры KDF из заголовка контейнера. */
    data class Params(
        val memoryKiB: Int = DEFAULT_MEMORY_KIB,
        val iterations: Int = DEFAULT_ITERATIONS,
        val parallelism: Int = DEFAULT_PARALLELISM,
        val salt: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Params) return false
            return memoryKiB == other.memoryKiB &&
                    iterations == other.iterations &&
                    parallelism == other.parallelism &&
                    salt.contentEquals(other.salt)
        }

        override fun hashCode(): Int {
            var result = memoryKiB
            result = 31 * result + iterations
            result = 31 * result + parallelism
            result = 31 * result + salt.contentHashCode()
            return result
        }
    }

    fun newSalt(): ByteArray = ByteArray(SALT_SIZE_BYTES).also(random::nextBytes)

    /** Вывод KEK. Не мутирует переданный password. */
    fun derive(password: CharArray, params: Params): ByteArray {
        // D-025: верхние границы первыми — чтобы 8*parallelism не переполнился на злом заголовке
        require(params.parallelism <= MAX_PARALLELISM) { "parallelism must be <= $MAX_PARALLELISM" }
        require(params.iterations <= MAX_ITERATIONS) { "iterations must be <= $MAX_ITERATIONS" }
        require(params.memoryKiB <= MAX_MEMORY_KIB) { "memoryKiB must be <= $MAX_MEMORY_KIB" }
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

        /*
// СТАРАЯ ВЕРСИЯ:

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
*/

        // НОВАЯ ВЕРСИЯ:

        val out = ByteArray(KEY_SIZE_BYTES)

        try {
            val buf = StandardCharsets.UTF_8.encode(
                CharBuffer.wrap(password)
            )

            val pwdBytes = ByteArray(buf.remaining()).also(buf::get)

            try {
                generator.generateBytes(
                    pwdBytes,
                    out,
                    0,
                    out.size
                )
            } finally {
                pwdBytes.fill(0)

                if (buf.hasArray()) {
                    buf.array().fill(0)
                }
            }

            return out

        } catch (e: Throwable) {
            // Если генерация не удалась, не оставляем частичный ключ.
            out.fill(0)
            throw e
        }
    }
}